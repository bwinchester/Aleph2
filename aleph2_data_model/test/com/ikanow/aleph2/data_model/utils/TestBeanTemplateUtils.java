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
import static org.hamcrest.CoreMatchers.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils.BeanTemplate;
import com.ikanow.aleph2.data_model.utils.TestBeanTemplateUtils.NestedTestBean.NestedNestedTestBean;

public class TestBeanTemplateUtils {

	public static class TestBean {
		public String testField() { return testField; } /** Test field */
		
		private String testField;
	}
	
	@Test
	public void test_fromJson() {
		final String jsonstr1 = "{\"testField\":\"test1\"}";
		final String jsonstr2 = "{\"testField\":\"test2\"}";
		final String jsonstr3 = "{\"testField\":\"test3\"}";
		final JsonNode json2 = BeanTemplateUtils.configureMapper(Optional.empty()).createObjectNode().put("testField", "test2");
		final Map<String, Object> json3 = ImmutableMap.<String, Object>builder().put("testField", "test3").build();
		
		assertEquals(jsonstr1, BeanTemplateUtils.toJson(BeanTemplateUtils.from(jsonstr1, TestBean.class).get()).toString());
		assertEquals(jsonstr2, BeanTemplateUtils.toJson(BeanTemplateUtils.from(json2, TestBean.class).get()).toString());
		assertEquals(jsonstr3, BeanTemplateUtils.toJson(BeanTemplateUtils.from(json3, TestBean.class).get()).toString());
	}
	
	
	
	@Test
	public void testSingleMethodHelper() {
		
		String test1 = BeanTemplateUtils.from(TestBean.class).field(TestBean::testField);
		TestBean t = new TestBean();
		String test2 = BeanTemplateUtils.from(t).field(TestBean::testField);
		assertEquals("The type safe reference should resolve correctly (class ref)", "testField", test1);
		assertEquals("The type safe reference should resolve correctly (object ref)", "testField", test2);
	}

	public static class NestedTestBean {
		public String testField() { return testField; } /** Test field */
		public NestedNestedTestBean nestedBean() { return nestedBean; } 
		public static class NestedNestedTestBean {
			public String testField() { return nestedField; } /** Test field */
			public NestedNestedTestBean nestedBean() { return nestedBean; }
			
			private NestedNestedTestBean nestedBean;			
			private String nestedField;
		}
		
		private String testField;
		private NestedNestedTestBean nestedBean;
		
	}
	
	@Test
	public void testNestedMethodHelper() {
		String test1 = BeanTemplateUtils.from(NestedTestBean.class)
							.field(NestedTestBean::testField);
		
		String test2 = BeanTemplateUtils.from(NestedTestBean.class)
							.nested(NestedTestBean::nestedBean, NestedNestedTestBean.class)
							.field(NestedNestedTestBean::testField);
		
		String test3 = BeanTemplateUtils.from(NestedTestBean.class)
							.nested(NestedTestBean::nestedBean, NestedNestedTestBean.class)
							.nested(NestedNestedTestBean::nestedBean, NestedNestedTestBean.class)
							.field(NestedNestedTestBean::testField);
		
		NestedTestBean t1 = new NestedTestBean();
		NestedNestedTestBean t2 = new NestedNestedTestBean();
		
		String test4 = BeanTemplateUtils.from(t1).nested(NestedTestBean::nestedBean, t2).field(NestedNestedTestBean::nestedBean);
		
		assertEquals("The type safe reference should resolve correctly (class ref)", "testField", test1);
		assertEquals("The type safe reference should resolve correctly (nested, class ref)", "nestedBean.testField", test2);
		assertEquals("The type safe reference should resolve correctly (2x nested, class ref)", "nestedBean.nestedBean.testField", test3);
		assertEquals("The type safe reference should resolve correctly (nested, object ref)", "nestedBean.nestedBean", test4);		
	}
	
	public static class TestBuildBean {
		public String testField() { return testField; } /** Test field */
		public String test3Field() { return test3Field; } /** Test field */
		
		String testField;
		String test2Field;
		String test3Field;
		List<String> test4Fields;
	}	
	
	@Test
	public void testBeanBuilder() {
				
		
		final BeanTemplate<TestBuildBean> template = BeanTemplateUtils.build(TestBuildBean.class)
								.with(TestBuildBean::testField, "4")
								.with("test2Field", "5")
								.with("test4Fields", Arrays.asList("1", "2", "3"))
								.done();

		assertEquals("testField should have been set", "4", template.get(TestBuildBean::testField));		
		
		final TestBuildBean test = template.get();
		
		assertEquals("testField should have been set", "4", test.testField());
		assertEquals("test2Field should have been set", "5", test.test2Field);
		assertEquals("test3Field should have not been set", null, test.test3Field());
		assertThat("test4Fields should be this list", test.test4Fields, is(Arrays.asList("1", "2", "3")));
		
		// Alternative form:
		
		final TestBuildBean test2 = BeanTemplateUtils.build(test)
				.with(TestBuildBean::testField, "5")
				.with("test2Field", "6")
				.with("test4Fields", Arrays.asList("4", "5", "6"))
				.done().get();
		
		assertEquals("testField should have been set", "5", test2.testField());
		assertEquals("test2Field should have been set", "6", test2.test2Field);
		assertEquals("test3Field should have not been set", null, test2.test3Field());
		assertThat("test4Fields should be this list", test2.test4Fields, is(Arrays.asList("4", "5", "6")));
		
	}
	
	@Rule
	public ExpectedException exception = ExpectedException.none();
	
	@Test
	public void testBeanBuilder_ContainersImmutable() {
		final TestBuildBean test = BeanTemplateUtils.build(TestBuildBean.class)
				.with(TestBuildBean::testField, "4")
				.with("test2Field", "5")
				.with("test4Fields", Arrays.asList("1", "2", "3"))
				.done().get();

		exception.expect(UnsupportedOperationException.class);
		test.test4Fields.add("INF");
	}

	public static class TestCloneBean {
		public String testField() { return testField; } /** Test field */
		public String test3Field() { return test3Field; } /** Test field */
		
		String testField;
		String test2Field;
		String test3Field;
		String test4Field;
		String test5Field;
		Map<String, Integer> test6Fields;
		
	}
	
	@Test
	public void testBeanCloner() {
		
		final TestCloneBean to_clone = new TestCloneBean();
		to_clone.testField = "1"; // change
		to_clone.test2Field = "2"; // null
		to_clone.test3Field = null; // write
		to_clone.test4Field = null; // leave
		to_clone.test5Field = "5"; // leave
		to_clone.test6Fields = new HashMap<String, Integer>();
		to_clone.test6Fields.put("6", 6);
		
		final TestCloneBean test = BeanTemplateUtils.clone(to_clone)
								.with(TestCloneBean::testField, "1b")
								.with("test2Field", null)
								.with(TestCloneBean::test3Field, "3")
								.with("test6Fields",
										ImmutableMap.<String, Integer>builder()
											.putAll(to_clone.test6Fields)
											.put("7", 7)
											.build()
										)
								.done();
		
		final HashMap<String, Integer> expected = new HashMap<String, Integer>();
		expected.put("6", 6);
		expected.put("7", 7);
		
		assertEquals("testField should have been changed", "1b", test.testField());
		assertEquals("test2Field should have nulled", null, test.test2Field);
		assertEquals("test3Field should have have been set", "3", test.test3Field());
		assertEquals("test4Field should have have been left null", null, test.test4Field);
		assertEquals("test5Field should have have been left 5", "5", test.test5Field);
		assertEquals("test6Fields should be this map", test.test6Fields, expected);		
	}

	public static class TestCloneBean2 extends TestCloneBean {
		String another_field1;
		String another_field2;
		public String another_field2() { return another_field2; }
	}
	
	
	@Test
	public void testBeanCloner_inherits() {
		
		final TestCloneBean2 to_clone = new TestCloneBean2();
		to_clone.testField = "1"; // change
		to_clone.test2Field = "2"; // null
		to_clone.test3Field = null; // write
		to_clone.test4Field = null; // leave
		to_clone.test5Field = "5"; // leave
		to_clone.test6Fields = new HashMap<String, Integer>();
		to_clone.test6Fields.put("6", 6);
		to_clone.another_field1 = "x";
		to_clone.another_field2 = "xx";
		
		final TestCloneBean2 test = BeanTemplateUtils.clone(to_clone)
								.with(TestCloneBean2::testField, "1b")
								.with("test2Field", null)
								.with(TestCloneBean2::another_field2, "yy")
								.with(TestCloneBean::test3Field, "3")
								.with("test6Fields",
										ImmutableMap.<String, Integer>builder()
											.putAll(to_clone.test6Fields)
											.put("7", 7)
											.build()
										)
								.done();
		
		final HashMap<String, Integer> expected = new HashMap<String, Integer>();
		expected.put("6", 6);
		expected.put("7", 7);
		
		assertEquals("testField should have been changed", "1b", test.testField());
		assertEquals("test2Field should have nulled", null, test.test2Field);
		assertEquals("test3Field should have have been set", "3", test.test3Field());
		assertEquals("test4Field should have have been left null", null, test.test4Field);
		assertEquals("test5Field should have have been left 5", "5", test.test5Field);
		assertEquals("test6Fields should be this map", test.test6Fields, expected);		
		assertEquals("x", test.another_field1);		
		assertEquals("yy", test.another_field2);		
	}
	
	
	@Test
	public void testBeanCloner_ContainersImmutable() {
		final TestCloneBean to_clone = new TestCloneBean();
		to_clone.testField = "1"; // change
		to_clone.test2Field = "2"; // null
		to_clone.test3Field = null; // write
		to_clone.test4Field = null; // leave
		to_clone.test5Field = "5"; // leave
		to_clone.test6Fields = new HashMap<String, Integer>();
		to_clone.test6Fields.put("6", 6);
		
		final TestCloneBean test = BeanTemplateUtils.clone(to_clone)
								.with(TestCloneBean::testField, "1b")
								.with("test2Field", null)
								.with(TestCloneBean::test3Field, "3")
								.with("test6Fields",
										ImmutableMap.<String, Integer>builder()
											.putAll(to_clone.test6Fields)
											.put("7", 7)
											.build()
										)
								.done();

		// Check to_clone hasn't changed
		assertEquals(to_clone.testField, "1");
		
		exception.expect(UnsupportedOperationException.class);
		
		test.test6Fields.put("INF", 0);
	}
	
	public static class TestDeserializeBean {
		Map<String, Object> testMap;
	}
	public static class TestDeserializeBean2 {
		Map<String, Double> testMap;
	}
	
	@Test
	public void testNumberDeserializer() {
		TestDeserializeBean bean1 = BeanTemplateUtils
				.from("{\"testMap\":{\"test\":1.0}}", TestDeserializeBean.class).get();
		JsonNode jn1 = BeanTemplateUtils.toJson(bean1);
		
		assertEquals("{\"testMap\":{\"test\":1}}", jn1.toString());
		
		
		TestDeserializeBean bean2 = BeanTemplateUtils
				.from("{\"testMap\":{\"test\":10000000000000,\"test1\":10000000000000.1,\"test2\":\"some string\"}}", TestDeserializeBean.class).get();
		JsonNode jn2 = BeanTemplateUtils.toJson(bean2);
		
		assertEquals("{\"testMap\":{\"test\":10000000000000,\"test1\":1.00000000000001E13,\"test2\":\"some string\"}}", jn2.toString());
		
		TestDeserializeBean bean3 = BeanTemplateUtils
				.from("{\"testMap\":{\"test\":1e7, \"test2\":1.0000000000000E7,\"test3\":1E19}}", TestDeserializeBean.class).get();
		JsonNode jn3 = BeanTemplateUtils.toJson(bean3);
		
		assertEquals("{\"testMap\":{\"test\":10000000,\"test2\":1.0E7,\"test3\":1.0E19}}", jn3.toString());
		
		//Mapper without the Number deserializer to make sure the double values are staying the same
		ObjectMapper plainMapper = new ObjectMapper();
		plainMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		plainMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);		
		plainMapper.setSerializationInclusion(Include.NON_NULL);
		
		
		//Checking that the deserializer doesn't break existing double values
		TestDeserializeBean bean4 = BeanTemplateUtils
				.from(
						"{\"testMap\":{\"test\":1e7, \"test2\":1.0000000000000E7,\"test3\":1.1,\"test4\":10000000000000.1,\"test4\":6e9,\"test5\":6e300,\"test5\":1E7 }}",
						TestDeserializeBean.class).get();
		JsonNode number_deserialized = BeanTemplateUtils.toJson(bean4);
		JsonNode plain = plainMapper.valueToTree(bean4);
		
		assertEquals(plain.toString(), number_deserialized.toString());
	
		// Just check it doesn't mess up actual double deserialization:
		
		TestDeserializeBean2 bean2_1 = BeanTemplateUtils
				.from("{\"testMap\":{\"test\":1.0}}", TestDeserializeBean2.class).get();
		JsonNode jn2_1 = BeanTemplateUtils.toJson(bean2_1);
		
		assertEquals("{\"testMap\":{\"test\":1.0}}", jn2_1.toString());
		
		
	}

	// Ended up not immutabilizing all these because you can lose too much information
	// So need to decide on a case by case basis...
	
//	public static class TestCollectionBean {
//		SortedSet<String> collectionTest1;
//		Set<String> collectionTest2;
//		Set<String> collectionTest2_change;
//		NavigableMap<String, String> collectionTest3;
//		SortedMap<String, String> collectionTest4;
//		Map<String, String> collectionTest5;
//		List<String> collectionTest6;
//		Multimap<String, String> collectionTest7;
//		ArrayDeque<String> collectionTest8;
//	}	
//	
//	@Test
//	public void collectionTest() {
//		
//		final TestCollectionBean test = new TestCollectionBean();
//		test.collectionTest1 = new TreeSet<String>(Arrays.asList("a"));
//		test.collectionTest2 = new HashSet<String>(Arrays.asList("b"));
//		test.collectionTest2_change = new HashSet<String>(Arrays.asList("b2"));
//		test.collectionTest3 = new ConcurrentSkipListMap<String, String>(); // (these are both navigable and sorted)
//		test.collectionTest3.put("c1", "c2");
//		test.collectionTest4 = new TreeMap<String, String>(); // (these are both navigable and sorted)
//		test.collectionTest4.put("d1", "d2");
//		test.collectionTest5 = new HashMap<String, String>(); // (these are both navigable and sorted)
//		test.collectionTest5.put("e1", "e2");
//		test.collectionTest6 = new ArrayList<String>(Arrays.asList("f"));
//		test.collectionTest7 = TreeMultimap.create();
//		test.collectionTest7.put("g1", "g2");
//		test.collectionTest8 = new ArrayDeque<String>(Arrays.asList("h"));
//		
//		final TestCollectionBean cloned = BeanTemplateUtils.clone(test).done();
//		
//		assertEquals(cloned.collectionTest1, test.collectionTest1);
//		assertEquals(cloned.collectionTest2, test.collectionTest2);
//		assertEquals(cloned.collectionTest3, test.collectionTest3);
//		assertEquals(cloned.collectionTest4, test.collectionTest4);
//		assertEquals(cloned.collectionTest5, test.collectionTest5);
//		assertEquals(cloned.collectionTest6, test.collectionTest6);
//		assertEquals(cloned.collectionTest7, test.collectionTest7);
//		assertEquals(cloned.collectionTest8, test.collectionTest8);
//		
//		@SuppressWarnings("unused")
//		final TestCollectionBean cloned2 = BeanTemplateUtils.clone(test)
//												.with("collectionTest2_change", new HashSet<String>(Arrays.asList("b3")))
//												.done();
//
//		assertEquals(test.collectionTest2_change, new HashSet<String>(Arrays.asList("b2")));
//	}
	
	public static class FieldTest {
		public Boolean is_transient() { return is_transient; }
		Boolean is_transient;
	}
	
	@Test
	public void test_jacksonGetterRead() {
		FieldTest x = BeanTemplateUtils.build(FieldTest.class)
				.with(FieldTest::is_transient, true)
				.done().get();
		String j = BeanTemplateUtils.toJson(x).toString();
		assertEquals("{\"is_transient\":true}", j);
	}
}
