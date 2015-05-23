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
package com.ikanow.aleph2.data_import_manager.utils;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;

import com.ikanow.aleph2.data_import_manager.harvest.utils.HarvestErrorUtils;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;

import fj.data.Either;

public class ClassloaderUtils {

	//TODO (ALEPH-19): want a cache in here to avoid doing it over and over again (but reloading whenever anything changes)
	
	/** Returns an instance of the requested class from the designated classpath (union of the libs below)
	 * @param primary_lib - optionally, a single library
	 * @param secondary_libs - optionally a set of other libraries
	 * @return an instance of the desired function
	 */
	@NonNull
	public static <R, M> Either<BasicMessageBean, R> getFromCustomClasspath(
													final @NonNull Class<R> interface_clazz,
													final @NonNull String implementation_classname,
													final @NonNull Optional<String> primary_lib, 
													final @NonNull List<String> secondary_libs,
													final @NonNull String handler_for_errors,
													final @NonNull M msg_for_errors
													)
	{
		try {
			final JarClassLoader jcl = new JarClassLoader();
			if (primary_lib.isPresent()) {
				jcl.add(new URL(primary_lib.get()));
			}
			secondary_libs.forEach(j -> { 
				try {
					jcl.add(new URL(j));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			
			final JclObjectFactory factory = JclObjectFactory.getInstance();
		
			@SuppressWarnings("unchecked")
			final R ret_val = (R) factory.create(jcl, implementation_classname);
			if (null == ret_val) {
				throw new RuntimeException("Unknown error");
			}
			return Either.right(ret_val);
		}
		catch (Exception e) {
			return Either.left(HarvestErrorUtils.buildErrorMessage(handler_for_errors, 
							msg_for_errors, 
							HarvestErrorUtils.getLongForm(HarvestErrorUtils.ERROR_LOADING_CLASS, e, implementation_classname) 
							));
		}
	}	
}