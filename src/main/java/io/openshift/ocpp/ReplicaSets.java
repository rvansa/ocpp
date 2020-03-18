package io.openshift.ocpp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.apps.DoneableReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class ReplicaSets extends AbstractResources {
   private static final String[] COLUMNS = { "NAME", "DESIRED", "CURRENT", "READY", "AGE" };

   private final String deployment;

   public ReplicaSets(String deployment) {
      this.deployment = deployment;
   }

   @Override
   public String getResourceType(List<String> row) {
      return "replicaset";
   }

   @Override
   NonNamespaceOperation<ReplicaSet, ReplicaSetList, DoneableReplicaSet,
         RollableScalableResource<ReplicaSet, DoneableReplicaSet>> getResources(Ocpp ocpp, List<String> row) {
      return (NonNamespaceOperation<ReplicaSet, ReplicaSetList, DoneableReplicaSet, RollableScalableResource<ReplicaSet, DoneableReplicaSet>>)
            ocpp.oc.apps().replicaSets().inNamespace(ocpp.ns()).withLabel("openshift.io/deployment-config.name", deployment);
   }

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(Ocpp ocpp) {
      return getResources(ocpp, null).list().getItems().stream().map(rs -> new String[] {
                  rs.getMetadata().getName(),
                  String.valueOf(rs.getStatus().getReplicas()),
                  String.valueOf(rs.getStatus().getAvailableReplicas()),
                  String.valueOf(rs.getStatus().getReadyReplicas()),
                  Util.getAge(rs.getMetadata().getCreationTimestamp())
            }).collect(Collectors.toList());
   }

   @Override
   public Map<String, Operation> getOperations(List<String> row) {
      return Resources.ops().add("describe", DESCRIBE).add("yaml", SHOW_YAML).add("delete", DELETE).build();
   }
}
