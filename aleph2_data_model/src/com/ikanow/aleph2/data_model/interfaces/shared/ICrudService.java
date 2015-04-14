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
package com.ikanow.aleph2.data_model.interfaces.shared;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.ikanow.aleph2.data_model.utils.CrudUtils;

/** A generic interface to an "object" datastore with a "MongoDB like" interface
 * @author acp
 *
 * @param <T> the bean type served by this repository
 */
public interface ICrudService<O> {

	//////////////////////////////////////////////////////
	
	// *C*REATE
	
	/** Stores the specified object in the database, optionally failing if it is already present
	 *  If the "_id" field of the object is not set then it is assigned
	 * @param new_object
	 * @param replace_if_present if true then any object with the specified _id is overwritten
	 * @return An optional containing the object, or Optional.empty() if not present 
	 */
	Future<Optional<O>> storeObject(@NonNull O new_object, boolean replace_if_present);

	/** Stores the specified object in the database, failing if it is already present
	 *  If the "_id" field of the object is not set then it is assigned
	 * @param new_object
	 * @return An optional containing the object, or Optional.empty() if not present 
	 */
	Future<Optional<O>> storeObject(@NonNull O new_object);
	
	//////////////////////////////////////////////////////
	
	// *R*ETRIEVE
	
	/** Registers that you wish to optimize sprecific queries
	 * @param ordered_field_list a list of the fields in the query
	 */
	void optimizeQuery(@NonNull List<String> ordered_field_list);	

	/** Registers that you wish to optimize sprecific queries
	 * @param A specification that must be initialized via CrudUtils.anyOf(new O()) and then the desired fields added via .exists(<field or getter>)
	 */
	void optimizeQuery(CrudUtils.@NonNull QueryComponent<O> spec);		
	
	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param spec A specification generated by CrudUtils.allOf(o) (all fields must be match) or CrudUtils.anyOf(o) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectBySpec(CrudUtils.@NonNull QueryComponent<O> unique_spec);

	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param spec A specification generated by CrudUtils.allOf(o) (all fields must be match) or CrudUtils.anyOf(o) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param field_list List of fields to return, supports "." nesting
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectBySpec(CrudUtils.@NonNull QueryComponent<O> unique_spec, @NonNull List<String> field_list);

	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param spec TODO   
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectBySpec(CrudUtils.@NonNull MultiQueryComponent<O> unique_multi_spec);

	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param spec TODO   
	 * @param field_list List of fields to return, supports "." nesting
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectBySpec(CrudUtils.@NonNull MultiQueryComponent<O> unique_multi_spec, @NonNull List<String> field_list);
	/** Returns the object given the id
	 * @param id the id of the object
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectById(@NonNull String id);	

	/** Returns the object given the id
	 * @param id the id of the object
	 * @param field_list List of fields to return, supports "." nesting
	 * @return A future containing an optional containing the object, or Optionl.empty() 
	 */
	Future<Optional<O>> getObjectById(@NonNull String id, @NonNull List<String> field_list);	
	
	/** Returns the list of objects specified by the spec
	 * @param spec A specification generated by CrudUtils.allOf(o) (all fields must be match) or CrudUtils.anyOf(o) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param field_list List of fields to return, supports "." nesting
	 * @return A future containing a (possibly empty) list of Os 
	 */
	Future<List<O>> getObjectsBySpec(CrudUtils.@NonNull QueryComponent<O> spec, @NonNull List<String> field_list);

	/** Returns the list of objects specified by the spec (all fields returned)
	 * @param spec A specification generated by CrudUtils.allOf(o) (all fields must be match) or CrudUtils.anyOf(o) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing a (possibly empty) list of Os 
	 */
	Future<List<O>> getObjectsBySpec(CrudUtils.@NonNull QueryComponent<O> spec);
	
	/** Returns the list of objects specified by the spec
	 * @param spec TODO   
	 * @param field_list List of fields to return, supports "." nesting
	 * @return A future containing a (possibly empty) list of Os 
	 */
	Future<List<O>> getObjectsBySpec(CrudUtils.@NonNull MultiQueryComponent<O> multi_spec, @NonNull List<String> field_list);

	/** Returns the list of objects specified by the spec (all fields returned)
	 * @param spec TODO   
	 * @return A future containing a (possibly empty) list of Os 
	 */
	Future<List<O>> getObjectsBySpec(CrudUtils.@NonNull MultiQueryComponent<O> multi_spec);
	
	//////////////////////////////////////////////////////
	
	// *U*PDATE
	
	//TODO
	/**
	 * @param id the id of the object to update
	 * @param set overwrites any fields
	 * @param add increments numbers or adds to sets/lists
	 * @param remove decrements numbers of removes from sets/lists
	 * @return a future describing if the update was successful
	 */
	Future<Boolean> updateObjectById(String id, Optional<O> set, Optional<O> add, Optional<O> remove);

	//TODO
	Future<Boolean> updateObjectBySpec(@NonNull O unique_spec, Optional<O> set, Optional<O> add, Optional<O> remove);

	//TODO atomic get-and-update
	
	//TODO bulk update
	
	//////////////////////////////////////////////////////
	
	// *D*ELETE
	
	//TODO
	Future<Boolean> deleteObjectById(@NonNull String id);

	//TODO
	Future<Boolean> deleteObjectBySpec(CrudUtils.@NonNull QueryComponent<O> unique_spec);

	//TODO
	Future<Boolean> deleteObjectBySpec(CrudUtils.@NonNull MultiQueryComponent<O> unique_multi_spec);
	
	//TODO bulk update	
	
	//////////////////////////////////////////////////////
	
	// OTHER:
	
	/** USE WITH CARE: this returns the driver to the underlying technology
	 *  shouldn't be used unless absolutely necessary!
	 * @param driver_class the class of the driver
	 * @param a string containing options in some technology-specific format
	 * @return a driver to the underlying technology. Will exception if you pick the wrong one!
	 */
	<T> T getUnderlyingPlatformDriver(Class<T> driver_class, Optional<String> driver_options);
}