package io.openshift.ocpp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.WaitingDialog;

import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.internal.SerializationUtils;

public abstract class AbstractResources implements Resources {
   final Operation DESCRIBE = this::describe;
   final Operation EDIT = this::edit;
   final Operation DELETE = this::delete;
   final Operation SHOW_YAML = this::showYaml;

   @Override
   public void describe(Ocpp ocpp, List<String> row) {
      String resourceName = row.get(0);
      String[] command = { Ocpp.OC_BINARY, "describe", "-n", ocpp.ns(), getResourceType(row), resourceName };
      GuiUtil.runAndView(ocpp, command, getResourceType(row) + "-" + resourceName, ".txt");
   }

   @Override
   public void showYaml(Ocpp ocpp, List<String> row) {
      String resourceName = row.get(0);
      String[] command = { Ocpp.OC_BINARY, "get", "-o", "yaml", "-n", ocpp.ns(), getResourceType(row), resourceName };
      GuiUtil.runAndView(ocpp, command, getResourceType(row) + "-" + resourceName, ".yaml");
   }

   @Override
   public void edit(Ocpp ocpp, List<String> row) {
      try {
         runOc(ocpp, "edit", getResourceType(row), row.get(0));
      } catch (IOException e) {
         GuiUtil.showException(ocpp, e);
      }
   }

   private void runOc(Ocpp ocpp, String command, String resourceType, String name) throws IOException {
      Process process = new ProcessBuilder(Ocpp.OC_BINARY, command, "-n", ocpp.ns(), resourceType, name)
            .inheritIO().start();
      try {
         process.waitFor();
      } catch (InterruptedException e) {
         GuiUtil.showException(ocpp, e);
      }
      GuiUtil.refreshScreen(ocpp);
   }

   @Override
   public void delete(Ocpp ocpp, List<String> row) {
      String resourceName = row.get(0);
      String resource = getResourceType(row) + "/" + resourceName;
      MessageDialogButton result = MessageDialog.showMessageDialog(ocpp.gui,
            "Confirm delete", "Do you really want to delete " + resource,
            MessageDialogButton.Yes, MessageDialogButton.No);
      if (result == MessageDialogButton.Yes) {
         WaitingDialog waitingDialog = WaitingDialog.showDialog(ocpp.gui, "Please wait", "Deleting " + resource + "...");
         ocpp.executor.submit(() -> {
            try {
               Resource<?, ?> r = getResources(ocpp, row).withName(resourceName);
               String prefix = getResourceType(row) + "-" + resourceName + "-";
               Path deletedBackup = Files.createTempFile(ocpp.deletions, prefix, ".yaml");
               Files.write(deletedBackup, SerializationUtils.getMapper().writeValueAsBytes(r.get()));
               r.delete();
            } catch (Exception e) {
               GuiUtil.showException(ocpp, e);
            } finally {
               waitingDialog.close();
            }
         });
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
            getResources(ocpp, null).delete();
         } finally {
            waitingDialog.close();
         }
      });
   }

   protected boolean confirmDeleteAll(Ocpp ocpp) {
      return MessageDialog.showMessageDialog(ocpp.gui, "Delete all?", "Really delete all " + getResourceType(null) + "?",
            MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialogButton.Yes;
   }

   abstract String getResourceType(List<String> row);

   protected MapBuilder<String, Operation> commonOps() {
      return Resources.ops().add("describe", DESCRIBE).add("edit", EDIT).add("yaml", SHOW_YAML).add("delete", DELETE);
   }

   abstract NonNamespaceOperation<?, ?, ?, ? extends Resource<?, ?>> getResources(Ocpp ocpp, List<String> row);

}
