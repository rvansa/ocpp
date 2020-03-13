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
import io.fabric8.openshift.client.OpenShiftClient;

public class Deployments extends AbstractResources {
   public static final Deployments INSTANCE = new Deployments();

   private static final String[] COLUMNS = new String[] { "NAME", "TYPE", "READY", "UP-TO-DATE", "AVAILABLE", "AGE" };

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(OpenShiftClient oc) {
      return Stream.concat(oc.apps().deployments().inNamespace(oc.getConfiguration().getNamespace()).list().getItems().stream().map(d -> new String[] {
            d.getMetadata().getName(),
            "deployment",
            Util.toString(d.getStatus().getReadyReplicas()) + "/" + Util.toString(d.getStatus().getReplicas()),
            Util.toString(d.getStatus().getUpdatedReplicas()),
            Util.toString(d.getStatus().getAvailableReplicas()),
            Util.getAge(d.getMetadata().getCreationTimestamp())
      }), oc.deploymentConfigs().inNamespace(oc.getConfiguration().getNamespace()).list().getItems().stream().map(dc -> new String[] {
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
   public Map<String, Operation> getOperations() {
      return commonOps().add("rescale", (ocpp, row) -> {
         String name = row.get(0);
         BigInteger newScale = TextInputDialog.showNumberDialog(ocpp.gui, "Rescale", "Set new #replicas for " + name, "");
         switch (row.get(1)) {
            case "deployment":
               ocpp.oc.apps().deployments().inNamespace(ocpp.ns()).withName(name).scale(newScale.intValue());
               break;
            case "dc":
               ocpp.oc.deploymentConfigs().inNamespace(ocpp.ns()).withName(name).scale(newScale.intValue());
               break;
            default:
               MessageDialog.showMessageDialog(ocpp.gui, "Cannot scale", "Cannot scale unknown deployment type: " + row.get(1), MessageDialogButton.OK);
         }
      }).build();
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
