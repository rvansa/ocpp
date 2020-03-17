package io.openshift.ocpp;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.dialogs.WaitingDialog;

import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class Deployments extends AbstractResources {
   public static final Deployments INSTANCE = new Deployments();

   private static final String[] COLUMNS = new String[] { "NAME", "TYPE", "READY", "UP-TO-DATE", "AVAILABLE", "AGE" };

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(Ocpp ocpp) {
      return Stream.concat(ocpp.oc.apps().deployments().inNamespace(ocpp.ns()).list().getItems().stream().map(d -> new String[] {
            d.getMetadata().getName(),
            "deployment",
            Util.toString(d.getStatus().getReadyReplicas()) + "/" + Util.toString(d.getStatus().getReplicas()),
            Util.toString(d.getStatus().getUpdatedReplicas()),
            Util.toString(d.getStatus().getAvailableReplicas()),
            Util.getAge(d.getMetadata().getCreationTimestamp())
      }), ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns()).list().getItems().stream().map(dc -> new String[] {
            dc.getMetadata().getName(),
            "dc",
            Util.toString(dc.getStatus().getReadyReplicas()) + "/" + Util.toString(dc.getStatus().getReplicas()),
            Util.toString(dc.getStatus().getUpdatedReplicas()),
            Util.toString(dc.getStatus().getAvailableReplicas()),
            Util.getAge(dc.getMetadata().getCreationTimestamp())
      })).collect(Collectors.toList());
   }

   @Override
   public String getResourceType(List<String> row) {
      return row.get(1);
   }

   @Override
   public Map<String, Operation> getOperations(List<String> row) {
      switch (row.get(1)) {
         case "deployment":
            return commonOps()
                  .add("rescale", this::rescaleDeployment)
                  .add("show replicasets", (ocpp, row2) -> ocpp.switchResources(new ReplicaSets(row2.get(0))))
                  .build();
         case "dc":
            return commonOps()
                  .add("rescale", this::rescaleDeploymentConfig)
                  .add("show repl.controllers", (ocpp, row2) -> ocpp.switchResources(new ReplicationControllers(row2.get(0))))
                  .add("rollout latest", (ocpp, row2) -> ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns()).withName(row2.get(0)).deployLatest())
                  .build();
         default:
            return Collections.emptyMap();
      }
   }

   private void rescaleDeployment(Ocpp ocpp, List<String> row) {
      String name = row.get(0);
      BigInteger newScale = TextInputDialog.showNumberDialog(ocpp.gui, "Rescale", "Set new #replicas for " + name, "");
      if (newScale != null) {
         ocpp.oc.apps().deployments().inNamespace(ocpp.ns()).withName(name).scale(newScale.intValue());
      }
   }

   private void rescaleDeploymentConfig(Ocpp ocpp, List<String> row) {
      String name = row.get(0);
      BigInteger newScale = TextInputDialog.showNumberDialog(ocpp.gui, "Rescale", "Set new #replicas for " + name, "");
      if (newScale != null) {
         ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns()).withName(name).scale(newScale.intValue());
      }
   }

   @Override
   public NonNamespaceOperation<?, ?, ?, ? extends Resource<?, ?>> getResources(Ocpp ocpp, List<String> row) {
      switch (row.get(1)) {
         case "deployment":
            return ocpp.oc.apps().deployments().inNamespace(ocpp.ns());
         case "dc":
            return ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns());
         default:
            return null;
      }
   }

   @Override
   public void deleteAll(Ocpp ocpp) {
      if (!confirmDeleteAll(ocpp)) {
         return;
      }
      WaitingDialog waitingDialog = WaitingDialog.showDialog(ocpp.gui, "Please wait", "Deleting...");
      ocpp.executor.submit(() -> {
         try {
            ocpp.oc.apps().deployments().inNamespace(ocpp.ns()).delete();
            ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns()).delete();
         } finally {
            waitingDialog.close();
         }
      });
   }
}
