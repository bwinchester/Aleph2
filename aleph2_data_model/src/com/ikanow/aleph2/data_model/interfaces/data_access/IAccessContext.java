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
package com.ikanow.aleph2.data_model.interfaces.data_access;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;

/**
 * Gives an app access to all the currently configured data services.
 * 
 * @author Burch
 *
 */
public interface IAccessContext extends IServiceContext {	
	
	/** (Should never be called by clients) this is used by the infrastructure to set up external contexts
	 * @param signature the string returned from getHarvestContextSignature
	 */
	void initializeNewContext(String string);
	
	/////////////////////////////////////////////////////////////////////
	
	// Real-time access to system components
	
	/** Enables access modules to subscribe to objects being generated by other components in the system
	 * @param bucket - the bucket to monitor
	 * @param stage - if set to Optionals.empty() then occurs post enrichment. If set to "" then occurs pre-enrichment. Otherwise should be the name of a module - will listen immediately after that. 
	 * @param on_new_object_callback - a void function taking a JsonNode (the object) 
	 * @return a future that completes when the subscription has occurred, describing its success or failure
	 */
	CompletableFuture<BasicMessageBean> subscribeToBucket(@NonNull DataBucketBean bucket, @NonNull Optional<String> stage, Consumer<JsonNode> on_new_object_callback);
	
	/** Enables access modules to subscribe to objects being generated by other components in the system
	 * @param analytic_thread - the thread to monitor
	 * @param stage - if set to Optionals.empty() then occurs post processing. If set to "" then occurs pre-processing. Otherwise should be the name of a module - will listen immediately after that. 
	 * @param on_new_object_callback - a void function taking a JsonNode (the object) 
	 * @return a future that completes when the subscription has occurred, describing its success or failure
	 */
	CompletableFuture<BasicMessageBean> subscribeToAnalyticThread(@NonNull AnalyticThreadBean analytic_thread, @NonNull Optional<String> stage, Consumer<JsonNode> on_new_object_callback);

	/** Enables access modules to subscribe to objects being generated by other components in the system
	 * @param bucket - the bucket to monitor
	 * @param stage - if set to Optionals.empty() then occurs post enrichment. If set to "" then occurs pre-enrichment. Otherwise should be the name of a module - will listen immediately after that. 
	 * @return a future returning stream of Json nodes as soon as the connection is established
	 */
	CompletableFuture<Stream<JsonNode>> getObjectStreamFromBucket(@NonNull DataBucketBean bucket, @NonNull Optional<String> stage);
	
	/** Enables access modules to subscribe to objects being generated by other components in the system
	 * @param analytic_thread - the analytic thread to monitor
	 * @param stage - if set to Optionals.empty() then occurs post processing. If set to "" then occurs pre-processing. Otherwise should be the name of a module - will listen immediately after that. 
	 * @return a future returning stream of Json nodes as soon as the connection is established
	 */
	Stream<JsonNode> getObjectStreamFromAnalyticThread(@NonNull AnalyticThreadBean analytic_thread, @NonNull Optional<String> stage);	
	
}
