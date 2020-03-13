package io.openshift.ocpp;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.googlecode.lanterna.gui2.dialogs.ListSelectDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.openshift.client.OpenShiftClient;

public class Pods extends AbstractResources {
   public static final Pods INSTANCE = new Pods();

   private static final String[] COLUMNS = new String[] { "NAME", "READY", "STATUS", "RESTARTS", "AGE", "IP", "NODE" };

   @Override
   public String[] getColumns() {
      return COLUMNS;
   }

   @Override
   public List<String[]> fetchRows(OpenShiftClient oc) {
      return oc.pods().inNamespace(oc.getConfiguration().getNamespace()).list().getItems().stream().map(pod -> new String[] {
            pod.getMetadata().getName(),
            pod.getStatus().getContainerStatuses().stream().filter(cs -> cs.getReady()).count() + "/" + pod.getStatus().getContainerStatuses().size(),
            describeStatus(pod.getStatus()),
            String.valueOf(pod.getStatus().getContainerStatuses().stream().mapToInt(cs -> cs.getRestartCount().intValue()).sum()),
            Util.getAge(pod.getStatus().getStartTime()),
            pod.getStatus().getPodIP(),
            pod.getSpec().getNodeName()
      }).sorted(Comparator.comparing(a -> a[0])).collect(Collectors.toList());
   }

   private String describeStatus(PodStatus status) {
      PodCondition scheduled = status.getConditions().stream().filter(c -> "PodScheduled".equals(c.getType())).findFirst().orElse(null);
      if (scheduled != null && !"True".equals(scheduled.getStatus())) {
         return "Pending";
      }
      PodCondition initialized = status.getConditions().stream().filter(c -> "Initialized".equals(c.getType())).findFirst().orElse(null);
      if (initialized == null) {
         return "Init:?";
      }
      if ("True".equals(initialized.getStatus())) {
         if ("PodCompleted".equals(initialized.getReason())) {
            return "Completed";
         }
         PodCondition ready = status.getConditions().stream().filter(c -> "Ready".equals(c.getType())).findFirst().orElse(null);
         // TODO
         if (ready == null) {
            return "NotReady:?";
         } else if ("True".equals(ready.getStatus())) {
            return "Running";
//         } else if ("Pending".equals(status.getPhase())) {
//            return "STARTING";
//         } else if ("Running".equals(status.getPhase())) {
//            return "TERMINATING";
         } else {
            return status.getContainerStatuses().stream().map(cs -> cs.getState())
                  .filter(s -> s.getTerminated() != null || s.getWaiting() != null)
                  .map(s -> s.getTerminated() != null ? s.getTerminated().getReason() : s.getWaiting().getReason())
                  .findFirst().orElse("NotReady");
         }
      } else {
         if ("ContainersNotInitialized".equals(initialized.getReason())) {
            return "Init:Error";
         }
      }
      return "Unknown";
   }

   @Override
   public String getResourceType(List<String> row) {
      return "pods";
   }

   @Override
   public Map<String, Operation> getOperations() {
      // intentionally omitting EDIT
      return Resources.ops().add("describe", DESCRIBE).add("yaml", SHOW_YAML).add("delete", DELETE)
            .add("logs", Pods::logs).add("rsh", Pods::rsh).add("top", Pods::top).build();
   }

   private static void top(Ocpp ocpp, List<String> row) {
      Pod pod = ocpp.oc.pods().inNamespace(ocpp.ns()).withName(row.get(0)).get();
      GuiUtil.ssh(ocpp, pod.getSpec().getNodeName(), "-t", "top");
   }

   private static void rsh(Ocpp ocpp, List<String> row) {
      String name = row.get(0);
      String container = selectContainer(ocpp, name);
      if (container == null) {
         return;
      }
      GuiUtil.resetTerminal();
      try {
         Process ssh = new ProcessBuilder(Ocpp.OC_BINARY, "rsh", "-n", ocpp.ns(), "-c", container, name).inheritIO().start();
         if (ssh.waitFor() != 0) {
            GuiUtil.refreshScreen(ocpp);
            MessageDialog.showMessageDialog(ocpp.gui, "RSH failed", "Failed to rsh to " + name + "/" + container, MessageDialogButton.OK);
         }
         GuiUtil.refreshScreen(ocpp);
      } catch (IOException | InterruptedException e) {
         GuiUtil.showException(ocpp, e);
      }
   }

   private static void logs(Ocpp ocpp, List<String> row) {
      String name = row.get(0);
      String container = selectContainer(ocpp, name);
      if (container != null) {
         GuiUtil.runAndView(ocpp, new String[]{ Ocpp.OC_BINARY, "logs", "-n", ocpp.ns(), "-c", container, name }, "logs-" + name, ".log");
      }
   }

   private static String selectContainer(Ocpp ocpp, String resourceName) {
      Pod pod = ocpp.oc.pods().inNamespace(ocpp.ns()).withName(resourceName).get();
      int numContainers = pod.getStatus().getContainerStatuses().size();
      String container;
      if (numContainers > 1) {
         container = ListSelectDialog.showDialog(ocpp.gui, "Select container", "Select container:",
               pod.getStatus().getContainerStatuses().stream().map(ContainerStatus::getName).toArray(String[]::new));
      } else {
         container = pod.getStatus().getContainerStatuses().stream().map(ContainerStatus::getName).findFirst().get();
      }
      return container;
   }


   @Override
   public NonNamespaceOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> getResources(Ocpp ocpp, List<String> row) {
      return ocpp.oc.pods().inNamespace(ocpp.ns());
   }
}
