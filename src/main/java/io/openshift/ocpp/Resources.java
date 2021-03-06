package io.openshift.ocpp;

import java.util.List;
import java.util.Map;

public interface Resources {
   String[] getColumns();

   List<String[]> fetchRows(Ocpp ocpp);

   Map<String, Operation> getOperations(List<String> row);

   void describe(Ocpp ocpp, List<String> row);

   void showYaml(Ocpp ocpp, List<String> row);

   void edit(Ocpp ocpp, List<String> row);

   void delete(Ocpp ocpp, List<String> row);

   void deleteAll(Ocpp ocpp);

   String getResourceType(List<String> row);

   interface Operation {
      void command(Ocpp ocpp, List<String> row);
   }

   static MapBuilder<String, Operation> ops() {
      return new MapBuilder<>();
   }
}
