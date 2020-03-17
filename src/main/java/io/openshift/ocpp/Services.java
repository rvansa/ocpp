package io.openshift.ocpp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ServiceResource;

public class Services extends AbstractResources {
   public static final Services INSTANCE = new Services();

   private static final String[] COLUMNS = new String[] { "NAME", "TYPE", "CLUSTER-IP", "EXTERNAL-IP", "PORT(S)", "AGE", "SELECTOR" };

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(Ocpp ocpp) {
      return ocpp.oc.services().inNamespace(ocpp.ns()).list().getItems().stream().map(svc -> new String[] {
            svc.getMetadata().getName(),
            svc.getSpec().getType(),
            svc.getSpec().getClusterIP(),
            Util.listToString(svc.getSpec().getExternalIPs()),
            Util.listToString(svc.getSpec().getPorts().stream().map(this::servicePort).collect(Collectors.toList())),
            Util.getAge(svc.getMetadata().getCreationTimestamp()),
            svc.getSpec().getSelector().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "))
      }).sorted(Comparator.comparing(a -> a[0])).collect(Collectors.toList());
   }

   private String servicePort(ServicePort sp) {
      StringBuilder sb = new StringBuilder();
      sb.append(sp.getName()).append("/").append(sp.getPort());
      if (sp.getNodePort() != null) {
         sb.append(':').append(sp.getNodePort());
      }
      if (sp.getTargetPort() != null) {
         if (sp.getTargetPort().getIntVal() != null) {
            if (!sp.getTargetPort().getIntVal().equals(sp.getPort())) {
               sb.append('>').append(sp.getTargetPort().getIntVal());
            }
         } else if (sp.getTargetPort().getStrVal() != null) {
            sb.append('>').append(sp.getTargetPort().getStrVal());
         }
      }
      return sb.append("/").append(sp.getProtocol()).toString();
   }

   @Override
   public String getResourceType(List<String> row) {
      return "services";
   }

   @Override
   public Map<String, Operation> getOperations() {
      return commonOps().build();
   }

   @Override
   public NonNamespaceOperation<Service, ServiceList, DoneableService, ServiceResource<Service, DoneableService>> getResources(Ocpp ocpp, List<String> row) {
      return ocpp.oc.services().inNamespace(ocpp.ns());
   }
}
