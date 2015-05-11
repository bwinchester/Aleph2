/*******************************************************************************
 * Copyright 2015, The IKANOW Open Source Project.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ikanow.aleph2.data_model.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.junit.Test;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.LinkedHashMultimap;
import com.ikanow.aleph2.data_model.utils.CrudUtils.MultiQueryComponent;
import com.ikanow.aleph2.data_model.utils.CrudUtils.Operator;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.CrudUtils.SingleQueryComponent;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class TestCrudUtils {

	// Some utility code (which actually end up being the basis for the mongodb crud operator...)
	
	private static BasicDBObject operatorToMongoKey(String field, Tuple2<Operator, Tuple2<Object, Object>> operator_args) {
		return Patterns.match(operator_args)
				.<BasicDBObject>andReturn()
				.when(op_args -> Operator.exists == op_args._1(), op_args -> new BasicDBObject(field, new BasicDBObject("$exists", op_args._2()._1())) )
				
				.when(op_args -> (Operator.any_of == op_args._1()), op_args -> new BasicDBObject(field, new BasicDBObject("$in", op_args._2()._1())) )
				.when(op_args -> (Operator.all_of == op_args._1()), op_args -> new BasicDBObject(field, new BasicDBObject("$all", op_args._2()._1())) )
				
				.when(op_args -> (Operator.equals == op_args._1()) && (null != op_args._2()._2()), op_args -> new BasicDBObject(field, new BasicDBObject("$ne", op_args._2()._2())) )
				.when(op_args -> (Operator.equals == op_args._1()), op_args -> new BasicDBObject(field, op_args._2()._1()) )
				
				.when(op_args -> Operator.range_open_open == op_args._1(), op_args -> {
					QueryBuilder qb = QueryBuilder.start(field);
					if (null != op_args._2()._1()) qb = qb.greaterThan(op_args._2()._1());
					if (null != op_args._2()._2()) qb = qb.lessThan(op_args._2()._2());
					return (BasicDBObject) qb.get(); 
				})
				.when(op_args -> Operator.range_open_closed == op_args._1(), op_args -> {
					QueryBuilder qb = QueryBuilder.start(field);
					if (null != op_args._2()._1()) qb = qb.greaterThan(op_args._2()._1());
					if (null != op_args._2()._2()) qb = qb.lessThanEquals(op_args._2()._2());
					return (BasicDBObject) qb.get(); 
				})
				.when(op_args -> Operator.range_closed_closed == op_args._1(), op_args -> {
					QueryBuilder qb = QueryBuilder.start(field);
					if (null != op_args._2()._1()) qb = qb.greaterThanEquals(op_args._2()._1());
					if (null != op_args._2()._2()) qb = qb.lessThanEquals(op_args._2()._2());
					return (BasicDBObject) qb.get(); 
				})
				.when(op_args -> Operator.range_closed_open == op_args._1(), op_args -> {
					QueryBuilder qb = QueryBuilder.start(field);
					if (null != op_args._2()._1()) qb = qb.greaterThanEquals(op_args._2()._1());
					if (null != op_args._2()._2()) qb = qb.lessThan(op_args._2()._2());
					return (BasicDBObject) qb.get(); 
				})
				.otherwise(op_args -> new BasicDBObject());
	}

	private static String getOperatorName(Operator op_in) {		
		return Patterns.match(op_in)
				.<String>andReturn()
				.when(op -> Operator.any_of == op, op -> "$or")
				.when(op -> Operator.all_of == op, op -> "$and")
				.otherwise(op -> "$and");
	}
	
	@SuppressWarnings("unchecked")
	private static <T> Tuple2<DBObject,DBObject> convertToMongoQuery(QueryComponent<T> query_in) {
		
		final String andVsOr = getOperatorName(query_in.getOp());
		
		final DBObject query_out = Patterns.match(query_in)
				.<DBObject>andReturn()
				.when((Class<SingleQueryComponent<T>>)(Class<?>)SingleQueryComponent.class, q -> convertToMongoQuery_single(andVsOr, q))
				.when((Class<MultiQueryComponent<T>>)(Class<?>)MultiQueryComponent.class, q -> convertToMongoQuery_multi(andVsOr, q))
				.otherwise(q -> (DBObject)new BasicDBObject());
		
		// Meta commands
		
		BasicDBObject meta = new BasicDBObject();
		
		if (null != query_in.getLimit()) meta.put("$limit", query_in.getLimit());
		final BasicDBObject sort = Patterns.match(query_in.getOrderBy())
				.<BasicDBObject>andReturn()
				.when(l -> l == null, l -> null)
				.otherwise(l -> {
							BasicDBObject s = new BasicDBObject();
							l.stream().forEach(field_order -> s.put(field_order._1(), field_order._2()));
							return s;
						});
		if (null != sort) meta.put("$sort", sort);
		
		return Tuples._2T(query_out, meta);
		
	}
	
	private static <T> DBObject convertToMongoQuery_multi(String andVsOr, MultiQueryComponent<T> query_in) {
		
		return Patterns.match(query_in.getElements())
				.<DBObject>andReturn()
				.when(f -> f.isEmpty(), f -> new BasicDBObject())
				.otherwise(f -> f.stream().collect(
						new Collector<SingleQueryComponent<T>, BasicDBList, DBObject>() {
							@Override
							public Supplier<BasicDBList> supplier() {
								return BasicDBList::new;
							}	
							@Override
							public BiConsumer<BasicDBList,SingleQueryComponent<T>> accumulator() {
								return (acc, entry) -> {
									acc.add(convertToMongoQuery_single(getOperatorName(entry.getOp()), entry));
								};
							}	
							@Override
							public BinaryOperator<BasicDBList> combiner() { return (a, b) -> { a.addAll(b); return a; } ; }	
							@Override
							public Function<BasicDBList, DBObject> finisher() { return acc -> (DBObject)new BasicDBObject(andVsOr, acc); }
							@Override
							public Set<java.util.stream.Collector.Characteristics> characteristics() { return EnumSet.of(Characteristics.UNORDERED); }
						} )); 
	}	
	
	private static <T> DBObject convertToMongoQuery_single(String andVsOr, SingleQueryComponent<T> query_in) {
		LinkedHashMultimap<String, Tuple2<Operator, Tuple2<Object, Object>>> fields = query_in.getAll();
		
		// The actual query:

		return Patterns.match(fields)
				.<DBObject>andReturn()
				.when(f -> f.isEmpty(), f -> new BasicDBObject())
				.otherwise(f -> f.asMap().entrySet().stream()
					.<Tuple2<String, Tuple2<Operator, Tuple2<Object, Object>>>>
						flatMap(entry -> entry.getValue().stream().map( val -> Tuples._2T(entry.getKey(), val) ) )
					.collect(		
						new Collector<Tuple2<String, Tuple2<Operator, Tuple2<Object, Object>>>, BasicDBObject, DBObject>() {
							@Override
							public Supplier<BasicDBObject> supplier() {
								return BasicDBObject::new;
							}	
							@Override
							public BiConsumer<BasicDBObject, Tuple2<String, Tuple2<Operator, Tuple2<Object, Object>>>> accumulator() {
								return (acc, entry) -> {
									Patterns.match(acc.get(andVsOr)).andAct()
										.when(l -> (null == l), l -> {
											BasicDBList dbl = new BasicDBList();
											dbl.add(operatorToMongoKey(entry._1(), entry._2()));
											acc.put(andVsOr, dbl);
										})
										.when(BasicDBList.class, l -> l.add(operatorToMongoKey(entry._1(), entry._2())))
										.otherwise(l -> {});
								};
							}	
							// Boilerplate:
							@Override
							public BinaryOperator<BasicDBObject> combiner() { return (a, b) -> { a.putAll(b.toMap()); return a; } ; }	
							@Override
							public Function<BasicDBObject, DBObject> finisher() { return acc -> acc; }
							@Override
							public Set<java.util.stream.Collector.Characteristics> characteristics() { return EnumSet.of(Characteristics.UNORDERED); }
						} ) 
					);		
	}
	
	// Test objects
	
	public static class TestBean {
		public static class NestedNestedTestBean {
			public String nested_nested_string_field() { return nested_nested_string_field; }
			
			private String nested_nested_string_field;
		}
		public static class NestedTestBean {
			public String nested_string_field() { return nested_string_field; }
			public NestedNestedTestBean nested_object() { return nested_object; }
			
			private String nested_string_field;
			private NestedNestedTestBean nested_object;
		}		
		public String string_field() { return string_field; }
		public Boolean bool_field() { return bool_field; }
		public Long long_field() { return long_field; }
		public List<NestedTestBean> nested_list() { return nested_list; }
		public Map<String, String> map() { return map; }
		public NestedTestBean nested_object() { return nested_object; }
		
		protected TestBean() {}
		private String string_field;
		private Boolean bool_field;
		private Long long_field;
		private List<NestedTestBean> nested_list;
		private NestedTestBean nested_object;
		private Map<String, String> map;
	}
	
	@Test
	public void emptyQuery() {
		
		// No meta:
		
		final SingleQueryComponent<TestBean> query_comp_1 = CrudUtils.allOf(new TestBean()); 
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);

		assertEquals(null, query_comp_1.getExtra());						
		
		assertEquals("{ }", query_meta_1._1().toString());
		assertEquals("{ }", query_meta_1._2().toString());
		
		// Meta fields
		
		TestBean template2 = ObjectTemplateUtils.build(TestBean.class).with(TestBean::string_field, null).done();
		
		final SingleQueryComponent<TestBean> query_comp_2 = CrudUtils.anyOf(template2)
													.orderBy(Tuples._2T("test_field_1", 1), Tuples._2T("test_field_2", -1));		

		assertEquals(template2, query_comp_2.getElement());				
		
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		
		assertEquals("{ }", query_meta_2._1().toString());
		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_1", 1);
		expected_meta_nested.put("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$sort", expected_meta_nested);
		assertEquals(expected_meta.toString(), query_meta_2._2().toString());
	}
	
	@Test
	public void basicSingleTest() {
		
		// Queries starting with allOf
		
		// Very simple

		TestBean template1 = ObjectTemplateUtils.build(TestBean.class).with(TestBean::string_field, "string_field").done();
		
		final SingleQueryComponent<TestBean> query_comp_1 = CrudUtils.allOf(template1).when(TestBean::bool_field, true);
		
		final SingleQueryComponent<TestBean> query_comp_1b = CrudUtils.allOf(TestBean.class)
				.when("bool_field", true)
				.when("string_field", "string_field");
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		final Tuple2<DBObject, DBObject> query_meta_1b = convertToMongoQuery(query_comp_1b);
		
		final DBObject expected_1 = QueryBuilder.start().and(
						QueryBuilder.start("bool_field").is(true).get(),
						QueryBuilder.start("string_field").is("string_field").get()
							).get();
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_1.toString(), query_meta_1b._1().toString());
		assertEquals("{ }", query_meta_1._2().toString());
		
		// Includes extra + all the checks except the range checks
		
		final SingleQueryComponent<TestBean> query_comp_2 = CrudUtils.anyOf(TestBean.class)
				.when(TestBean::string_field, "string_field")
				.withPresent(TestBean::bool_field)
				.withNotPresent(TestBean::long_field)
				.withAny(TestBean::string_field, Arrays.asList("test1a", "test1b"))
				.withAll(TestBean::long_field, Arrays.asList(10, 11, 12))
				.whenNot(TestBean::long_field, 13)
				.limit(100);

		final SingleQueryComponent<TestBean> query_comp_2b = CrudUtils.anyOf(TestBean.class)
				.when("string_field", "string_field")
				.withPresent("bool_field")
				.withNotPresent("long_field")
				.withAny("string_field", Arrays.asList("test1a", "test1b"))
				.withAll("long_field", Arrays.asList(10, 11, 12))
				.whenNot("long_field", 13)
				.limit(100);		
		
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		final Tuple2<DBObject, DBObject> query_meta_2b = convertToMongoQuery(query_comp_2b);
		
		final DBObject expected_2 = QueryBuilder.start().or(
										QueryBuilder.start("string_field").is("string_field").get(),
										QueryBuilder.start("string_field").in(Arrays.asList("test1a", "test1b")).get(),
										QueryBuilder.start("bool_field").exists(true).get(),
										QueryBuilder.start("long_field").exists(false).get(),
										QueryBuilder.start("long_field").all(Arrays.asList(10, 11, 12)).get(),
										QueryBuilder.start("long_field").notEquals(13).get()										
									).get();
		
		assertEquals(expected_2.toString(), query_meta_2._1().toString());
		assertEquals(expected_2.toString(), query_meta_2b._1().toString());
		assertEquals("{ \"$limit\" : 100}", query_meta_2._2().toString());		
	}
	
	@Test
	public void testAllTheRangeQueries() {
		
		final SingleQueryComponent<TestBean> query_comp_1 = CrudUtils.allOf(TestBean.class)
				.rangeAbove(TestBean::string_field, "bbb", true)
				.rangeBelow(TestBean::string_field, "fff", false)
				.rangeIn(TestBean::string_field, "ccc", false, "ddd", true)
				.rangeIn(TestBean::string_field, "xxx", false, "yyy", false)
				
				.rangeAbove(TestBean::long_field, 1000, false)
				.rangeBelow(TestBean::long_field, 10000, true)
				.rangeIn(TestBean::long_field, 2000, true, 20000, true)
				.rangeIn(TestBean::long_field, 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1), Tuples._2T("test_field_2", -1))
				.limit(200);

		final SingleQueryComponent<TestBean> query_comp_1b = CrudUtils.allOf(TestBean.class)
				.rangeAbove("string_field", "bbb", true)
				.rangeBelow("string_field", "fff", false)
				.rangeIn("string_field", "ccc", false, "ddd", true)
				.rangeIn("string_field", "xxx", false, "yyy", false)
				
				.rangeAbove("long_field", 1000, false)
				.rangeBelow("long_field", 10000, true)
				.rangeIn("long_field", 2000, true, 20000, true)
				.rangeIn("long_field", 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1)).orderBy(Tuples._2T("test_field_2", -1))		
				.limit(200);
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		final Tuple2<DBObject, DBObject> query_meta_1b = convertToMongoQuery(query_comp_1b);

		final DBObject expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").greaterThan("bbb").get(),
				QueryBuilder.start("string_field").lessThanEquals("fff").get(),
				QueryBuilder.start("string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("string_field").greaterThanEquals("xxx").lessThanEquals("yyy").get(),
				
				QueryBuilder.start("long_field").greaterThanEquals(1000).get(),
				QueryBuilder.start("long_field").lessThan(10000).get(),
				QueryBuilder.start("long_field").greaterThan(2000).lessThan(20000).get(),
				QueryBuilder.start("long_field").greaterThan(3000).lessThanEquals(30000).get()
				
				).get();

		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_1", 1);
		expected_meta_nested.put("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 200);
		expected_meta.put("$sort", expected_meta_nested);
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_1.toString(), query_meta_1b._1().toString());
		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		
	}

	@Test 
	public void testNestedQueries() {
		
		// 1 level of nesting
		
		final SingleQueryComponent<TestBean> query_comp_1 = CrudUtils.allOf(TestBean.class)
														.when(TestBean::string_field, "a")
														.nested(TestBean::nested_list, 
																CrudUtils.anyOf(TestBean.NestedTestBean.class)
																	.when(TestBean.NestedTestBean::nested_string_field, "x")
																	.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
																	.rangeIn("nested_string_field", "ccc", false, "ddd", true)
																	.limit(1000) // (should be ignored)
																	.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
														)
														.withPresent("long_field")
														.limit(5) 
														.orderBy(Tuples._2T("test_field_2", -1));
														
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		
				
		final DBObject expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),				
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("long_field").exists(true).get()
				).get();		
		
		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 5);
		expected_meta.put("$sort", expected_meta_nested);
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		
		// 2 levels of nesting

		TestBean.NestedTestBean nestedBean = ObjectTemplateUtils.build(TestBean.NestedTestBean.class).with("nested_string_field", "x").done();
		
		final SingleQueryComponent<TestBean> query_comp_2 = CrudUtils.allOf(TestBean.class)
				.when(TestBean::string_field, "a")
				.nested(TestBean::nested_list, 
						CrudUtils.anyOf(nestedBean)
							.when(TestBean.NestedTestBean::nested_string_field, "y")
							.nested(TestBean.NestedTestBean::nested_object,
									CrudUtils.allOf(TestBean.NestedNestedTestBean.class)
										.when(TestBean.NestedNestedTestBean::nested_nested_string_field, "z")
										.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
										.limit(1000) // (should be ignored)
										.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
									)
							.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
							.rangeIn("nested_string_field", "ccc", false, "ddd", true)
				)
				.withPresent("long_field");

				
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		
		final DBObject expected_2 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("y").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),	
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").is("z").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),
				QueryBuilder.start("long_field").exists(true).get()
				).get();		
		
		assertEquals(expected_2.toString(), query_meta_2._1().toString());
		assertEquals("{ }", query_meta_2._2().toString());
		
		// Nested objects
		
		TestBean t = new TestBean();
		t.string_field = "a";
		t.map = new HashMap<String, String>();
		t.bool_field = true;
		t.long_field = 1L;
		t.nested_list = Arrays.asList();
		TestBean.NestedTestBean nt2 = new TestBean.NestedTestBean();
		nt2.nested_string_field = "xx";
		t.nested_object = nt2;
		TestBean.NestedNestedTestBean nnt = new TestBean.NestedNestedTestBean();
		nnt.nested_nested_string_field = "i";
		nt2.nested_object = nnt;
		
		final SingleQueryComponent<TestBean> query_comp_3 = CrudUtils.allOf(t)
																.when(TestBean::long_field, 2)
																	.nested(TestBean::nested_list,
																			CrudUtils.allOf(TestBean.NestedTestBean.class)
																				.when(TestBean.NestedTestBean::nested_string_field, "y")
																				.nested(TestBean.NestedTestBean::nested_object,
																						CrudUtils.allOf(TestBean.NestedNestedTestBean.class)
																							.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
																						)
																				);
		
		final Tuple2<DBObject, DBObject> query_meta_3 = convertToMongoQuery(query_comp_3);
		
		final DBObject expected_3 = QueryBuilder.start().and(
				QueryBuilder.start("long_field").is(2).get(),
				QueryBuilder.start("long_field").is(1).get(),
				QueryBuilder.start("nested_list.nested_string_field").is("y").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),

				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("bool_field").is(true).get(),
				QueryBuilder.start("nested_list").is(Arrays.asList()).get(),
				QueryBuilder.start("nested_object.nested_string_field").is("xx").get(),
				QueryBuilder.start("nested_object.nested_object.nested_nested_string_field").is("i").get(),
				QueryBuilder.start("map").is(new HashMap<String, String>()).get()
				
				).get();		
		
		assertEquals(expected_3.toString(), query_meta_3._1().toString());
		assertEquals("{ }", query_meta_3._2().toString());
		
		// Test JSON version:
		
		final Tuple2<DBObject, DBObject> query_meta_3b = convertToMongoQuery(query_comp_3.toJsonComponent());
		
		assertEquals(expected_3.toString(), query_meta_3b._1().toString());
		assertEquals("{ }", query_meta_3b._2().toString());		
	}
	
	@Test
	public void testMultipleQueries() {

		// Just to test .. single node versions
		
		final SingleQueryComponent<TestBean> query_comp_1 = CrudUtils.allOf(TestBean.class)
				.rangeAbove(TestBean::string_field, "bbb", true)
				.rangeBelow(TestBean::string_field, "fff", false)
				.rangeIn(TestBean::string_field, "ccc", false, "ddd", true)
				.rangeIn(TestBean::string_field, "xxx", false, "yyy", false)
				
				.rangeAbove(TestBean::long_field, 1000, false)
				.rangeBelow(TestBean::long_field, 10000, true)
				.rangeIn(TestBean::long_field, 2000, true, 20000, true)
				.rangeIn(TestBean::long_field, 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1))	// should be ignored
				.limit(200); // should be ignored

		final MultiQueryComponent<TestBean> multi_query_1 = CrudUtils.<TestBean>allOf(query_comp_1).orderBy(Tuples._2T("test_field_2", -1)).limit(5);
		final MultiQueryComponent<TestBean> multi_query_2 = CrudUtils.<TestBean>anyOf(query_comp_1);
				
		final QueryBuilder expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").greaterThan("bbb").get(), 
				QueryBuilder.start("string_field").lessThanEquals("fff").get(),
				QueryBuilder.start("string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("string_field").greaterThanEquals("xxx").lessThanEquals("yyy").get(),
				
				QueryBuilder.start("long_field").greaterThanEquals(1000).get(),
				QueryBuilder.start("long_field").lessThan(10000).get(),
				QueryBuilder.start("long_field").greaterThan(2000).lessThan(20000).get(),
				QueryBuilder.start("long_field").greaterThan(3000).lessThanEquals(30000).get()
				
				);
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(multi_query_1);
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(multi_query_2);
		
		final DBObject multi_expected_1 = QueryBuilder.start().and((DBObject)expected_1.get()).get();
		final DBObject multi_expected_2 = QueryBuilder.start().or((DBObject)expected_1.get()).get();
		
		assertEquals(multi_expected_1.toString(), query_meta_1._1().toString());
		assertEquals(multi_expected_2.toString(), query_meta_2._1().toString());

		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 5);
		expected_meta.put("$sort", expected_meta_nested);

		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		assertEquals("{ }", query_meta_2._2().toString());		
		
		// Multiple nested
		
		final SingleQueryComponent<TestBean> query_comp_2 = CrudUtils.allOf(TestBean.class)
				.when(TestBean::string_field, "a")
				.nested(TestBean::nested_list, 
						CrudUtils.anyOf(TestBean.NestedTestBean.class)
							.when(TestBean.NestedTestBean::nested_string_field, "x")
							.nested(TestBean.NestedTestBean::nested_object,
									CrudUtils.allOf(TestBean.NestedNestedTestBean.class)
										.when(TestBean.NestedNestedTestBean::nested_nested_string_field, "z")
										.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
										.limit(1000) // (should be ignored)
										.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
									)
							.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
							.rangeIn("nested_string_field", "ccc", false, "ddd", true)
				)
				.withPresent("long_field");
		
		final MultiQueryComponent<TestBean> multi_query_3 = CrudUtils.allOf(query_comp_1, query_comp_2).limit(5);
		final MultiQueryComponent<TestBean> multi_query_4 = CrudUtils.anyOf(query_comp_1, query_comp_2).orderBy(Tuples._2T("test_field_2", -1));

		final MultiQueryComponent<TestBean> multi_query_5 = CrudUtils.<TestBean>allOf(query_comp_1).also(query_comp_2).limit(5);
		final MultiQueryComponent<TestBean> multi_query_6 = CrudUtils.<TestBean>anyOf(query_comp_1).also(query_comp_2).orderBy().orderBy(Tuples._2T("test_field_2", -1));
		
		
		final Tuple2<DBObject, DBObject> query_meta_3 = convertToMongoQuery(multi_query_3);
		final Tuple2<DBObject, DBObject> query_meta_4 = convertToMongoQuery(multi_query_4);
		final Tuple2<DBObject, DBObject> query_meta_5 = convertToMongoQuery(multi_query_5);
		final Tuple2<DBObject, DBObject> query_meta_6 = convertToMongoQuery(multi_query_6);
		
		final QueryBuilder expected_2 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),	
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").is("z").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),
				QueryBuilder.start("long_field").exists(true).get()
				);		
		
		final DBObject multi_expected_3 = QueryBuilder.start().and((DBObject)expected_1.get(), (DBObject)expected_2.get()).get();
		final DBObject multi_expected_4 = QueryBuilder.start().or((DBObject)expected_1.get(), (DBObject)expected_2.get()).get();
		
		assertEquals(multi_expected_3.toString(), query_meta_3._1().toString());
		assertEquals(multi_expected_4.toString(), query_meta_4._1().toString());
		assertEquals(multi_expected_3.toString(), query_meta_5._1().toString());
		assertEquals(multi_expected_4.toString(), query_meta_6._1().toString());

		final BasicDBObject expected_meta_nested_2 = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta_2 = new BasicDBObject("$sort", expected_meta_nested_2);

		assertEquals("{ \"$limit\" : 5}", query_meta_3._2().toString());		
		assertEquals(expected_meta_2.toString(), query_meta_4._2().toString());
		assertEquals("{ \"$limit\" : 5}", query_meta_5._2().toString());		
		assertEquals(expected_meta_2.toString(), query_meta_6._2().toString());
	}

	///////////////////////////////////////////////////////////////////////	
	
	//TODO (ALEPH-22) - write test code for the JsonNode builders (basically just duplicate the above tests I think?)
	
	@Test
	public void emptyQuery_json() {
		
		// No meta:
		
		final SingleQueryComponent<JsonNode> query_comp_1 = CrudUtils.allOf_json(new TestBean()); 
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);

		assertEquals(null, query_comp_1.getExtra());						
		
		assertEquals("{ }", query_meta_1._1().toString());
		assertEquals("{ }", query_meta_1._2().toString());
		
		// Meta fields
		
		TestBean template2 = ObjectTemplateUtils.build(TestBean.class).with(TestBean::string_field, null).done();
		
		final SingleQueryComponent<JsonNode> query_comp_2 = CrudUtils.anyOf_json(template2)
													.orderBy(Tuples._2T("test_field_1", 1), Tuples._2T("test_field_2", -1));		

		assertEquals(template2, query_comp_2.getElement());				
		
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		
		assertEquals("{ }", query_meta_2._1().toString());
		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_1", 1);
		expected_meta_nested.put("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$sort", expected_meta_nested);
		assertEquals(expected_meta.toString(), query_meta_2._2().toString());
	}
	
	@Test
	public void basicSingleTest_json() {
		
		// Queries starting with allOf
		
		// Very simple

		TestBean template1 = ObjectTemplateUtils.build(TestBean.class).with(TestBean::string_field, "string_field").done();
		
		final SingleQueryComponent<JsonNode> query_comp_1 = CrudUtils.allOf_json(template1).when(TestBean::bool_field, true);
		
		final SingleQueryComponent<JsonNode> query_comp_1b = CrudUtils.allOf_json(TestBean.class)
				.when("bool_field", true)
				.when("string_field", "string_field");
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		final Tuple2<DBObject, DBObject> query_meta_1b = convertToMongoQuery(query_comp_1b);
		
		final DBObject expected_1 = QueryBuilder.start().and(
						QueryBuilder.start("bool_field").is(true).get(),
						QueryBuilder.start("string_field").is("string_field").get()
							).get();
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_1.toString(), query_meta_1b._1().toString());
		assertEquals("{ }", query_meta_1._2().toString());
		
		// Includes extra + all the checks except the range checks
		
		final SingleQueryComponent<JsonNode> query_comp_2 = CrudUtils.anyOf_json(TestBean.class)
				.when(TestBean::string_field, "string_field")
				.withPresent(TestBean::bool_field)
				.withNotPresent(TestBean::long_field)
				.withAny(TestBean::string_field, Arrays.asList("test1a", "test1b"))
				.withAll(TestBean::long_field, Arrays.asList(10, 11, 12))
				.whenNot(TestBean::long_field, 13)
				.limit(100);

		final SingleQueryComponent<JsonNode> query_comp_2b = CrudUtils.anyOf_json(TestBean.class)
				.when("string_field", "string_field")
				.withPresent("bool_field")
				.withNotPresent("long_field")
				.withAny("string_field", Arrays.asList("test1a", "test1b"))
				.withAll("long_field", Arrays.asList(10, 11, 12))
				.whenNot("long_field", 13)
				.limit(100);		
		
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		final Tuple2<DBObject, DBObject> query_meta_2b = convertToMongoQuery(query_comp_2b);
		
		final DBObject expected_2 = QueryBuilder.start().or(
										QueryBuilder.start("string_field").is("string_field").get(),
										QueryBuilder.start("string_field").in(Arrays.asList("test1a", "test1b")).get(),
										QueryBuilder.start("bool_field").exists(true).get(),
										QueryBuilder.start("long_field").exists(false).get(),
										QueryBuilder.start("long_field").all(Arrays.asList(10, 11, 12)).get(),
										QueryBuilder.start("long_field").notEquals(13).get()										
									).get();
		
		assertEquals(expected_2.toString(), query_meta_2._1().toString());
		assertEquals(expected_2.toString(), query_meta_2b._1().toString());
		assertEquals("{ \"$limit\" : 100}", query_meta_2._2().toString());		
	}
	
	@Test
	public void testAllTheRangeQueries_json() {
		
		final SingleQueryComponent<JsonNode> query_comp_1 = CrudUtils.allOf_json(TestBean.class)
				.rangeAbove(TestBean::string_field, "bbb", true)
				.rangeBelow(TestBean::string_field, "fff", false)
				.rangeIn(TestBean::string_field, "ccc", false, "ddd", true)
				.rangeIn(TestBean::string_field, "xxx", false, "yyy", false)
				
				.rangeAbove(TestBean::long_field, 1000, false)
				.rangeBelow(TestBean::long_field, 10000, true)
				.rangeIn(TestBean::long_field, 2000, true, 20000, true)
				.rangeIn(TestBean::long_field, 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1), Tuples._2T("test_field_2", -1))
				.limit(200);

		final SingleQueryComponent<JsonNode> query_comp_1b = CrudUtils.allOf_json(TestBean.class)
				.rangeAbove("string_field", "bbb", true)
				.rangeBelow("string_field", "fff", false)
				.rangeIn("string_field", "ccc", false, "ddd", true)
				.rangeIn("string_field", "xxx", false, "yyy", false)
				
				.rangeAbove("long_field", 1000, false)
				.rangeBelow("long_field", 10000, true)
				.rangeIn("long_field", 2000, true, 20000, true)
				.rangeIn("long_field", 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1)).orderBy(Tuples._2T("test_field_2", -1))		
				.limit(200);
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		final Tuple2<DBObject, DBObject> query_meta_1b = convertToMongoQuery(query_comp_1b);

		final DBObject expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").greaterThan("bbb").get(),
				QueryBuilder.start("string_field").lessThanEquals("fff").get(),
				QueryBuilder.start("string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("string_field").greaterThanEquals("xxx").lessThanEquals("yyy").get(),
				
				QueryBuilder.start("long_field").greaterThanEquals(1000).get(),
				QueryBuilder.start("long_field").lessThan(10000).get(),
				QueryBuilder.start("long_field").greaterThan(2000).lessThan(20000).get(),
				QueryBuilder.start("long_field").greaterThan(3000).lessThanEquals(30000).get()
				
				).get();

		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_1", 1);
		expected_meta_nested.put("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 200);
		expected_meta.put("$sort", expected_meta_nested);
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_1.toString(), query_meta_1b._1().toString());
		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		
	}

	@Test 
	public void testNestedQueries_json() {
		
		// 1 level of nesting
		
		final SingleQueryComponent<JsonNode> query_comp_1 = CrudUtils.allOf_json(TestBean.class)
														.when(TestBean::string_field, "a")
														.nested(TestBean::nested_list, 
																CrudUtils.anyOf_json(TestBean.NestedTestBean.class)
																	.when(TestBean.NestedTestBean::nested_string_field, "x")
																	.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
																	.rangeIn("nested_string_field", "ccc", false, "ddd", true)
																	.limit(1000) // (should be ignored)
																	.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
														)
														.withPresent("long_field")
														.limit(5) 
														.orderBy(Tuples._2T("test_field_2", -1));
														
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(query_comp_1);
		
				
		final DBObject expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),				
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("long_field").exists(true).get()
				).get();		
		
		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 5);
		expected_meta.put("$sort", expected_meta_nested);
		
		assertEquals(expected_1.toString(), query_meta_1._1().toString());
		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		
		// 2 levels of nesting

		TestBean.NestedTestBean nestedBean = ObjectTemplateUtils.build(TestBean.NestedTestBean.class).with("nested_string_field", "x").done();
		
		final SingleQueryComponent<JsonNode> query_comp_2 = CrudUtils.allOf_json(TestBean.class)
				.when(TestBean::string_field, "a")
				.nested(TestBean::nested_list, 
						CrudUtils.anyOf_json(nestedBean)
							.when(TestBean.NestedTestBean::nested_string_field, "y")
							.nested(TestBean.NestedTestBean::nested_object,
									CrudUtils.allOf_json(TestBean.NestedNestedTestBean.class)
										.when(TestBean.NestedNestedTestBean::nested_nested_string_field, "z")
										.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
										.limit(1000) // (should be ignored)
										.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
									)
							.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
							.rangeIn("nested_string_field", "ccc", false, "ddd", true)
				)
				.withPresent("long_field");

				
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(query_comp_2);
		
		final DBObject expected_2 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("y").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),	
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").is("z").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),
				QueryBuilder.start("long_field").exists(true).get()
				).get();		
		
		assertEquals(expected_2.toString(), query_meta_2._1().toString());
		assertEquals("{ }", query_meta_2._2().toString());
		
		// Nested objects
		
		TestBean t = new TestBean();
		t.string_field = "a";
		t.map = new HashMap<String, String>();
		t.bool_field = true;
		t.long_field = 1L;
		t.nested_list = Arrays.asList();
		TestBean.NestedTestBean nt2 = new TestBean.NestedTestBean();
		nt2.nested_string_field = "xx";
		t.nested_object = nt2;
		TestBean.NestedNestedTestBean nnt = new TestBean.NestedNestedTestBean();
		nnt.nested_nested_string_field = "i";
		nt2.nested_object = nnt;
		
		final SingleQueryComponent<JsonNode> query_comp_3 = CrudUtils.allOf_json(t)
																.when(TestBean::long_field, 2)
																	.nested(TestBean::nested_list,
																			CrudUtils.allOf_json(TestBean.NestedTestBean.class)
																				.when(TestBean.NestedTestBean::nested_string_field, "y")
																				.nested(TestBean.NestedTestBean::nested_object,
																						CrudUtils.allOf_json(TestBean.NestedNestedTestBean.class)
																							.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
																						)
																				);
		
		final Tuple2<DBObject, DBObject> query_meta_3 = convertToMongoQuery(query_comp_3);
		
		final DBObject expected_3 = QueryBuilder.start().and(
				QueryBuilder.start("long_field").is(2).get(),
				QueryBuilder.start("long_field").is(1).get(),
				QueryBuilder.start("nested_list.nested_string_field").is("y").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),

				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("bool_field").is(true).get(),
				QueryBuilder.start("nested_list").is(Arrays.asList()).get(),
				QueryBuilder.start("nested_object.nested_string_field").is("xx").get(),
				QueryBuilder.start("nested_object.nested_object.nested_nested_string_field").is("i").get(),
				QueryBuilder.start("map").is(new HashMap<String, String>()).get()
				
				).get();		
		
		assertEquals(expected_3.toString(), query_meta_3._1().toString());
		assertEquals("{ }", query_meta_3._2().toString());
	}
	
	@Test
	public void testMultipleQueries_json() {

		// Just to test .. single node versions
		
		final SingleQueryComponent<JsonNode> query_comp_1 = CrudUtils.allOf_json(TestBean.class)
				.rangeAbove(TestBean::string_field, "bbb", true)
				.rangeBelow(TestBean::string_field, "fff", false)
				.rangeIn(TestBean::string_field, "ccc", false, "ddd", true)
				.rangeIn(TestBean::string_field, "xxx", false, "yyy", false)
				
				.rangeAbove(TestBean::long_field, 1000, false)
				.rangeBelow(TestBean::long_field, 10000, true)
				.rangeIn(TestBean::long_field, 2000, true, 20000, true)
				.rangeIn(TestBean::long_field, 3000, true, 30000, false)
				
				.orderBy(Tuples._2T("test_field_1", 1))	// should be ignored
				.limit(200); // should be ignored

		final MultiQueryComponent<JsonNode> multi_query_1 = CrudUtils.<JsonNode>allOf(query_comp_1).orderBy(Tuples._2T("test_field_2", -1)).limit(5);
		final MultiQueryComponent<JsonNode> multi_query_2 = CrudUtils.<JsonNode>anyOf(query_comp_1);
				
		final QueryBuilder expected_1 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").greaterThan("bbb").get(), 
				QueryBuilder.start("string_field").lessThanEquals("fff").get(),
				QueryBuilder.start("string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("string_field").greaterThanEquals("xxx").lessThanEquals("yyy").get(),
				
				QueryBuilder.start("long_field").greaterThanEquals(1000).get(),
				QueryBuilder.start("long_field").lessThan(10000).get(),
				QueryBuilder.start("long_field").greaterThan(2000).lessThan(20000).get(),
				QueryBuilder.start("long_field").greaterThan(3000).lessThanEquals(30000).get()
				
				);
		
		final Tuple2<DBObject, DBObject> query_meta_1 = convertToMongoQuery(multi_query_1);
		final Tuple2<DBObject, DBObject> query_meta_2 = convertToMongoQuery(multi_query_2);
		
		final DBObject multi_expected_1 = QueryBuilder.start().and((DBObject)expected_1.get()).get();
		final DBObject multi_expected_2 = QueryBuilder.start().or((DBObject)expected_1.get()).get();
		
		assertEquals(multi_expected_1.toString(), query_meta_1._1().toString());
		assertEquals(multi_expected_2.toString(), query_meta_2._1().toString());

		final BasicDBObject expected_meta_nested = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta = new BasicDBObject("$limit", 5);
		expected_meta.put("$sort", expected_meta_nested);

		assertEquals(expected_meta.toString(), query_meta_1._2().toString());
		assertEquals("{ }", query_meta_2._2().toString());		
		
		// Multiple nested
		
		final SingleQueryComponent<JsonNode> query_comp_2 = CrudUtils.allOf_json(TestBean.class)
				.when(TestBean::string_field, "a")
				.nested(TestBean::nested_list, 
						CrudUtils.anyOf_json(TestBean.NestedTestBean.class)
							.when(TestBean.NestedTestBean::nested_string_field, "x")
							.nested(TestBean.NestedTestBean::nested_object,
									CrudUtils.allOf_json(TestBean.NestedNestedTestBean.class)
										.when(TestBean.NestedNestedTestBean::nested_nested_string_field, "z")
										.withNotPresent(TestBean.NestedNestedTestBean::nested_nested_string_field)
										.limit(1000) // (should be ignored)
										.orderBy(Tuples._2T("test_field_1", 1)) // (should be ignored)
									)
							.withAny(TestBean.NestedTestBean::nested_string_field, Arrays.asList("x", "y"))
							.rangeIn("nested_string_field", "ccc", false, "ddd", true)
				)
				.withPresent("long_field");
		
		final MultiQueryComponent<JsonNode> multi_query_3 = CrudUtils.allOf(query_comp_1, query_comp_2).limit(5);
		final MultiQueryComponent<JsonNode> multi_query_4 = CrudUtils.anyOf(query_comp_1, query_comp_2).orderBy(Tuples._2T("test_field_2", -1));

		final MultiQueryComponent<JsonNode> multi_query_5 = CrudUtils.<JsonNode>allOf(query_comp_1).also(query_comp_2).limit(5);
		final MultiQueryComponent<JsonNode> multi_query_6 = CrudUtils.<JsonNode>anyOf(query_comp_1).also(query_comp_2).orderBy().orderBy(Tuples._2T("test_field_2", -1));
		
		
		final Tuple2<DBObject, DBObject> query_meta_3 = convertToMongoQuery(multi_query_3);
		final Tuple2<DBObject, DBObject> query_meta_4 = convertToMongoQuery(multi_query_4);
		final Tuple2<DBObject, DBObject> query_meta_5 = convertToMongoQuery(multi_query_5);
		final Tuple2<DBObject, DBObject> query_meta_6 = convertToMongoQuery(multi_query_6);
		
		final QueryBuilder expected_2 = QueryBuilder.start().and(
				QueryBuilder.start("string_field").is("a").get(),
				QueryBuilder.start("nested_list.nested_string_field").is("x").get(),
				QueryBuilder.start("nested_list.nested_string_field").in(Arrays.asList("x", "y")).get(),	
				QueryBuilder.start("nested_list.nested_string_field").greaterThanEquals("ccc").lessThan("ddd").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").is("z").get(),
				QueryBuilder.start("nested_list.nested_object.nested_nested_string_field").exists(false).get(),
				QueryBuilder.start("long_field").exists(true).get()
				);		
		
		final DBObject multi_expected_3 = QueryBuilder.start().and((DBObject)expected_1.get(), (DBObject)expected_2.get()).get();
		final DBObject multi_expected_4 = QueryBuilder.start().or((DBObject)expected_1.get(), (DBObject)expected_2.get()).get();
		
		assertEquals(multi_expected_3.toString(), query_meta_3._1().toString());
		assertEquals(multi_expected_4.toString(), query_meta_4._1().toString());
		assertEquals(multi_expected_3.toString(), query_meta_5._1().toString());
		assertEquals(multi_expected_4.toString(), query_meta_6._1().toString());

		final BasicDBObject expected_meta_nested_2 = new BasicDBObject("test_field_2", -1);
		final BasicDBObject expected_meta_2 = new BasicDBObject("$sort", expected_meta_nested_2);

		assertEquals("{ \"$limit\" : 5}", query_meta_3._2().toString());		
		assertEquals(expected_meta_2.toString(), query_meta_4._2().toString());
		assertEquals("{ \"$limit\" : 5}", query_meta_5._2().toString());		
		assertEquals(expected_meta_2.toString(), query_meta_6._2().toString());
	}
	
}
