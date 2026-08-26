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

    /** 托盘是否已成功挂载（App 靠它决定是否兜底显示主窗口） */
    private static volatile boolean trayReady = false;

    public static boolean isTrayReady() {
        return trayReady;
    }

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
            trayReady = true;
            trayIcon.addActionListener(e -> showMainWindow());
            // 托盘就绪后再藏主窗口（无窗运行）；托盘初始化失败则保留窗口，程序永远可操作
            Platform.runLater(() -> {
                javafx.stage.Stage stage = (javafx.stage.Stage) mainWindow.getScene().getWindow();
                if (stage != null) {
                    stage.hide();
                }
            });
        } catch (AWTException e) {
            System.err.println("Tray add failed: " + e.getMessage());
        }
    }

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem showItem = new MenuItem("Show Panel");
        showItem.addActionListener(e -> showMainWindow());
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

    /**
     * 显示主窗口（对隐藏/最小化状态都有效）
     */
    private void showMainWindow() {
        javafx.stage.Stage stage = (javafx.stage.Stage) mainWindow.getScene().getWindow();
        if (stage != null) {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        }
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
        // 保存窗口位置尺寸（System.exit 不走 JavaFX stop 流程，需显式保存）
        com.servicemanager.App.saveWindowState();
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
