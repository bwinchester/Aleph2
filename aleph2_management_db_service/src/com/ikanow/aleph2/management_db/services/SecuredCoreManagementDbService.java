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
package com.ikanow.aleph2.management_db.services;

import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IServiceContext;
import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.data_model.objects.shared.SharedLibraryBean;

public class SecuredCoreManagementDbService extends CoreManagementDbService {

	protected AuthorizationBean authorizationBean = null;
	
	public SecuredCoreManagementDbService(IServiceContext service_context, DataBucketCrudService data_bucket_service,
			DataBucketStatusCrudService data_bucket_status_service, SharedLibraryCrudService shared_library_service,
			ManagementDbActorContext actor_context) {
		super(service_context, data_bucket_service, data_bucket_status_service, shared_library_service, actor_context);		
	}

	public SecuredCoreManagementDbService(IServiceContext service_context, IManagementDbService _underlying_management_db, DataBucketCrudService data_bucket_service,
			DataBucketStatusCrudService data_bucket_status_service, SharedLibraryCrudService shared_library_service,
			ManagementDbActorContext actor_context,AuthorizationBean authorizationBean) {
		super(service_context, data_bucket_service, data_bucket_status_service, shared_library_service, actor_context);
		this.authorizationBean  = authorizationBean;
	}

	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService#getSharedLibraryStore()
	 */
	@Override
	public IManagementCrudService<SharedLibraryBean> getSharedLibraryStore() {
		return super.getSharedLibraryStore().secured(_service_context,authorizationBean);
	}


}
