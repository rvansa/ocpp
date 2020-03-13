package io.openshift.ocpp;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.client.OpenShiftClient;

public class ConfigMaps extends AbstractResources {
   public static final ConfigMaps INSTANCE = new ConfigMaps();

   private static final String[] COLUMNS = new String[] { "NAME", "DATA", "AGE" };

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(OpenShiftClient oc) {
      return oc.configMaps().inNamespace(oc.getConfiguration().getNamespace()).list().getItems().stream().map(cm -> new String[] {
         cm.getMetadata().getName(),
         describeData(cm.getData()),
         Util.getAge(cm.getMetadata().getCreationTimestamp())
      }).collect(Collectors.toList());
   }

   private String describeData(Map<String, String> data) {
      StringBuilder sb = new StringBuilder();
      sb.append(data.size()).append(": ");
      boolean first = true;
      for (String key : data.keySet()) {
         if (sb.length() + 2 + key.length() >= 32) {
            sb.append("...");
            break;
         }
         if (!first) {
            sb.append(", ");
         }
         sb.append(key);
         first = false;
      }
      return sb.toString();
   }

   @Override
   public String getResourceType(List<String> row) {
      return "configmaps";
   }

   @Override
   public Map<String, Operation> getOperations() {
      return commonOps().build();
   }

   @Override
   public NonNamespaceOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> getResources(Ocpp ocpp, List<String> row) {
      return ocpp.oc.configMaps().inNamespace(ocpp.ns());
   }
}
