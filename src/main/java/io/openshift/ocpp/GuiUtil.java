package io.openshift.ocpp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.WaitingDialog;
import com.googlecode.lanterna.screen.Screen;

public class GuiUtil {
   static void showException(Ocpp ocpp, Throwable e) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      e.printStackTrace(new PrintStream(stream));
      refreshScreen(ocpp);
      String error = new String(stream.toByteArray());
      ocpp.gui.getGUIThread().invokeLater(() -> {
         BasicWindow errorWindow = new BasicWindow("Error");
         errorWindow.setHints(new HashSet<>(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.MODAL)));
         Panel panel = new Panel(new BorderLayout());
         errorWindow.setComponent(panel);
         panel.addComponent(new TextBox(error, TextBox.Style.MULTI_LINE)
               .setReadOnly(true).setVerticalFocusSwitching(false));
         panel.addComponent(new Button("OK", errorWindow::close), BorderLayout.Location.BOTTOM);
         ocpp.gui.addWindow(errorWindow);
      });
   }

   static void resetTerminal() {
      System.out.print("\u001b[0m\u001b[2J\u001b[H");
   }

   static void runAndView(Ocpp ocpp, String[] command, String prefix, String suffix) {
      WaitingDialog waitingDialog = WaitingDialog.showDialog(ocpp.gui, "Please wait", "Downloading...");
      ocpp.executor.submit(() -> {
         try {
            File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            Process process = new ProcessBuilder(command)
                  .inheritIO().redirectOutput(tempFile).start();
            try {
               if (process.waitFor() != 0) {
                  MessageDialog.showMessageDialog(ocpp.gui, "Error", "Process has exited with status " + process.exitValue(), MessageDialogButton.OK);
                  return;
               }
            } catch (InterruptedException e) {
               showException(ocpp, e);
               tempFile.delete();
               return;
            } finally {
               waitingDialog.close();
            }
            ocpp.gui.getGUIThread().invokeLater(() -> {
               resetTerminal();
               try {
                  new ProcessBuilder("less", tempFile.getAbsolutePath()).inheritIO().start().waitFor();
               } catch (InterruptedException | IOException e) {
                  showException(ocpp, e);
               }
               tempFile.delete();
               refreshScreen(ocpp);
            });
         } catch (IOException e) {
            waitingDialog.close();
            showException(ocpp, e);
         }
      });
   }

   static void refreshScreen(Ocpp ocpp) {
      try {
         ocpp.gui.getScreen().refresh(Screen.RefreshType.COMPLETE);
      } catch (IOException e) {
         showException(ocpp, e);
      }
   }

   static void ssh(Ocpp ocpp, String hostname, String... command) {
      resetTerminal();
      try {
         String[] fullCommand = Stream.concat(
               Stream.of("ssh", "-t", "-o", "StrictHostKeyChecking=no", "core@" + hostname),
               Stream.of(command)).toArray(String[]::new);
         ProcessBuilder processBuilder = new ProcessBuilder(fullCommand).inheritIO();
         Process ssh = processBuilder.start();
         if (ssh.waitFor() != 0) {
            refreshScreen(ocpp);
            MessageDialog.showMessageDialog(ocpp.gui, "SSH failed", "Failed to ssh to " + hostname + ", exit status " + ssh.waitFor(), MessageDialogButton.OK);
         }
         refreshScreen(ocpp);
      } catch (IOException | InterruptedException e) {
         showException(ocpp, e);
      }
   }
}
