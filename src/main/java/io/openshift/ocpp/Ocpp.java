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

   public Ocpp(WindowBasedTextGUI gui, OpenShiftClient oc) throws IOException {
      this.gui = gui;
      this.oc = oc;
      this.deletions = Files.createTempDirectory("ocpp-deletions");
   }

   public String ns() {
      return oc.getConfiguration().getNamespace();
   }
}
