package io.openshift.ocpp;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialog;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

class SearchActionsByKey extends WindowListenerAdapter {
   private final ActionListBox listBox;
   private String searchString = "";

   public SearchActionsByKey(ActionListDialog dialog) {
      listBox = ((Panel) dialog.getComponent()).getChildren().stream()
            .filter(ActionListBox.class::isInstance).map(ActionListBox.class::cast)
            .findFirst().orElse(null);
   }

   @Override
   public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
      if (keyStroke.getKeyType() == KeyType.Character) {
         searchString += keyStroke.getCharacter();
         List<Runnable> items = listBox.getItems();
         int longestMatch = 0;
         int matchLength = 0;
         for (int i = 0; i < items.size(); i++) {
            Runnable item = items.get(i);
            String label = item.toString();
            for (int j = 0; ; ++j) {
               if (j >= searchString.length() || j >= label.length()) {
                  if (j > matchLength) {
                     longestMatch = i;
                     matchLength = j;
                  }
                  break;
               }
               if (label.charAt(j) != searchString.charAt(j)) {
                  if (j - 1 > matchLength) {
                     longestMatch = i;
                     matchLength = j - 1;
                  }
                  break;
               }
            }
         }
         if (matchLength > 0) {
            listBox.setSelectedIndex(longestMatch);
         }
         hasBeenHandled.set(true);
      } else {
         searchString = "";
      }
   }
}
