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

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;


/**
 * Utility class that converts a null collection/iterable into an empty one
 */
public class Optionals {
	
	/** Designed for low performance evaluation of a chain of accessors, eg a.b().c().d() - if any of them are empty then returns Optiona.empty()
	 *  eg in the above case you just call Optionals.of(() -> a.b().c().d())
	 * @param accessor_chain
	 * @return
	 */
	public static <T> Optional<T> of(final Supplier<T> accessor_chain) {
		try {
			return Optional.ofNullable(accessor_chain.get());
		}
		catch (Exception e) {
			return Optional.empty();
		}
	}
	
	/** Returns the first element of a list that can be empty or null, with an empty option
	 *  NOTE: the element inside can be null
	 * @param a collection of Ts
	 * @return the first element, or an empty optional if it's null or empty
	 */
	public static <T> Optional<T> first(final Iterable<T> ts) {
		try {
			return Optional.of(ts.iterator().next());
		}
		catch (Exception e) { // empty
			return Optional.empty();
		}
	}
	
	/**
	 * @param a collection of Ts
	 * @return the collection, or an empty collection if "ts" is null
	 */
	public static <T> Collection<T> ofNullable(final Collection<T> ts) {
		return Optional.ofNullable(ts).orElse(Collections.emptyList());
	}
	/**
	 * @param ts
	 * @return the iterable, or an empty iterable if "ts" is null
	 */
	public static <T> Iterable<T> ofNullable(final Iterable<T> ts) {
		return Optional.ofNullable(ts).orElse(Collections.emptyList());
	}
}