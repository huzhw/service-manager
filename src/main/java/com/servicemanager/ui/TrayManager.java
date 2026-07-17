package com.servicemanager.ui;

import com.servicemanager.MainWindow;
import javafx.application.Platform;

import java.awt.*;

/**
 * 系统托盘管理
 */
public class TrayManager {

    private TrayIcon trayIcon;
    private final MainWindow mainWindow;
    private MenuItem autoStartItem;
    private boolean running = true;

    public TrayManager(MainWindow mainWindow) {
        this.mainWindow = mainWindow;

        if (!SystemTray.isSupported()) {
            System.err.println("Tray not supported");
            return;
        }

        Image image = AppIcon.createTrayIcon();
        trayIcon = new TrayIcon(image, "Service Manager", buildMenu());

        try {
            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.addActionListener(e -> {
                javafx.stage.Stage stage = (javafx.stage.Stage) mainWindow.getScene().getWindow();
                stage.setIconified(false);
                stage.toFront();
            });
        } catch (AWTException e) {
            System.err.println("Tray add failed: " + e.getMessage());
        }
    }

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem showItem = new MenuItem("Show Panel");
        showItem.addActionListener(e -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) mainWindow.getScene().getWindow();
            stage.setIconified(false);
            stage.toFront();
        });
        menu.add(showItem);

        menu.addSeparator();

        MenuItem startAllItem = new MenuItem("Start All");
        startAllItem.addActionListener(e -> mainWindow.startAllServices());
        menu.add(startAllItem);

        MenuItem stopAllItem = new MenuItem("Stop All");
        stopAllItem.addActionListener(e -> mainWindow.stopAllServices());
        menu.add(stopAllItem);

        menu.addSeparator();

        boolean isAutoStart = StartupManager.isAutoStartEnabled();
        autoStartItem = new MenuItem(isAutoStart ? "V  Auto-start (ON)" : "Auto-start (OFF)");
        autoStartItem.addActionListener(e -> toggleAutoStart());
        menu.add(autoStartItem);

        menu.addSeparator();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> exit());
        menu.add(exitItem);

        return menu;
    }

    private void toggleAutoStart() {
        boolean current = StartupManager.isAutoStartEnabled();
        if (current) {
            StartupManager.disableAutoStart();
            autoStartItem.setLabel("Auto-start (OFF)");
        } else {
            StartupManager.enableAutoStart();
            autoStartItem.setLabel("V  Auto-start (ON)");
        }
    }

    public void exit() {
        running = false;
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        Platform.exit();
        System.exit(0);
    }

    public void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    // ==========================================
    //  开机自启管理（注册表方式）
    // ==========================================
    static class StartupManager {

        private static final String REG_KEY = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        private static final String ENTRY_NAME = "ServiceManager";

        /**
         * 检查是否已启用开机自启
         */
        static boolean isAutoStartEnabled() {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "reg", "query", REG_KEY, "/v", ENTRY_NAME);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exitCode = p.waitFor();
                return exitCode == 0;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * 启用开机自启
         */
        static void enableAutoStart() {
            try {
                String javaHome = System.getProperty("java.home");
                String jarPath = getJarPath();
                String cmd = String.format("cmd /c \"%s\\bin\\javaw.exe -jar \"%s\"\"",
                        javaHome, jarPath);

                ProcessBuilder pb = new ProcessBuilder(
                        "reg", "add", REG_KEY, "/v", ENTRY_NAME,
                        "/t", "REG_SZ", "/d", cmd, "/f");
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (Exception e) {
                // ignore
            }
        }

        /**
         * 禁用开机自启
         */
        static void disableAutoStart() {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "reg", "delete", REG_KEY, "/v", ENTRY_NAME, "/f");
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (Exception e) {
                // ignore
            }
        }

        /**
         * 获取当前运行的 JAR 路径
         */
        private static String getJarPath() {
            String classpath = System.getProperty("java.class.path");
            String[] paths = classpath.split(";");
            for (String path : paths) {
                if (path.toLowerCase().endsWith(".jar") && path.toLowerCase().contains("service-manager")) {
                    return path;
                }
            }
            return System.getProperty("user.dir") + "\\service-manager.jar";
        }
    }
}
