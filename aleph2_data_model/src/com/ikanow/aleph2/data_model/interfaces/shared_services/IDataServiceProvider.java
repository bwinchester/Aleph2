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
package com.ikanow.aleph2.data_model.interfaces.shared_services;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;

/** Provides an optional generic data service interface
 * @author Alex
 */
public interface IDataServiceProvider {

	/** A collection of method calls that represents a generic data service in Aleph2
	 *  Not all of the methods make sense for all data services - the optional is used to indicate whether a particular method can be invoked (for a particular bucket)
	 * @author Alex
	 */
	public static interface IGenericDataService {
		
		/** Returns an IDataWriteService that can be written to. For more complex write operations (eg updates) on data services that support it, IDataWriteService.getCrudService can be used
		 * @param clazz - the class of the bean to use, or JsonNode.class for schema-less writes
		 * @param bucket - the bucket for which to write data (cannot be a multi bucket or alias)
		 * @param options - an arbitrary string whose function depends on the particular data service (eg "entity" or "association" to get a CRUD store from a graph DB)
		 * @param secondary_buffer - for "ping pong" type buckets, where the data is replaced atomically each run (or for modifying the contents of any bucket atomically) - use a secondary buffer (any string)
		 * @return optionally, a data write service that can be used to store and delete data
		 */
		<O> Optional<IDataWriteService<O>> getWritableDataService(final Class<O> clazz, final DataBucketBean bucket, final Optional<String> options, final Optional<String> secondary_buffer);
		
		/** Returns a full CRUD service (though may be functionally read-only, or maybe update-only) for the specified buckets 
		 * @param clazz - the class of the bean to use, or JsonNode.class for schema-less writes
		 * @param buckets - the buckets across which the CRUD operations will be applied
		 * @param options - an arbitrary string whose function depends on the particular data service (eg "entity" or "association" to get a CRUD store from a graph DB)
		 * @return optionally, a CRUD service pointing at the collection of buckets 
		 */
		<O> Optional<ICrudService<O>> getReadableCrudService(final Class<O> clazz, final Collection<DataBucketBean> buckets, final Optional<String> options);
		
		/** Returns the list of secondary buffers used with this bucket (so that they can be deleted)
		 * @param bucket
		 * @return
		 */
		Collection<String> getSecondaryBufferList(final DataBucketBean bucket);
		
		/** For "ping pong" buffers (or when atomically modifying the contents of any bucket), switches the "active" read buffer to the designated secondary buffer 
		 * @param bucket - the bucket to switch
		 * @param secondary_buffer - the name of the buffer
		 * @return a future containing the success/failure of the operation and associated status
		 */
		CompletableFuture<BasicMessageBean> switchCrudServiceToPrimaryBuffer(final DataBucketBean bucket, final Optional<String> secondary_buffer);
				
		/** Indicates that this service should examine the data in this bucket and delete any old data
		 * @param bucket - the bucket to be checked
		 * @return a future containing the success/failure of the operation and associated status (if success==true, create a detail called "loggable" in order to log the result)
		 */
		CompletableFuture<BasicMessageBean> handleAgeOutRequest(final DataBucketBean bucket);
		
		/** Deletes or purges the bucket
		 * @param bucket - the bucket to be deleted/purged
		 * @param secondary_buffer - if this is specified then only the designated secondary buffer for the bucket is deleted
		 * @param bucket_getting_deleted - whether this operation is part of a full deletion of the bucket
		 * @return a future containing the success/failure of the operation and associated status
		 */
		CompletableFuture<BasicMessageBean> handleBucketDeletionRequest(final DataBucketBean bucket, final Optional<String> secondary_buffer, final boolean bucket_getting_deleted);
	};
	
	/** If this service instance has a data service associated with it, then return an interface that enables its use 
	 * @return the generic data service interface
	 */
	default Optional<IGenericDataService> getDataService() {
		return Optional.empty();
	}
	
}
