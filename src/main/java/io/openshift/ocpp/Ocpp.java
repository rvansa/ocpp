package io.openshift.ocpp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

import io.fabric8.openshift.client.OpenShiftClient;

public class Ocpp {
   static final String OC_BINARY = System.getProperty("ocpp.oc", "oc");
   final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
   final WindowBasedTextGUI gui;
   final Path deletions;
   OpenShiftClient oc;
   private Resources resources = Pods.INSTANCE;
   private final Runnable resourceSwitchCallback;

   public Ocpp(WindowBasedTextGUI gui, OpenShiftClient oc, Runnable resourceSwitchCallback) throws IOException {
      this.gui = gui;
      this.oc = oc;
      this.resourceSwitchCallback = resourceSwitchCallback;
      this.deletions = Files.createTempDirectory("ocpp-deletions");
   }

   public String ns() {
      return oc.getConfiguration().getNamespace();
   }

   public Resources resources() {
      return resources;
   }

   public void switchResources(Resources resources) {
      this.resources = resources;
      resourceSwitchCallback.run();
   }
}
