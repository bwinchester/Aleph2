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
package com.ikanow.aleph2.management_db.controllers.actors;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import scala.Tuple2;
import scala.concurrent.duration.Duration;
import akka.actor.Inbox;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IServiceContext;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.FutureUtils.ManagementFuture;
import com.ikanow.aleph2.data_model.utils.ModuleUtils;
import com.ikanow.aleph2.distributed_services.services.ICoreDistributedServices;
import com.ikanow.aleph2.distributed_services.services.MockCoreDistributedServices;
import com.ikanow.aleph2.management_db.data_model.BucketMgmtMessage.BucketDeletionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketMgmtMessage.BucketMgmtEventBusWrapper;
import com.ikanow.aleph2.management_db.services.DataBucketCrudService;
import com.ikanow.aleph2.management_db.services.ManagementDbActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class TestBucketDeletionActor {

	@Inject 
	protected IServiceContext _service_context = null;	
	
	protected ICoreDistributedServices _cds = null;
	protected IManagementDbService _core_mgmt_db = null;
	protected ManagementDbActorContext _actor_context = null;

	protected MockSearchIndexService _mock_index = null;
	
	@Before
	public void testSetup() throws Exception {
		
		if (null != _service_context) {
			return;
		}
		final String temp_dir = System.getProperty("java.io.tmpdir") + File.separator;
		
		// OK we're going to use guice, it was too painful doing this by hand...				
		Config config = ConfigFactory.parseReader(new InputStreamReader(this.getClass().getResourceAsStream("actor_test.properties")))
							.withValue("globals.local_root_dir", ConfigValueFactory.fromAnyRef(temp_dir))
							.withValue("globals.local_cached_jar_dir", ConfigValueFactory.fromAnyRef(temp_dir))
							.withValue("globals.distributed_root_dir", ConfigValueFactory.fromAnyRef(temp_dir))
							.withValue("globals.local_yarn_config_dir", ConfigValueFactory.fromAnyRef(temp_dir));
		
		Injector app_injector = ModuleUtils.createInjector(Arrays.asList(), Optional.of(config));	
		app_injector.injectMembers(this);
		
		_cds = _service_context.getService(ICoreDistributedServices.class, Optional.empty()).get();
		MockCoreDistributedServices mcds = (MockCoreDistributedServices) _cds;
		mcds.setApplicationName("DataImportManager");
		
		new ManagementDbActorContext(_service_context);		
		_actor_context = ManagementDbActorContext.get();
		
		_mock_index = (MockSearchIndexService) _service_context.getSearchIndexService().get();
		
		_core_mgmt_db = _service_context.getCoreManagementDbService();		
	}	
		
	public static class TestBean {};
	
	public DataBucketBean createBucketInfrastructure(final String path, boolean create_file_path) throws Exception {
		System.out.println("CREATING BUCKET: " + path);
		
		// delete the existing path if present:
		try {
			FileUtils.deleteDirectory(new File(System.getProperty("java.io.tmpdir") + File.separator + path));
		}
		catch (Exception e) {} // (fine, dir prob dones't delete)
		
		final DataBucketBean bucket = BeanTemplateUtils.build(DataBucketBean.class).with("full_name", path).done().get();
		
		// Then create it:
		
		if (create_file_path) {
			DataBucketCrudService.createFilePaths(bucket, _service_context.getStorageService());
			final String bucket_path = System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name();
			assertTrue("The file path has been created", new File(bucket_path + "/managed_bucket").exists());
			FileUtils.writeStringToFile(new File(bucket_path + IStorageService.STORED_DATA_SUFFIX + "/test"), "");
			assertTrue("The extra file path has been created", new File(bucket_path + IStorageService.STORED_DATA_SUFFIX + "/test").exists());
		}
		
		// Also create a state directory object so we can check that gets deleted
		ICrudService<?> top_level_state_directory = _core_mgmt_db.getStateDirectory(Optional.empty(), Optional.empty());
		top_level_state_directory.deleteDatastore().get();
		assertEquals(0, top_level_state_directory.countObjects().get().intValue());
		ICrudService<TestBean> harvest_state = _core_mgmt_db.getBucketHarvestState(TestBean.class, bucket, Optional.of("test1"));
		harvest_state.deleteObjectsBySpec(CrudUtils.allOf(TestBean.class)).get();
		assertEquals(0, harvest_state.countObjects().get().intValue());
		ICrudService<TestBean> enrich_state = _core_mgmt_db.getBucketEnrichmentState(TestBean.class, bucket, Optional.of("test2"));
		enrich_state.deleteObjectsBySpec(CrudUtils.allOf(TestBean.class)).get();
		assertEquals(0, enrich_state.countObjects().get().intValue());
		ICrudService<TestBean> analytics_state = _core_mgmt_db.getBucketAnalyticThreadState(TestBean.class, bucket, Optional.of("test3"));
		analytics_state.deleteObjectsBySpec(CrudUtils.allOf(TestBean.class)).get();
		assertEquals(0, analytics_state.countObjects().get().intValue());
		assertEquals(3, top_level_state_directory.countObjects().get().intValue());
		harvest_state.storeObject(new TestBean()).get();
		enrich_state.storeObject(new TestBean()).get();
		analytics_state.storeObject(new TestBean()).get();
		assertEquals(1, harvest_state.countObjects().get().intValue());
		assertEquals(1, enrich_state.countObjects().get().intValue());
		assertEquals(1, analytics_state.countObjects().get().intValue());
		
		return bucket;
	}

	@Test
	public void test_bucketDeletionActor_fullDelete() throws Exception {
		
		final DataBucketBean bucket = createBucketInfrastructure("/test/full/delete", true);
		
		final BucketDeletionMessage msg = new BucketDeletionMessage(bucket, new Date(), false);
		
		final Inbox inbox = Inbox.create(_actor_context.getActorSystem());		
		
		_actor_context.getDeletionMgmtBus().publish(new BucketMgmtEventBusWrapper(inbox.getRef(), msg));
		
		try {
			final BucketDeletionMessage reply_msg = (BucketDeletionMessage) inbox.receive(Duration.create(4L, TimeUnit.SECONDS));
			assertEquals(reply_msg._id(), "/test/full/delete");
			
			// check file system deleted:
			assertFalse("The file path has been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + "/managed_bucket").exists());
			
			// check mock index deleted:
			assertEquals(1, _mock_index._handleBucketDeletionRequests.size());
			final Collection<Tuple2<String, Object>> deletions = _mock_index._handleBucketDeletionRequests.get("handleBucketDeletionRequest");
			assertEquals(1, deletions.size());
			assertEquals("/test/full/delete", deletions.iterator().next()._1());
			assertEquals(true, deletions.iterator().next()._2());
			_mock_index._handleBucketDeletionRequests.clear();
			
			// check state directory cleansed:
			checkStateDirectoriesCleaned(bucket);
		}
		catch (Exception e) {
			fail("Timed out waiting for reply from BucketDeletionActor (or incorrect error): " + e.getMessage());
		}
	}

	@Test
	public void test_bucketDeletionActor_fullDelete_notExist() throws Exception {		
		final DataBucketBean bucket = createBucketInfrastructure("/test/full/delete/missing", false);
		assertFalse("The file path next existed", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + "/managed_bucket").exists());

		final BucketDeletionMessage msg = new BucketDeletionMessage(bucket, new Date(), false);
		
		final Inbox inbox = Inbox.create(_actor_context.getActorSystem());
		_actor_context.getDeletionMgmtBus().publish(new BucketMgmtEventBusWrapper(inbox.getRef(), msg));
		
		try {
			final BucketDeletionMessage reply_msg = (BucketDeletionMessage) inbox.receive(Duration.create(4L, TimeUnit.SECONDS));
			assertEquals(reply_msg._id(), "/test/full/delete/missing");
			
			// check state directory *NOT* cleansed in this case:
			checkStateDirectoriesNotCleaned(bucket);
		}		
		catch (Exception e) {
			fail("Timed out waiting for reply from BucketDeletionActor (or incorrect error): " + e.getMessage());
		}
	}	
	
	@Test
	public void test_bucketDeletionActor_fullDelete_beanStillPresent() throws Exception {
		final DataBucketBean bucket = createBucketInfrastructure("/test/full/delete/fail_bean_present", true);
		
		//final IManagementDbService underlying_mgmt_db = _core_mgmt_db.getUnderlyingPlatformDriver(IManagementDbService.class, Optional.empty()).get();
				//_service_context.getService(IManagementDbService.class, Optional.empty()).get();
		
		@SuppressWarnings("unchecked")
		final ICrudService<DataBucketBean> underlying_crud = (
				ICrudService<DataBucketBean>) this._core_mgmt_db.getDataBucketStore().getUnderlyingPlatformDriver(ICrudService.class, Optional.empty()).get();
		
		underlying_crud.deleteDatastore().get();
		assertEquals(0, underlying_crud.countObjects().get().intValue());
		underlying_crud.storeObject(bucket).get();
		assertEquals(1, underlying_crud.countObjects().get().intValue());
		
		final BucketDeletionMessage msg = new BucketDeletionMessage(bucket, new Date(), false);
		
		final Inbox inbox = Inbox.create(_actor_context.getActorSystem());		
		
		_actor_context.getDeletionMgmtBus().publish(new BucketMgmtEventBusWrapper(inbox.getRef(), msg));
		
		try {
			while (true) {
				final BucketDeletionMessage reply_msg = (BucketDeletionMessage) inbox.receive(Duration.create(4L, TimeUnit.SECONDS));
				if (!String.class.isAssignableFrom(reply_msg.getClass())) { // (Else some dead letter)
					fail("Should have timed out because bucket deletion should have failed: " + reply_msg.getClass());
				}
			}
		}
		catch (Exception e) {
			// worked - check stuff still around: 
			assertEquals(0, underlying_crud.countObjects().get().intValue());

			// check was added to the deletion queue:
			assertEquals(1, _core_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get().intValue());
			
			// check file system not deleted:
			assertTrue("The file path has *not* been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + "/managed_bucket").exists());
			
			// check mock index not deleted:
			assertEquals(0, _mock_index._handleBucketDeletionRequests.size());			
			
			// check state directory *NOT* cleansed in this case:
			checkStateDirectoriesNotCleaned(bucket);
		}
		_core_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).deleteDatastore().get();
		assertEquals(0, _core_mgmt_db.getDataBucketStore().countObjects().get().intValue());
	}
	
	@Test
	public void test_bucketDeletionActor_purge_immediate() throws Exception {
		final DataBucketBean bucket = createBucketInfrastructure("/test/purge/immediate", true);
		
		final IManagementDbService underlying_mgmt_db = _service_context.getService(IManagementDbService.class, Optional.empty()).get();
		underlying_mgmt_db.getDataBucketStore().deleteDatastore().get();
		assertEquals(0, underlying_mgmt_db.getDataBucketStore().countObjects().get().intValue());
		underlying_mgmt_db.getDataBucketStore().storeObject(bucket).get();
		assertEquals(1, underlying_mgmt_db.getDataBucketStore().countObjects().get().intValue());
		
		final ManagementFuture<Boolean> res = _core_mgmt_db.purgeBucket(bucket, Optional.empty());
		
		// check result
		assertTrue("Purge called succeeded", res.get());
		assertEquals(2, res.getManagementResults().get().size());
		
		//check system state afterwards
		
		// Full filesystem exists
		assertTrue("The file path has *not* been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + "/managed_bucket").exists());
		
		// Data directories no longer exist
		assertFalse("The data path has been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + IStorageService.STORED_DATA_SUFFIX + "/test").exists());
		
		// check state directory _not_ cleaned in this case (the harvester can always do this once that's been wired up):
		checkStateDirectoriesNotCleaned(bucket);

		// check mock index deleted:
		assertEquals(1, _mock_index._handleBucketDeletionRequests.size());
		final Collection<Tuple2<String, Object>> deletions = _mock_index._handleBucketDeletionRequests.get("handleBucketDeletionRequest");
		assertEquals(1, deletions.size());
		assertEquals("/test/purge/immediate", deletions.iterator().next()._1());
		assertEquals(false, deletions.iterator().next()._2());
		_mock_index._handleBucketDeletionRequests.clear();
	}

	@Test
	public void test_bucketDeletionActor_purge_delayed() throws Exception {
		final DataBucketBean bucket = createBucketInfrastructure("/test/purge/delayed", true);
		
		final IManagementDbService underlying_mgmt_db = _service_context.getService(IManagementDbService.class, Optional.empty()).get();
		underlying_mgmt_db.getDataBucketStore().storeObject(bucket).get();
		assertEquals(1, underlying_mgmt_db.getDataBucketStore().countObjects().get().intValue());
		
		underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).deleteDatastore().get();
		assertEquals(0, underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get().intValue());
		
		// (in practice means will have to wait up to 10 seconds...)
		final ManagementFuture<Boolean> res = _core_mgmt_db.purgeBucket(bucket, Optional.of(java.time.Duration.ofSeconds(1)));
		
		// check result
		assertTrue("Purge called succeeded", res.get());
		assertEquals(0, res.getManagementResults().get().size());
		
		// Wait for it to complete:
		for (int i = 0; i < 5; ++i) {
			Thread.sleep(1000L);
			if (underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get() > 0) break;
		}
		assertEquals(1, underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get().intValue());
		for (int i = 0; i < 20; ++i) {
			Thread.sleep(1000L);
			if (underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get() == 0) break;
		}
		assertEquals(0, underlying_mgmt_db.getBucketDeletionQueue(BucketDeletionMessage.class).countObjects().get().intValue());
		
		//check system state afterwards
		
		// Full filesystem exists
		assertTrue("The file path has *not* been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + "/managed_bucket").exists());
		
		// Data directories no longer exist
		assertFalse("The data path has been deleted", new File(System.getProperty("java.io.tmpdir") + File.separator + bucket.full_name() + IStorageService.STORED_DATA_SUFFIX + "/test").exists());
		
		// check state directory _not_ cleaned in this case (the harvester can always do this once that's been wired up):
		checkStateDirectoriesNotCleaned(bucket);

		// check mock index deleted:
		assertEquals(1, _mock_index._handleBucketDeletionRequests.size());
		final Collection<Tuple2<String, Object>> deletions = _mock_index._handleBucketDeletionRequests.get("handleBucketDeletionRequest");
		assertEquals(1, deletions.size());
		assertEquals("/test/purge/delayed", deletions.iterator().next()._1());
		assertEquals(false, deletions.iterator().next()._2());
		_mock_index._handleBucketDeletionRequests.clear();
	}
	
	@After
	public void cleanupTest() {
		_actor_context.onTestComplete();
	}
	
	protected void checkStateDirectoriesCleaned(DataBucketBean bucket) throws InterruptedException, ExecutionException {
		ICrudService<?> top_level_state_directory = _core_mgmt_db.getStateDirectory(Optional.empty(), Optional.empty());
		assertEquals(0, top_level_state_directory.countObjects().get().intValue());
		ICrudService<TestBean> harvest_state = _core_mgmt_db.getBucketHarvestState(TestBean.class, bucket, Optional.of("test1"));
		assertEquals(0, harvest_state.countObjects().get().intValue());
		ICrudService<TestBean> enrich_state = _core_mgmt_db.getBucketEnrichmentState(TestBean.class, bucket, Optional.of("test2"));
		assertEquals(0, enrich_state.countObjects().get().intValue());
		ICrudService<TestBean> analytics_state = _core_mgmt_db.getBucketAnalyticThreadState(TestBean.class, bucket, Optional.of("test3"));
		assertEquals(0, analytics_state.countObjects().get().intValue());		
	}
	protected void checkStateDirectoriesNotCleaned(DataBucketBean bucket) throws InterruptedException, ExecutionException {
		ICrudService<?> top_level_state_directory = _core_mgmt_db.getStateDirectory(Optional.empty(), Optional.empty());
		assertEquals(3, top_level_state_directory.countObjects().get().intValue());
		ICrudService<TestBean> harvest_state = _core_mgmt_db.getBucketHarvestState(TestBean.class, bucket, Optional.of("test1"));
		assertEquals(1, harvest_state.countObjects().get().intValue());
		ICrudService<TestBean> enrich_state = _core_mgmt_db.getBucketEnrichmentState(TestBean.class, bucket, Optional.of("test2"));
		assertEquals(1, enrich_state.countObjects().get().intValue());
		ICrudService<TestBean> analytics_state = _core_mgmt_db.getBucketAnalyticThreadState(TestBean.class, bucket, Optional.of("test3"));
		assertEquals(1, analytics_state.countObjects().get().intValue());		
	}
}