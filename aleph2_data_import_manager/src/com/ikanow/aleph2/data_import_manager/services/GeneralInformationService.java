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
package com.ikanow.aleph2.data_import_manager.services;


import com.ikanow.aleph2.data_import_manager.harvest.utils.HostInformationUtils;

/** 
 * @author acp
 *
 */
public class GeneralInformationService {

	/** Returns the hostname for this host 
	 * @return
	 */
	public String getHostname() {
		return HostInformationUtils.getHostname();
	}
	
	/** Returns a process UUID assigned once during the lifetime of the process 
	 * @return
	 */
	public String getProcessUuid() {
		return HostInformationUtils.getProcessUuid();
	}	
}
