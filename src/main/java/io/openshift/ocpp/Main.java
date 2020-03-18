package io.openshift.ocpp;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
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
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import io.fabric8.kubernetes.api.model.AuthInfoBuilder;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.NamedAuthInfo;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.internal.OpenShiftOAuthInterceptor;

public class Main {
   private static final HashSet<Window.Hint> MODAL_CENTERED = new HashSet<>(Arrays.asList(Window.Hint.MODAL, Window.Hint.CENTERED));

   final Ocpp ocpp;
   final TerminalScreen screen;
   final Window mainWindow = new BasicWindow("oc++");
   final Table<String> table;

   public static void main(String[] args) throws IOException {
      new Main().run();
   }

   Main() throws IOException {
      DefaultTerminalFactory factory = new DefaultTerminalFactory();
      if (Boolean.getBoolean("forceTerminal")) {
         factory.setForceTextTerminal(true);
      }
      screen = factory.createScreen();
      ocpp = new Ocpp(new MultiWindowTextGUI(screen), new DefaultOpenShiftClient(), this::onResourcesSwitch);
      table = new Table<>(ocpp.resources().getColumns());
   }

   public void run() throws IOException {
      screen.startScreen();
      mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
      ocpp.gui.addWindow(mainWindow);
      Panel mainPanel = new Panel(new BorderLayout());
      mainWindow.setComponent(mainPanel);
      Panel resourcePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
      mainPanel.addComponent(resourcePanel, BorderLayout.Location.TOP);
      resourcePanel.addComponent(new Button("pods", () -> ocpp.switchResources(Pods.INSTANCE)));
      resourcePanel.addComponent(new Button("svcs", () -> ocpp.switchResources(Services.INSTANCE)));
      resourcePanel.addComponent(new Button("deployments", () -> ocpp.switchResources(Deployments.INSTANCE)));
      resourcePanel.addComponent(new Button("configmaps", () -> ocpp.switchResources(ConfigMaps.INSTANCE)));
      resourcePanel.addComponent(new Button("nodes", () -> ocpp.switchResources(Nodes.INSTANCE)));

      mainPanel.addComponent(table, BorderLayout.Location.CENTER);
      table.setSelectAction(this::selectOperation);

      Panel actionsPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
      mainPanel.addComponent(actionsPanel, BorderLayout.Location.BOTTOM);
      actionsPanel.addComponent(new Button("namespaces", this::invokeSwitchNamespace));
      actionsPanel.addComponent(new Button("delete all", () -> ocpp.resources().deleteAll(ocpp)));
      actionsPanel.addComponent(new Button("quit", mainWindow::close));

      mainWindow.addWindowListener(new WindowListenerAdapter() {
         @Override
         public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
            try {
               if (keyStroke.getCharacter() == null) {
                  switch (keyStroke.getKeyType()) {
                     case Delete:
                        if (keyStroke.isShiftDown()) {
                           ocpp.resources().deleteAll(ocpp);
                        } else {
                           ocpp.resources().delete(ocpp, getCurrentRow());
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
                     ocpp.resources().showYaml(ocpp, getCurrentRow());
                     break;
                  case 'e':
                     ocpp.resources().edit(ocpp, getCurrentRow());
                     break;
                  case 'p':
                     ocpp.switchResources(Pods.INSTANCE);
                     break;
                  case 's':
                     ocpp.switchResources(Services.INSTANCE);
                     break;
                  case 'd':
                     ocpp.switchResources(Deployments.INSTANCE);
                     break;
                  case 'c':
                     ocpp.switchResources(ConfigMaps.INSTANCE);
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
      ActionListDialogBuilder actions = new ActionListDialogBuilder().setTitle("Select operation");

      Map<String, Resources.Operation> operations = ocpp.resources().getOperations(row);
      if (operations.isEmpty()) {
         return;
      }
      for (Map.Entry<String, Resources.Operation> entry : operations.entrySet()) {
         actions.addAction(entry.getKey(), () -> entry.getValue().command(ocpp, row));
      }
      ActionListDialog dialog = actions.build();
      dialog.setCloseWindowWithEscape(true);
      dialog.addWindowListener(new SearchActionsByKey(dialog));
      ocpp.gui.addWindow(dialog);
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

   private void onResourcesSwitch() {
      table.getTableModel().clear();
      for (int i = table.getTableModel().getColumnCount() - 1; i >= 0; --i) {
         table.getTableModel().removeColumn(i);
      }
      for (String column : ocpp.resources().getColumns()) {
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
         Resources resources = ocpp.resources();
         List<String[]> rows = resources.fetchRows(ocpp);
         ocpp.gui.getGUIThread().invokeLater(() -> {
            if (ocpp.resources() != resources) {
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
            DefaultOpenShiftClient newClient = new DefaultOpenShiftClient(ocpp.oc.getConfiguration());
            ocpp.oc = newClient;
            waitingDialog.close();
            fetchAndUpdate();
            persistToken(newClient);
         });
      }), GridLayout.createHorizontallyFilledLayoutData(2));
      ocpp.gui.addWindow(window);
      username.takeFocus();
   }

   private void persistToken(DefaultOpenShiftClient newClient) {
      newClient.getHttpClient().interceptors().stream()
            .filter(OpenShiftOAuthInterceptor.class::isInstance)
            .findFirst().ifPresent(interceptor -> {
         String token;
         try {
            Field oauthToken = OpenShiftOAuthInterceptor.class.getDeclaredField("oauthToken");
            oauthToken.setAccessible(true);
            AtomicReference<String> tokenReference = (AtomicReference<String>) oauthToken.get(interceptor);
            token = tokenReference.get();
            if (token == null) {
               return;
            }
         } catch (NoSuchFieldException | IllegalAccessException e) {
            return;
         }
         try {
            Path kubeconfig = Paths.get(System.getProperty("user.home"), ".kube", "config");
            Config config = KubeConfigUtils.parseConfig(kubeconfig.toFile());
            String[] context = config.getCurrentContext().split("/");
            String configUsername = context[2] + "/" + context[1];
            Optional<NamedAuthInfo> nai = config.getUsers().stream().filter(u -> configUsername.equals(u.getName())).findFirst();
            if (nai.isPresent()) {
               nai.get().getUser().setToken(token);
            } else {
               config.getUsers().add(new NamedAuthInfo(configUsername, new AuthInfoBuilder().withToken(token).build()));
            }
            Path backup = kubeconfig.getParent().resolve("config.backup");
            if (!backup.toFile().exists()) {
               Files.copy(kubeconfig, backup);
            }
            Files.write(kubeconfig, SerializationUtils.getMapper().writeValueAsBytes(config));
         } catch (IOException e) {
            GuiUtil.showException(ocpp, e);
         }
      });
   }

   private void invokeSwitchNamespace() {
      WaitingDialog dialog = WaitingDialog.showDialog(ocpp.gui, "Loading...", "Please wait for the list of namespaces");
      ocpp.executor.submit(() -> switchNamespace(dialog));
   }

   private void switchNamespace(WaitingDialog waitingDialog) {
      ActionListDialogBuilder builder = new ActionListDialogBuilder().setTitle("Select namespace...");
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
      dialog.addWindowListener(new SearchActionsByKey(dialog));
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
