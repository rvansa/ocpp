package io.openshift.ocpp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.DoneableReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class ReplicationControllers extends AbstractResources {
   private static final String[] COLUMNS = { "NAME", "DESIRED", "CURRENT", "READY", "AGE" };

   private final String deployment;

   public ReplicationControllers(String deployment) {
      this.deployment = deployment;
   }

   @Override
   String getResourceType(List<String> row) {
      return "replicationcontroller";
   }

   @Override
   NonNamespaceOperation<ReplicationController, ReplicationControllerList, DoneableReplicationController,
         RollableScalableResource<ReplicationController, DoneableReplicationController>> getResources(Ocpp ocpp, List<String> row) {
      return (NonNamespaceOperation<ReplicationController, ReplicationControllerList, DoneableReplicationController, RollableScalableResource<ReplicationController, DoneableReplicationController>>)
            ocpp.oc.replicationControllers().inNamespace(ocpp.ns()).withLabel("openshift.io/deployment-config.name", deployment);
   }

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(Ocpp ocpp) {
      return getResources(ocpp, null).list().getItems().stream().map(rc -> new String[] {
                  rc.getMetadata().getName(),
                  String.valueOf(rc.getStatus().getReplicas()),
                  String.valueOf(rc.getStatus().getAvailableReplicas()),
                  String.valueOf(rc.getStatus().getReadyReplicas()),
                  Util.getAge(rc.getMetadata().getCreationTimestamp())
            }).collect(Collectors.toList());
   }

   @Override
   public Map<String, Operation> getOperations() {
      return Resources.ops().add("describe", DESCRIBE).add("yaml", SHOW_YAML).add("delete", DELETE).build();
   }
}
