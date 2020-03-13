package io.openshift.ocpp;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialog;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.WaitingDialog;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;

public class Main {
   private static final HashSet<Window.Hint> MODAL_CENTERED = new HashSet<>(Arrays.asList(Window.Hint.MODAL, Window.Hint.CENTERED));

   final Ocpp ocpp;

   final TerminalScreen screen;

   final Window mainWindow = new BasicWindow("oc++");

   Resources resources = Pods.INSTANCE;
   final Table<String> table = new Table<>(resources.getColumns());

   public static void main(String[] args) throws IOException {
      new Main().run();
   }

   Main() throws IOException {
      DefaultTerminalFactory factory = new DefaultTerminalFactory();
      if (Boolean.getBoolean("forceTerminal")) {
         factory.setForceTextTerminal(true);
      }
      screen = factory.createScreen();
      ocpp = new Ocpp(new MultiWindowTextGUI(screen), new DefaultOpenShiftClient());
   }

   public void run() throws IOException {
      screen.startScreen();
      mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
      ocpp.gui.addWindow(mainWindow);
      Panel mainPanel = new Panel(new BorderLayout());
      mainWindow.setComponent(mainPanel);
      Panel resourcePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
      mainPanel.addComponent(resourcePanel, BorderLayout.Location.TOP);
      resourcePanel.addComponent(new Button("pods", () -> switchResource(Pods.INSTANCE)));
      resourcePanel.addComponent(new Button("svcs", () -> switchResource(Services.INSTANCE)));
      resourcePanel.addComponent(new Button("deployments", () -> switchResource(Deployments.INSTANCE)));
      resourcePanel.addComponent(new Button("configmaps", () -> switchResource(ConfigMaps.INSTANCE)));
      resourcePanel.addComponent(new Button("nodes", () -> switchResource(Nodes.INSTANCE)));

      mainPanel.addComponent(table, BorderLayout.Location.CENTER);
      table.setSelectAction(this::selectOperation);

      Panel actionsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
      mainPanel.addComponent(actionsPanel, BorderLayout.Location.BOTTOM);
      actionsPanel.addComponent(new Button("namespaces", this::invokeSwitchNamespace));
      actionsPanel.addComponent(new Button("delete all", () -> resources.deleteAll(ocpp)));
      actionsPanel.addComponent(new Button("quit", mainWindow::close));

      mainWindow.addWindowListener(new WindowListenerAdapter() {
         @Override
         public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
            try {
               if (keyStroke.getCharacter() == null) {
                  switch (keyStroke.getKeyType()) {
                     case Delete:
                        if (keyStroke.isShiftDown()) {
                           resources.deleteAll(ocpp);
                        } else {
                           resources.delete(ocpp, getCurrentRow());
                        }
                        break;
                     default:
                        return;
                  }
                  hasBeenHandled.set(true);
                  return;
               }
               switch (keyStroke.getCharacter()) {
                  case 'q':
                     mainWindow.close();
                     break;
                  case 'n':
                     invokeSwitchNamespace();
                     break;
                  case 'y':
                     resources.showYaml(ocpp, getCurrentRow());
                     break;
                  case 'e':
                     resources.edit(ocpp, getCurrentRow());
                     break;
                  case 'p':
                     switchResource(Pods.INSTANCE);
                     break;
                  case 's':
                     switchResource(Services.INSTANCE);
                     break;
                  case 'd':
                     switchResource(Deployments.INSTANCE);
                     break;
                  case 'c':
                     switchResource(ConfigMaps.INSTANCE);
                     break;
                  default:
                     return;
               }
               hasBeenHandled.set(true);
            } catch (Exception e) {
               GuiUtil.showException(ocpp, e);
            }
         }
      });

      ocpp.executor.scheduleWithFixedDelay(this::fetchAndUpdate, 0, 1, TimeUnit.SECONDS);

      ocpp.gui.waitForWindowToClose(mainWindow);
      ocpp.executor.shutdown();

      screen.stopScreen();
      GuiUtil.resetTerminal();
      System.exit(0);
   }

   private void selectOperation() {
      List<String> row = getCurrentRow();
      if (row == null) {
         return;
      }
      BasicWindow dialogWindow = new BasicWindow("Select operation");
      dialogWindow.setHints(MODAL_CENTERED);
      dialogWindow.setCloseWindowWithEscape(true);
      Panel panel = new Panel(new LinearLayout(Direction.HORIZONTAL));
      dialogWindow.setComponent(panel);
      Function<Runnable, Runnable> closeAnd = r -> () -> {
         dialogWindow.close();
         GuiUtil.refreshScreen(ocpp);
         r.run();
      };
      Map<String, Resources.Operation> operations = resources.getOperations();
      if (operations.isEmpty()) {
         return;
      }
      for (Map.Entry<String, Resources.Operation> entry : operations.entrySet()) {
         panel.addComponent(new Button(entry.getKey(), closeAnd.apply(() -> entry.getValue().command(ocpp, row))));
      }
      dialogWindow.addWindowListener(new WindowListenerAdapter() {
         @Override
         public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
            if (keyStroke.getKeyType() == KeyType.Character) {
               for (Component c : panel.getChildren()) {
                  Button b = (Button) c;
                  if (b.getLabel().startsWith(keyStroke.getCharacter().toString())) {
                     deliverEvent.set(true);
                     b.handleKeyStroke(new KeyStroke(KeyType.Enter));
                     break;
                  }
               }
            }
         }
      });
      ocpp.gui.addWindow(dialogWindow);
      panel.getChildren().stream().map(Button.class::cast).findFirst().ifPresent(Button::takeFocus);
   }

   private List<String> getCurrentRow() {
      int selectedRow = table.getSelectedRow();
      if (selectedRow < 0 || selectedRow >= table.getTableModel().getRowCount()) {
         return null;
      }
      List<String> row = table.getTableModel().getRow(selectedRow);
      if (row.isEmpty()) {
         return null;
      }
      return row;
   }

   private void switchResource(Resources resources) {
      this.resources = resources;
      table.getTableModel().clear();
      for (int i = table.getTableModel().getColumnCount() - 1; i >= 0; --i) {
         table.getTableModel().removeColumn(i);
      }
      for (String column : resources.getColumns()) {
         table.getTableModel().addColumn(column, new String[0]);
      }
      table.setSelectedRow(0);
      ocpp.executor.submit(this::fetchAndUpdate);
   }

   private void fetchAndUpdate() {
      if (ocpp.gui.getActiveWindow() != mainWindow) {
         return;
      }
      try {
         Resources resources = this.resources;
         List<String[]> rows = resources.fetchRows(ocpp.oc);
         ocpp.gui.getGUIThread().invokeLater(() -> {
            if (this.resources != resources) {
               // Do not update the table when the resources have switched
               return;
            }
            table.getTableModel().clear();
            for (String[] row : rows) {
               table.getTableModel().addRow(row);
            }
         });
      } catch (KubernetesClientException kce) {
         if (kce.getCode() == 401) {
            login();
         } else {
            GuiUtil.showException(ocpp, kce);
         }
      } catch (Exception e) {
         GuiUtil.showException(ocpp, e);
      }
   }

   private void login() {
      BasicWindow window = new BasicWindow("Please log in");
      window.setHints(MODAL_CENTERED);
      Panel panel = new Panel(new GridLayout(2));
      window.setComponent(panel);
      panel.addComponent(new Label("server"));
      TextBox masterUrl = new TextBox(ocpp.oc.getConfiguration().getMasterUrl() != null ? ocpp.oc.getConfiguration().getMasterUrl() : "");
      panel.addComponent(masterUrl);
      panel.addComponent(new Label("user"));
      TextBox username = new TextBox(ocpp.oc.getConfiguration().getUsername() != null ? ocpp.oc.getConfiguration().getUsername() : "");
      panel.addComponent(username);
      panel.addComponent(new Label("password"));
      TextBox password = new TextBox().setMask('*');
      panel.addComponent(password);
      panel.addComponent(new Button("Login", () -> {
         ocpp.oc.getConfiguration().setMasterUrl(masterUrl.getText());
         ocpp.oc.getConfiguration().setUsername(username.getText());
         ocpp.oc.getConfiguration().setPassword(password.getText());
         window.close();
         WaitingDialog waitingDialog = WaitingDialog.showDialog(ocpp.gui, "Please wait", "Logging in...");
         ocpp.executor.submit(() -> {
            ocpp.oc = new DefaultOpenShiftClient(ocpp.oc.getConfiguration());
            waitingDialog.close();
            fetchAndUpdate();
         });
      }), GridLayout.createHorizontallyFilledLayoutData(2));
      ocpp.gui.addWindow(window);
   }

   private void invokeSwitchNamespace() {
      WaitingDialog dialog = WaitingDialog.showDialog(ocpp.gui, "Loading...", "Please wait for the list of namespaces");
      ocpp.executor.submit(() -> switchNamespace(dialog));
   }

   private void switchNamespace(WaitingDialog waitingDialog) {
      ActionListDialogBuilder builder = new ActionListDialogBuilder().setTitle("Select namespace...").setCanCancel(true);
      try {
         for (Namespace ns : ocpp.oc.namespaces().list().getItems()) {
            String name = ns.getMetadata().getName();
            builder.addAction(name, () -> {
               ocpp.oc.getConfiguration().setNamespace(name);
               table.setSelectedRow(0);
            });
         }
      } catch (KubernetesClientException kce) {
         if (kce.getCode() == 401) {
            login();
            switchNamespace(waitingDialog);
         } else {
            fail(kce);
         }
      }
      waitingDialog.close();
      ActionListDialog dialog = builder.setExtraWindowHints(new HashSet<>(Arrays.asList(Window.Hint.FIT_TERMINAL_WINDOW, Window.Hint.MODAL))).build();
      dialog.setCloseWindowWithEscape(true);
      ActionListBox listBox = ((Panel) dialog.getComponent()).getChildren().stream()
            .filter(ActionListBox.class::isInstance).map(ActionListBox.class::cast)
            .findFirst().orElse(null);
      dialog.addWindowListener(new WindowListenerAdapter() {
         String searchString = "";

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
      });
      dialog.showDialog(ocpp.gui);
   }

   private void fail(Throwable throwable) {
      try {
         screen.stopScreen();
      } catch (IOException e) {
         e.printStackTrace();
      }
      GuiUtil.resetTerminal();
      throwable.printStackTrace();
      System.exit(1);
   }
}
