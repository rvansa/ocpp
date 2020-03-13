package io.openshift.ocpp;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.fabric8.kubernetes.api.model.Quantity;

public class Util {
   static String formatSeconds(long seconds) {
      if (seconds < 60) {
         return seconds + "s";
      } else if (seconds < 3600) {
         return (seconds / 60) + "m " + (seconds % 60) + "s";
      } else if (seconds < 86400) {
         return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
      } else {
         return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
      }
   }

   public static String listToString(List<String> items) {
      if (items.isEmpty()) {
         return "<none>";
      } else {
         return String.join(", ", items);
      }
   }

   static String getAge(String startTime) {
      if (startTime == null || startTime.isEmpty()) {
         return "";
      }
      return formatSeconds(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(startTime)).until(Instant.now(), ChronoUnit.SECONDS));
   }

   static String toString(Integer replicas) {
      return replicas != null ? replicas.toString() : "0";
   }

   public static int millicores(Quantity q) {
      try {
         if ("m".equals(q.getFormat())) {
            return Integer.parseInt(q.getAmount());
         } else {
            return Integer.parseInt(q.getAmount()) * 1000;
         }
      } catch (NumberFormatException e) {
          return 0;
      }
   }
}
