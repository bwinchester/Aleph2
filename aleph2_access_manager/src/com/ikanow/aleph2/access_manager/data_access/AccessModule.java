package com.ikanow.aleph2.access_manager.data_access;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IExtraDependencyLoader;
import com.ikanow.aleph2.data_model.objects.shared.ConfigDataServiceEntry;
import com.ikanow.aleph2.data_model.utils.PropertiesUtils;
import com.typesafe.config.Config;

public class AccessModule extends AbstractModule {
	private final Logger logger =  Logger.getLogger(AccessModule.class.getName());
	private final String DATA_SERVICES_PROPERTY = "data_service";
	private Set<Class<?>> interfaceHasDefault = new HashSet<Class<?>>();
	private final Config config;
	
	public AccessModule(@NonNull Config config) {
		this.config = config;
	}
	
	//THIS CLASS NEEDS TO BE USED TO SETUP ACCESSCONTEXT IF THATS SOMETHING
	//WE NEED INJECTION FOR
	
	
	/**
	 * Handles reading the config file to do the interface -> service w/ annotation bindings
	 * 
	 */
	@Override
	protected void configure() {
//		List<ConfigDataServiceEntry> dataServiceProperties = PropertiesUtils.getDataServiceProperties(config, DATA_SERVICES_PROPERTY);
//		dataServiceProperties.stream()
//			.forEach( entry -> bindDataServiceEntry(entry));
	}
	
//	@SuppressWarnings("rawtypes")
//	private void bindDataServiceEntry(@NonNull ConfigDataServiceEntry entry) {
//		System.out.println("BINDING: " + entry.annotationName + " " + entry.interfaceName + " " + entry.serviceName + " " + entry.isDefault);
//		Class serviceClazz = null;
//		try {
//			serviceClazz = Class.forName(entry.serviceName);
//		} catch (ClassNotFoundException e) {
//			this.addError(new Exception(entry.serviceName + " could not be converted to a class"));
//		}
//		//if serviceClazz implements IExtraDepedency then add those bindings
//		if ( IExtraDependencyLoader.class.isAssignableFrom(serviceClazz) ) {
//			System.out.println("Loading Extra Depedency Modules");
//			try {	
//				//TODO force the modules to conform to our naming convetion of "{module_name}.{user_defined_name}" somehow
//				//TODO these deps are going into the global binder, so they fail because 2 things can make the same binding
//				List<Module> modules = (List<Module>) serviceClazz.getMethod("getExtraDependencyModules", null).invoke(null, null);
//				modules.stream().forEach(module -> install(module));				
//				//TODO what can I do with this?
//			} catch (IllegalAccessException | IllegalArgumentException
//					| InvocationTargetException | NoSuchMethodException
//					| SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		
//		Optional<Class> interfaceClazz = Optional.empty();
//		if ( entry.interfaceName.isPresent() )
//			try {
//				interfaceClazz = Optional.of(Class.forName(entry.interfaceName.get()));
//				//check default
//				if ( entry.isDefault ) {
//					if (interfaceHasDefault.contains(interfaceClazz.get()))
//						this.addError(new Exception(entry.interfaceName + " already had a default binding, there can be only one."));
//					else
//						interfaceHasDefault.add(interfaceClazz.get());
//				}
//			} catch (ClassNotFoundException e) {
//				this.addError(new Exception(entry.interfaceName + " could not be converted to a class"));
//			}
//		//TODO handle modules
//		bindDataService(serviceClazz, interfaceClazz, entry.annotationName, entry.isDefault);
//	}
//	
//	@SuppressWarnings({ "unchecked", "rawtypes" })
//	private void bindDataService(@NonNull Class serviceClazz, @NonNull Optional<Class> interfaceClazz, @NonNull String bindingName, @NonNull boolean isDefault) {
//		if ( interfaceClazz.isPresent() ) {
//			logger.fine("Binding " + interfaceClazz.get().getName() + " to " + serviceClazz.getName());			
//			bind(interfaceClazz.get()).annotatedWith(Names.named(bindingName)).to(serviceClazz).in(Scopes.SINGLETON);			
//			if ( isDefault )
//				bind(interfaceClazz.get()).to(serviceClazz).in(Scopes.SINGLETON);	
//		}
//		else {
//			//custom classes cannot be annotated, as you have to get them by the classname anyways so there is no point (ie you can't have 2 of the same class)
//			logger.fine("Binding Custom Class " + serviceClazz.getName());		
//			bind(serviceClazz).in(Scopes.SINGLETON);
//		}
//	}

}
