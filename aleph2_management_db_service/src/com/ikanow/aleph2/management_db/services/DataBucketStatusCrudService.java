/*******************************************************************************
* Copyright 2015, The IKANOW Open Source Project.
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License, version 3,
* as published by the Free Software Foundation.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package com.ikanow.aleph2.management_db.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.NonNull;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IBasicSearchService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IServiceContext;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketStatusBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;
import com.ikanow.aleph2.data_model.objects.shared.ProjectBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils.MethodNamingHelper;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.ErrorUtils;
import com.ikanow.aleph2.data_model.utils.FutureUtils;
import com.ikanow.aleph2.data_model.utils.Lambdas;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent;
import com.ikanow.aleph2.data_model.utils.FutureUtils.ManagementFuture;
import com.ikanow.aleph2.data_model.utils.Patterns;
import com.ikanow.aleph2.management_db.data_model.BucketActionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketActionRetryMessage;
import com.ikanow.aleph2.management_db.utils.ManagementDbErrorUtils;
import com.ikanow.aleph2.management_db.utils.MgmtCrudUtils;

//TODO (ALEPH-19): No method currently to access the harvest/enrichment/storage logs

/** CRUD service for Data Bucket Status with management proxy
 * @author acp
 */
public class DataBucketStatusCrudService implements IManagementCrudService<DataBucketStatusBean> {

	protected final IStorageService _storage_service;
	protected final IManagementDbService _underlying_management_db;
	
	protected final ICrudService<DataBucketBean> _underlying_data_bucket_db;
	protected final ICrudService<DataBucketStatusBean> _underlying_data_bucket_status_db;
	protected final ICrudService<BucketActionRetryMessage> _bucket_action_retry_store;
	
	protected final ManagementDbActorContext _actor_context;
	
	/** Guice invoked constructor
	 * @param underlying_management_db
	 */
	@Inject
	public DataBucketStatusCrudService(final IServiceContext service_context, ManagementDbActorContext actor_context)
	{
		_underlying_management_db = service_context.getService(IManagementDbService.class, Optional.empty());
		_underlying_data_bucket_db = _underlying_management_db.getDataBucketStore();
		_underlying_data_bucket_status_db = _underlying_management_db.getDataBucketStatusStore();
		_bucket_action_retry_store = _underlying_management_db.getRetryStore(BucketActionRetryMessage.class);
		
		_storage_service = service_context.getStorageService();
		
		_actor_context = actor_context;
	}

	/** User constructor, for wrapping
	 * @param underlying_management_db
	 * @param underlying_data_bucket_db
	 */
	public DataBucketStatusCrudService(final @NonNull IManagementDbService underlying_management_db, 
			final @NonNull IStorageService storage_service,
			final @NonNull ICrudService<DataBucketBean> underlying_data_bucket_db,
			final @NonNull ICrudService<DataBucketStatusBean> underlying_data_bucket_status_db			
			)
	{
		_underlying_management_db = underlying_management_db;
		_underlying_data_bucket_db = underlying_data_bucket_db;
		_underlying_data_bucket_status_db = underlying_data_bucket_status_db;
		_bucket_action_retry_store = _underlying_management_db.getRetryStore(BucketActionRetryMessage.class);
		_actor_context = ManagementDbActorContext.get();		
		_storage_service = storage_service;
	}
	
	@Override
	public @NonNull boolean deregisterOptimizedQuery(
			@NonNull List<String> ordered_field_list) {
		return _underlying_data_bucket_status_db.deregisterOptimizedQuery(ordered_field_list);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getSearchService()
	 */
	@Override
	public @NonNull Optional<IBasicSearchService<DataBucketStatusBean>> getSearchService() {
		return _underlying_data_bucket_status_db.getSearchService();
	}

	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getUnderlyingPlatformDriver(java.lang.Class, java.util.Optional)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> @NonNull T getUnderlyingPlatformDriver(
			@NonNull Class<T> driver_class, Optional<String> driver_options) {
		if (driver_class == ICrudService.class) {
			return (@NonNull T) _underlying_data_bucket_status_db;
		}
		else {
			throw new RuntimeException("DataBucketCrudService.getUnderlyingPlatformDriver not supported");
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getFilteredRepo(java.lang.String, java.util.Optional, java.util.Optional)
	 */
	@Override
	public @NonNull IManagementCrudService<DataBucketStatusBean> getFilteredRepo(
			@NonNull String authorization_fieldname,
			Optional<AuthorizationBean> client_auth,
			Optional<ProjectBean> project_auth) {
		return new DataBucketStatusCrudService(_underlying_management_db.getFilteredDb(client_auth, project_auth), 
				_storage_service,
				_underlying_data_bucket_db.getFilteredRepo(authorization_fieldname, client_auth, project_auth),
				_underlying_data_bucket_status_db.getFilteredRepo(authorization_fieldname, client_auth, project_auth)
				);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#storeObject(java.lang.Object, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Supplier<Object>> storeObject(
			@NonNull DataBucketStatusBean new_object, boolean replace_if_present) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#storeObject(java.lang.Object)
	 */
	@Override
	public @NonNull ManagementFuture<Supplier<Object>> storeObject(
			@NonNull DataBucketStatusBean new_object) {
		return this.storeObject(new_object, false);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#storeObjects(java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(
			@NonNull List<DataBucketStatusBean> new_objects, boolean continue_on_error) {
		throw new RuntimeException("This method is not supported, call storeObject on each object separately");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#storeObjects(java.util.List)
	 */
	@Override
	public @NonNull ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(
			@NonNull List<DataBucketStatusBean> new_objects) {
		return this.storeObjects(new_objects, false);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#optimizeQuery(java.util.List)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> optimizeQuery(
			@NonNull List<String> ordered_field_list) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.optimizeQuery(ordered_field_list));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketStatusBean>> getObjectBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> unique_spec) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectBySpec(unique_spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketStatusBean>> getObjectBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> unique_spec,
			@NonNull List<String> field_list, boolean include) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectBySpec(unique_spec, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectById(java.lang.Object)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketStatusBean>> getObjectById(
			@NonNull Object id) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectById(id));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectById(java.lang.Object, java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketStatusBean>> getObjectById(
			@NonNull Object id, @NonNull List<String> field_list,
			boolean include) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectById(id, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Cursor<DataBucketStatusBean>> getObjectsBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> spec) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectsBySpec(spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Cursor<DataBucketStatusBean>> getObjectsBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> spec,
			@NonNull List<String> field_list, boolean include) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.getObjectsBySpec(spec, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#countObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Long> countObjectsBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> spec) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.countObjectsBySpec(spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#countObjects()
	 */
	@Override
	public @NonNull ManagementFuture<Long> countObjects() {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_status_db.countObjects());
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectById(java.lang.Object, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> updateObjectById(
			@NonNull Object id, @NonNull UpdateComponent<DataBucketStatusBean> update) {
		return this.updateObjectBySpec(CrudUtils.allOf(DataBucketStatusBean.class).when(DataBucketStatusBean::_id, id), Optional.of(false), update);
	}

	@NonNull
	private static Collection<BasicMessageBean> validateUpdateCommand(UpdateComponent<DataBucketStatusBean> update) {
		final MethodNamingHelper<DataBucketStatusBean> helper = BeanTemplateUtils.from(DataBucketStatusBean.class); 
		
		// There's a limited set of operations that are permitted:
		// - change the number of objects
		// - suspend or resume
		// - quarantine		
		// - add/remove log or status messages
		
		final ImmutableSet<String> statusFields = ImmutableSet.<String>builder()
														.add(helper.field(DataBucketStatusBean::last_harvest_status_messages))
														.add(helper.field(DataBucketStatusBean::last_enrichment_status_messages))
														.add(helper.field(DataBucketStatusBean::last_storage_status_messages))
														.build();
														
		final List<BasicMessageBean> errors = update.getAll().entries().stream()
			.map(kvtmp -> Patterns.match(kvtmp)
							.<BasicMessageBean>andReturn()
							.when(kv -> helper.field(DataBucketStatusBean::num_objects).equals(kv.getKey()) 
									&& (kv.getValue()._1().equals(CrudUtils.UpdateOperator.set)
											|| kv.getValue()._1().equals(CrudUtils.UpdateOperator.increment))
									&& (kv.getValue()._2() instanceof Number)
									,
									__ -> { return null; })
							.when(kv -> helper.field(DataBucketStatusBean::suspended).equals(kv.getKey()) 
									&& kv.getValue()._1().equals(CrudUtils.UpdateOperator.set)
									&& (kv.getValue()._2() instanceof Boolean)
									,
									__ -> { return null; })
							.when(kv -> helper.field(DataBucketStatusBean::quarantined_until).equals(kv.getKey()) 
									&& ((kv.getValue()._1().equals(CrudUtils.UpdateOperator.set)
											&& (kv.getValue()._2() instanceof Date))
										|| kv.getValue()._1().equals(CrudUtils.UpdateOperator.unset))
									,
									__ -> { return null; })
							.when(kv -> statusFields.contains(kv.getKey().split("[.]"))
									,
									__ -> { return null; })
							.otherwise(kv -> { 
								return new BasicMessageBean(
										new Date(), // date
										false, // success
										"CoreManagementDbService",
										BucketActionMessage.UpdateBucketStateActionMessage.class.getSimpleName(),
										null, // message code
										ErrorUtils.get(ManagementDbErrorUtils.ILLEGAL_UPDATE_COMMAND, kv.getKey(), kv.getValue()._1()),
										null); // details						
							})
			)
			.filter(errmsg -> errmsg != null)
			.collect(Collectors.toList());
		
		return errors;
	}
	
	/** Tries to distribute a request to listening data import managers to notify their harvesters that the bucket state has been updated
	 * @param update_reply - the future reply to the find-and-update
	 * @param suspended_predicate - takes the status bean (must exist at this point) and checks whether the bucket should be suspended
	 * @param underlying_data_bucket_db - the data bucket bean db store
	 * @param actor_context - actor context for distributing out requests
	 * @param retry_store - the retry store for handling data import manager connectivity problems
	 * @return a collection of success/error messages from either this function or the 
	 */
	private static <T> CompletableFuture<Collection<BasicMessageBean>> getOperationFuture(
			final @NonNull CompletableFuture<Optional<DataBucketStatusBean>> update_reply, 
			final @NonNull Predicate<DataBucketStatusBean> suspended_predicate,
			final @NonNull ICrudService<DataBucketBean> underlying_data_bucket_db,
			final @NonNull ManagementDbActorContext actor_context,
			final @NonNull ICrudService<BucketActionRetryMessage> retry_store
			)
	{		
		return update_reply.thenCompose(
				sb -> {
					return sb.isPresent()
							? underlying_data_bucket_db.getObjectById(sb.get()._id())
							: CompletableFuture.completedFuture(Optional.empty());
				})
				.thenCompose(bucket -> {
					if (!bucket.isPresent()) {
						return CompletableFuture.completedFuture(Arrays.asList(new BasicMessageBean(
										new Date(), // date
										false, // success
										"CoreManagementDbService",
										BucketActionMessage.UpdateBucketStateActionMessage.class.getSimpleName(),
										null, // message code
										ErrorUtils.get(ManagementDbErrorUtils.MISSING_STATUS_BEAN_OR_BUCKET, 
												update_reply.join().map(s -> s._id()).orElse("(unknown)")),
										null) // details						
								));
					}
					else { // If we're here we've retrieved both the bucket and bucket status, so we're good to go
						final DataBucketStatusBean status_bean = update_reply.join().get(); 
							// (as above, if we're here then must exist)
						
						// Once we have the bucket, issue the update command
						final BucketActionMessage.UpdateBucketStateActionMessage update_message = new
									BucketActionMessage.UpdateBucketStateActionMessage(bucket.get(), 
											suspended_predicate.test(status_bean), // (ie user picks whether to suspend or unsuspend here)
											new HashSet<String>(
													Optional.ofNullable(status_bean.node_affinity())
													.orElse(Collections.emptyList())
													));
						
						// Collect message and handle retries
						
						final CompletableFuture<Collection<BasicMessageBean>> management_results =
							MgmtCrudUtils.applyRetriableManagementOperation(actor_context, retry_store, 
									update_message, source -> {
										return new BucketActionMessage.UpdateBucketStateActionMessage(
												update_message.bucket(), status_bean.suspended(),
												new HashSet<String>(Arrays.asList(source)));	
									});
							
						return management_results;										
					}
				});
	}
	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> updateObjectBySpec(
			final @NonNull QueryComponent<DataBucketStatusBean> unique_spec,
			final Optional<Boolean> upsert,
			final @NonNull UpdateComponent<DataBucketStatusBean> update)		
	{
		final MethodNamingHelper<DataBucketStatusBean> helper = BeanTemplateUtils.from(DataBucketStatusBean.class); 
		
		if (upsert.orElse(false)) {
			throw new RuntimeException("This method is not supported with upsert set and true");			
		}
		
		final Collection<BasicMessageBean> errors = validateUpdateCommand(update);
		if (!errors.isEmpty()) {
			return FutureUtils.createManagementFuture(CompletableFuture.completedFuture(false),
														CompletableFuture.completedFuture(errors));
		}

		// Now perform the update and based on the results we may need to send out instructions
		// to any listening buckets

		final CompletableFuture<Optional<DataBucketStatusBean>> update_reply = _underlying_data_bucket_status_db.
			updateAndReturnObjectBySpec(unique_spec, Optional.of(false), update, Optional.of(false),
										 Arrays.asList(
												 helper.field(DataBucketStatusBean::_id),
												 helper.field(DataBucketStatusBean::suspended), 
												 helper.field(DataBucketStatusBean::quarantined_until),
												 helper.field(DataBucketStatusBean::node_affinity)), 
											true);
		
		try {
			// What happens now depends on the contents of the message			
			
			// Maybe the user wanted to suspend/resume the bucket:
			
			final CompletableFuture<Collection<BasicMessageBean>> suspend_future = 
				Lambdas.<CompletableFuture<Collection<BasicMessageBean>>>exec(() -> {					
					if (update.getAll().containsKey(helper.field(DataBucketStatusBean::suspended))) {
						
						return getOperationFuture(update_reply, sb -> sb.suspended(),
								_underlying_data_bucket_db, _actor_context, _bucket_action_retry_store
								);
					}
					else { // (this isn't an error, just nothing to do here)
						return CompletableFuture.completedFuture(Collections.<BasicMessageBean>emptyList());
					}
			});
					
			// Maybe the user wanted to set quarantine on/off:
			
			final CompletableFuture<Collection<BasicMessageBean>> quarantine_future = 
				Lambdas.<CompletableFuture<Collection<BasicMessageBean>>>exec(() -> {					
					if (update.getAll().containsKey(helper.field(DataBucketStatusBean::quarantined_until))) {
						
						return getOperationFuture(update_reply, 
								sb -> { // (this predicate is slightly more complex)
									return (null != sb.quarantined_until()) || (new Date().getTime() < sb.quarantined_until().getTime());
								},
								_underlying_data_bucket_db, _actor_context, _bucket_action_retry_store
								);
					}
					else { // (this isn't an error, just nothing to do here)
						return CompletableFuture.completedFuture(Collections.<BasicMessageBean>emptyList());
					}
			});
			
			return FutureUtils.createManagementFuture(
									update_reply.thenApply(o -> o.isPresent()), // whether we updated
									suspend_future.thenCombine(quarantine_future, 
											(f1, f2) -> Stream.concat(f1.stream(), f2.stream()).collect(Collectors.toList())));
										//(+combine error messages from suspend/quarantine operations)
		}			
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}		
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Long> updateObjectsBySpec(
			final @NonNull QueryComponent<DataBucketStatusBean> spec,
			final Optional<Boolean> upsert,
			final @NonNull UpdateComponent<DataBucketStatusBean> update)
	{	
		if (upsert.orElse(false)) {
			throw new RuntimeException("This method is not supported with upsert set and true");			
		}
		
		final CompletableFuture<Cursor<DataBucketStatusBean>> affected_ids =
				_underlying_data_bucket_status_db.getObjectsBySpec(spec, 
						Arrays.asList(BeanTemplateUtils.from(DataBucketStatusBean.class).field(DataBucketStatusBean::_id)), true);
		
		try {
			return MgmtCrudUtils.applyCrudPredicate(affected_ids, status -> this.updateObjectById(status._id(), update));
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateAndReturnObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent, java.util.Optional, java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketStatusBean>> updateAndReturnObjectBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> unique_spec,
			Optional<Boolean> upsert,
			@NonNull UpdateComponent<DataBucketStatusBean> update,
			Optional<Boolean> before_updated, @NonNull List<String> field_list,
			boolean include) {
		throw new RuntimeException("This method is not supported (use non-atomic update/get instead)");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#deleteObjectById(java.lang.Object)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> deleteObjectById(
			@NonNull Object id) {
		throw new RuntimeException("This method is not supported (delete the bucket instead)");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#deleteObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> deleteObjectBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> unique_spec) {
		throw new RuntimeException("This method is not supported (delete the bucket instead)");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#deleteObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Long> deleteObjectsBySpec(
			@NonNull QueryComponent<DataBucketStatusBean> spec) {
		throw new RuntimeException("This method is not supported (delete the bucket instead)");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#deleteDatastore()
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> deleteDatastore() {
		throw new RuntimeException("This method is not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#getRawCrudService()
	 */
	@Override
	public @NonNull IManagementCrudService<JsonNode> getRawCrudService() {
		throw new RuntimeException("DataBucketStatusCrudService.getRawCrudService not supported");
	}

}