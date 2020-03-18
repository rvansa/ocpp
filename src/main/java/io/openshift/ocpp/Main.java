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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import com.googlecode.lanterna.TerminalSize;
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
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.WaitingDialog;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import io.fabric8.kubernetes.api.model.AuthInfoBuilder;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.NamedAuthInfo;
import io.fabric8.kubernetes.api.model.NamedCluster;
import io.fabric8.kubernetes.api.model.NamedClusterBuilder;
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
         public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
            table.setVisibleRows(newSize.getRows() - 3);
         }

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

      if (ocpp.oc.getConfiguration().getNamespace() == null) {
         ocpp.oc.getConfiguration().setNamespace("default");
      }
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
            Panel mainPanel = (Panel) mainWindow.getComponent();
            if (rows.size() == 0) {
               if (mainPanel.removeComponent(table)) {
                  Label label = new Label("No " + resources.getResourceType(null) + " in namespace " + ocpp.ns());
                  mainPanel.addComponent(label, BorderLayout.Location.CENTER);
               }
            } else {
               Component label = mainPanel.getChildren().stream().filter(Label.class::isInstance).findFirst().orElse(null);
               if (label != null) {
                  mainPanel.removeComponent(label);
                  mainPanel.addComponent(table, BorderLayout.Location.CENTER);
               }
            }
            table.getTableModel().clear();
            for (String[] row : rows) {
               table.getTableModel().addRow(row);
            }
         });
      } catch (KubernetesClientException kce) {
         if (kce.getCause() instanceof SSLException) {
            askForInsecureConnection();
         } else if (requiresLogin(kce)) {
            login();
         } else {
            GuiUtil.showException(ocpp, kce);
         }
      } catch (Exception e) {
         GuiUtil.showException(ocpp, e);
      }
   }

   private void askForInsecureConnection() {
      if (MessageDialog.showMessageDialog(ocpp.gui, "Allow insecure connection?",
            "Certificate for the API connection is invalid. Allow insecure connection?",
            MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialogButton.Yes) {
         ocpp.oc.getConfiguration().setTrustCerts(true);
         retryLogin(new CompletableFuture<>());
      } else {
         GuiUtil.resetTerminal();
         System.exit(1);
      }
   }

   private CompletableFuture<Void> login() {
      CompletableFuture<Void> loggedInFuture = new CompletableFuture<>();
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
         retryLogin(loggedInFuture);
      }), GridLayout.createHorizontallyFilledLayoutData(2));
      window.addWindowListener(new WindowListenerAdapter() {
         @Override
         public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
            if (keyStroke.getKeyType() == KeyType.Escape) {
               GuiUtil.resetTerminal();
               System.exit(0);
            }
         }
      });
      ocpp.gui.addWindow(window);
      username.takeFocus();
      return loggedInFuture;
   }

   private void retryLogin(CompletableFuture<Void> loggedInFuture) {
      WaitingDialog waitingDialog = WaitingDialog.showDialog(ocpp.gui, "Please wait", "Logging in...");
      ocpp.executor.submit(() -> {
         DefaultOpenShiftClient newClient = new DefaultOpenShiftClient(ocpp.oc.getConfiguration());
         ocpp.oc = newClient;
         waitingDialog.close();
         loggedInFuture.complete(null);
         fetchAndUpdate();
         persistToken(newClient);
      });
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
         updateKubeconfig(config -> {
            String context = config.getCurrentContext();
            if (context == null) {
               String masterUrl = newClient.getConfiguration().getMasterUrl();
               int hostStart = masterUrl.indexOf("://") + 3;
               if (masterUrl.indexOf('/', hostStart) >= 0) {
                  masterUrl = masterUrl.substring(0, masterUrl.indexOf('/', hostStart));
               }
               String finalMasterUrl = masterUrl;
               String clusterName = config.getClusters().stream()
                     .filter(c -> finalMasterUrl.equals(c.getCluster().getServer()))
                     .map(NamedCluster::getName).findFirst().orElse(null);
               if (clusterName == null) {
                  clusterName = masterUrl.substring(hostStart).replaceAll("\\.", "-");
                  config.getClusters().add(new NamedClusterBuilder()
                        .withName(clusterName)
                        .withNewCluster()
                           .withInsecureSkipTlsVerify(newClient.getConfiguration().isTrustCerts())
                           .withServer(masterUrl)
                        .endCluster().build());
               }
               context = "default/" + clusterName + "/" + newClient.getConfiguration().getUsername();
               config.setCurrentContext(context);
            }
            String[] contextParts = context.split("/");
            String configUsername = (contextParts.length < 3 ? "" : contextParts[2]) + "/" + (contextParts.length < 2 ? "" : contextParts[1]);
            Optional<NamedAuthInfo> nai = config.getUsers().stream().filter(u -> configUsername.equals(u.getName())).findFirst();
            if (nai.isPresent()) {
               nai.get().getUser().setToken(token);
            } else {
               config.getUsers().add(new NamedAuthInfo(configUsername, new AuthInfoBuilder().withToken(token).build()));
            }
         });
      });
   }

   private void updateKubeconfig(Consumer<Config> configUpdater) {
      try {
         String KUBECONFIG = System.getenv("KUBECONFIG");
         Path kubeconfig = KUBECONFIG != null ? Paths.get(KUBECONFIG) : Paths.get(System.getProperty("user.home"), ".kube", "config");
         Config config = KubeConfigUtils.parseConfig(kubeconfig.toFile());
         configUpdater.accept(config);
         Path backup = kubeconfig.getParent().resolve("config.backup");
         if (!backup.toFile().exists()) {
            Files.copy(kubeconfig, backup);
         }
         Files.write(kubeconfig, SerializationUtils.getMapper().writeValueAsBytes(config));
      } catch (IOException e) {
         GuiUtil.showException(ocpp, e);
      }
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
               ocpp.executor.submit(() -> updateKubeconfig(config -> {
                  String[] ctx = config.getCurrentContext().split("/");
                  config.setCurrentContext(name + "/" + ctx[1] + "/" + ctx[2]);
               }));
            });
         }
      } catch (KubernetesClientException kce) {
         waitingDialog.close();
         if (kce.getCause() instanceof SSLException) {
            askForInsecureConnection();
         } else if (requiresLogin(kce)) {
            login().thenRun(this::invokeSwitchNamespace);
         } else {
            fail(kce);
         }
         return;
      }
      waitingDialog.close();
      ActionListDialog dialog = builder.setExtraWindowHints(new HashSet<>(Arrays.asList(Window.Hint.FIT_TERMINAL_WINDOW, Window.Hint.MODAL))).build();
      dialog.setCloseWindowWithEscape(true);
      dialog.addWindowListener(new SearchActionsByKey(dialog));
      dialog.showDialog(ocpp.gui);
   }

   private boolean requiresLogin(KubernetesClientException kce) {
      return kce.getCode() == 401 || kce.getCode() == 0;
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
