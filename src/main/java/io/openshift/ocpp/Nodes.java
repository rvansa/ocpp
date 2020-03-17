package io.openshift.ocpp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class Nodes extends AbstractResources {
   public static final Nodes INSTANCE = new Nodes();

   private static final String[] COLUMNS = new String[] { "NAME", "STATUS", "ROLES", "AGE", "INTERNAL-IP", "PODS", "CPU-REQS"};

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(Ocpp ocpp) {
      Map<String, List<Pod>> allPods = ocpp.oc.pods().inAnyNamespace().list().getItems().stream()
            .filter(pod -> pod.getSpec().getNodeName() != null)
            .collect(Collectors.groupingBy(pod -> pod.getSpec().getNodeName()));
      return ocpp.oc.nodes().list().getItems().stream().map(n -> new String[] {
            n.getMetadata().getName(),
            n.getStatus().getConditions().stream()
                  .filter(nc -> "Ready".equals(nc.getType()))
                  .map(nc -> "True".equals(nc.getStatus()) ? "READY" : "NOT_READY").findAny().orElse("UNKNOWN"),
            n.getMetadata().getLabels().keySet().stream()
                  .filter(l -> l.startsWith("node-role.kubernetes.io"))
                  .map(l -> l.substring(l.indexOf('/') + 1)).findFirst().orElse(""),
            Util.getAge(n.getMetadata().getCreationTimestamp()),
            n.getStatus().getAddresses().stream()
                  .filter(a -> "InternalIP".equals(a.getType()))
                  .map(NodeAddress::getAddress).findAny().orElse(""),
            (allPods.containsKey(n.getMetadata().getName()) ? allPods.get(n.getMetadata().getName()).size() : 0) + "/" + n.getStatus().getCapacity().get("pods").getAmount(),
            (allPods.containsKey(n.getMetadata().getName()) ? allPods.get(n.getMetadata().getName()).stream()
                  .flatMap(pod -> pod.getSpec().getContainers().stream())
                  .map(Container::getResources).filter(Objects::nonNull)
                  .map(ResourceRequirements::getRequests).filter(Objects::nonNull)
                  .map(reqs -> reqs.get("cpu")).filter(Objects::nonNull)
                  .mapToInt(Util::millicores).sum() : 0) + "m/" + n.getStatus().getCapacity().get("cpu").getAmount()
      }).collect(Collectors.toList());
   }

   @Override
   public String getResourceType(List<String> row) {
      return "nodes";
   }

   @Override
   public Map<String, Operation> getOperations(List<String> unused) {
      return commonOps().add("ssh", (ocpp, row) -> {
         String name = row.get(0);
         Node node = ocpp.oc.nodes().withName(name).get();
         String hostname = node.getStatus().getAddresses().stream()
               .filter(a -> "Hostname".equals(a.getType()))
               .map(NodeAddress::getAddress).findAny().orElse(null);
         if (hostname == null) {
            hostname = node.getMetadata().getName();
         }
         GuiUtil.ssh(ocpp, hostname);
      }).build();
   }

   @Override
   public NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> getResources(Ocpp ocpp, List<String> row) {
      return ocpp.oc.nodes();
   }
}
