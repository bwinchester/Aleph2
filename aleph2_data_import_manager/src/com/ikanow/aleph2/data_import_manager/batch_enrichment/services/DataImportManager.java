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
package com.ikanow.aleph2.data_import_manager.batch_enrichment.services;

import org.apache.log4j.Logger;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;

import com.google.inject.Inject;
import com.ikanow.aleph2.data_import_manager.batch_enrichment.actors.FolderWatcherActor;
import com.ikanow.aleph2.data_import_manager.services.DataImportActorContext;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;

public class DataImportManager  {
	private static final Logger logger = Logger.getLogger(DataImportManager.class);
    private ActorSystem system = null;
    protected ActorRef folderWatchActor = null;
    

	protected final DataImportActorContext _context;
	protected IStorageService _storage_service;
	
	protected ActorRef getFolderWatchActor(){
		if(folderWatchActor==null){
		system = _context.getActorSystem();
		
		Props props = Props.create(FolderWatcherActor.class,_storage_service);
		folderWatchActor = system.actorOf(props,"folderWatch");
		}
		return folderWatchActor;
		
	}
    @Inject
    public DataImportManager(DataImportActorContext context, IStorageService storageService) {
    	this._context = context;
    	this._storage_service = storageService;
    }
    
	public void start() {
        // Create the 'greeter' actor
		logger.info("DataImportManager starting...");
		getFolderWatchActor().tell("start", ActorRef.noSender());
	}

	public void stop() {
		logger.info("DataImportManager stopping...");
		getFolderWatchActor().tell("stop", ActorRef.noSender());
	}

	public void tick() {
		logger.info("DataImportManager sending tick.");
		getFolderWatchActor().tell("tick", ActorRef.noSender());
	}
}