package io.openshift.ocpp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

import io.fabric8.openshift.client.OpenShiftClient;

public class Ocpp {
   static final String OC_BINARY = System.getProperty("ocpp.oc", "oc");
   final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
   final WindowBasedTextGUI gui;
   OpenShiftClient oc;

   public Ocpp(WindowBasedTextGUI gui, OpenShiftClient oc) {
      this.gui = gui;
      this.oc = oc;
   }

   public String ns() {
      return oc.getConfiguration().getNamespace();
   }
}
