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
package com.ikanow.aleph2.distributed_services.utils;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.Time;

/**
 * Creates a local instance of a kafka broker to use for testing
 * 
 * @author Burch
 *
 */
public class MockKafkaBroker {
	public KafkaServer kafka_server;
	private static final Logger logger = LogManager.getLogger();
	private int broker_port = -1;
	
	/**
	 * Creates an instance of kafka on a random open port using the supplied
	 * zookeeper.
	 * 
	 * @param zookeeper_connection
	 * @throws IOException
	 */
	public MockKafkaBroker(String zookeeper_connection) throws IOException {
		for (int i = 0; i < 5; ++i) {
			try {
				setupServer(zookeeper_connection, getAvailablePort());
				break;
			}
			catch (Exception e) { // Possibly a race condition, sleep and try again
				try { Thread.sleep((long)(Math.random() * 1000)); } catch (Exception ee) {}
			}			
		}
	}
	
	/**
	 * Creates an instance of kafka on the given broker_port using the supplied
	 * zookeeper.
	 * 
	 * @param zookeeper_connection
	 * @param broker_port
	 * @throws IOException 
	 */
	public MockKafkaBroker(String zookeeper_connection, int broker_port) throws IOException {
		setupServer(zookeeper_connection, broker_port);
	}
	/**
	 * Creates an instance of kafka on the given broker_port using the supplied
	 * zookeeper.
	 * 
	 * @param zookeeper_connection
	 * @param broker_port
	 * @throws IOException 
	 */
	protected void setupServer(final String zookeeper_connection, final int broker_port) throws IOException {
		Properties props = new Properties();
		this.broker_port = broker_port;
		props.put("port", ""+broker_port);
		props.put("broker.id", "1");
		// System.getProperty("java.io.tmpdir") + File.pathSeparator + "kafka_local_temp_" + System.currentTimeMillis());
		props.put("log.dir", File.createTempFile("kafka_local_temp_", "").getAbsolutePath() + File.pathSeparator);
		logger.debug("MockKafkaBroker log dir is: " + props.getProperty("log.dir"));
		String zk = zookeeper_connection;
		logger.debug("ZOOKEEPER: " + zk);
		props.put("zookeeper.connect", zk);
		props.put("auto.create.topics.enable", "true");
		props.put("delete.topic.enable", "true");
		KafkaConfig config = new KafkaConfig(props);
		
		//NOTE: scala version won't work here for some reason, copied same implementation as {@link kafka.utils.SystemTime}
		kafka_server = new KafkaServer(config, new Time() {
			
			@Override
			public void sleep(long arg0) {
				try {
					Thread.sleep(arg0);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public long nanoseconds() {
				return System.nanoTime();
			}
			
			@Override
			public long milliseconds() {
				return System.currentTimeMillis();
			}
		});
		kafka_server.startup();
		logger.debug("local kafka is a go");
	}
	
	
	/**
	 * Shuts down the local kafka instance
	 * 
	 */
	public void stop() {
		kafka_server.shutdown();
		logger.debug("local kafka is a stop");
	}
	
	/**
	 * Returns the currently configured broker port
	 * 
	 * @return
	 */
	public int getBrokerPort() {
		return broker_port;
	}
	
	/**
	 * Finds an open local port and returns it
	 * 
	 * @return
	 * @throws IOException
	 */
	private static int getAvailablePort() throws IOException {
	    ServerSocket socket = new ServerSocket(0);
	    try {
	    	return socket.getLocalPort();
	    } finally {
	    	socket.close();
	    }
	}
}
