package com.linkedin.venice.endToEnd;

import com.linkedin.venice.controller.Admin;
import com.linkedin.venice.controllerapi.ControllerClient;
import com.linkedin.venice.controllerapi.UpdateStoreQueryParams;
import com.linkedin.venice.integration.utils.ServiceFactory;
import com.linkedin.venice.integration.utils.VeniceControllerWrapper;
import com.linkedin.venice.integration.utils.VeniceMultiClusterWrapper;
import com.linkedin.venice.integration.utils.VeniceTwoLayerMultiColoMultiClusterWrapper;
import com.linkedin.venice.utils.TestUtils;
import com.linkedin.venice.utils.Time;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;


public class TestWritePathComputation {
  private static final Logger logger = Logger.getLogger(TestWritePathComputation.class);
  private static final long GET_MASTER_CONTROLLER_TIMEOUT = 20 * Time.MS_PER_SECOND;

  @Test
  public void testFeatureFlagSingleDC() {
    try (VeniceMultiClusterWrapper multiClusterWrapper = ServiceFactory.getVeniceMultiClusterWrapper(1, 1, 1, 1)) {
      String clusterName = multiClusterWrapper.getClusterNames()[0];
      String storeName = "test-store0";

      // Create store
      Admin admin = multiClusterWrapper.getMasterController(clusterName,GET_MASTER_CONTROLLER_TIMEOUT).getVeniceAdmin();
      admin.addStore(clusterName, storeName, "tester", "\"string\"", "\"string\"");
      Assert.assertTrue(admin.hasStore(clusterName, storeName));
      Assert.assertFalse(admin.getStore(clusterName, storeName).isWriteComputationEnabled());

      // Set flag
      String controllerUrl = multiClusterWrapper.getMasterController(clusterName, GET_MASTER_CONTROLLER_TIMEOUT).getControllerUrl();
      ControllerClient controllerClient = new ControllerClient(clusterName, controllerUrl);
      controllerClient.updateStore(storeName, new UpdateStoreQueryParams().setWriteComputationEnabled(true));
      Assert.assertTrue(admin.getStore(clusterName, storeName).isWriteComputationEnabled());

      // Reset flag
      controllerClient.updateStore(storeName, new UpdateStoreQueryParams().setWriteComputationEnabled(false));
      Assert.assertFalse(admin.getStore(clusterName, storeName).isWriteComputationEnabled());
    }
  }

  @Test
  public void testFeatureFlagMultipleDC() {
    try (VeniceTwoLayerMultiColoMultiClusterWrapper twoLayerMultiColoMultiClusterWrapper = ServiceFactory.getVeniceTwoLayerMultiColoMultiClusterWrapper(
        1, 1, 1, 1, 1, 1)) {

      VeniceMultiClusterWrapper multiCluster = twoLayerMultiColoMultiClusterWrapper.getClusters().get(0);
      VeniceControllerWrapper parentController = twoLayerMultiColoMultiClusterWrapper.getParentControllers().get(0);
      String clusterName = multiCluster.getClusterNames()[0];
      String storeName = "test-store0";

      // Create store
      Admin parentAdmin = twoLayerMultiColoMultiClusterWrapper.getMasterController(clusterName).getVeniceAdmin();
      Admin childAdmin = multiCluster.getMasterController(clusterName, GET_MASTER_CONTROLLER_TIMEOUT).getVeniceAdmin();
      parentAdmin.addStore(clusterName, storeName, "tester", "\"string\"", "\"string\"");
      TestUtils.waitForNonDeterministicAssertion(15, TimeUnit.SECONDS, true, () -> {
        Assert.assertTrue(parentAdmin.hasStore(clusterName, storeName));
        Assert.assertTrue(childAdmin.hasStore(clusterName, storeName));
        Assert.assertFalse(parentAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
        Assert.assertFalse(childAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
      });

      // Set flag
      String parentControllerUrl = parentController.getControllerUrl();
      ControllerClient parentControllerClient = new ControllerClient(clusterName, parentControllerUrl);
      parentControllerClient.updateStore(storeName, new UpdateStoreQueryParams().setWriteComputationEnabled(true));
      TestUtils.waitForNonDeterministicAssertion(15, TimeUnit.SECONDS, true, () -> {
        Assert.assertTrue(parentAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
        Assert.assertTrue(childAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
      });

      // Reset flag
      parentControllerClient.updateStore(storeName, new UpdateStoreQueryParams().setWriteComputationEnabled(false));
      TestUtils.waitForNonDeterministicAssertion(15, TimeUnit.SECONDS, true, () -> {
        Assert.assertFalse(parentAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
        Assert.assertFalse(childAdmin.getStore(clusterName, storeName).isWriteComputationEnabled());
      });
    }
  }
}
