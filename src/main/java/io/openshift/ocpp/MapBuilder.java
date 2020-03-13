package io.openshift.ocpp;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapBuilder<K, V> {
   private Map<K, V> map = new LinkedHashMap<>();

   public MapBuilder<K, V> add(K key, V value) {
      map.put(key, value);
      return this;
   }

   public Map<K, V> build() {
      return map;
   }
}
