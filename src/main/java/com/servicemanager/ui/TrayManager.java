package com.servicemanager.ui;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * 系统托盘管理
 */
public class TrayManager {

    private TrayIcon trayIcon;
    private final MainFrame mainFrame;
    private MenuItem autoStartItem;

    public TrayManager(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        if (!SystemTray.isSupported()) {
            System.err.println("当前系统不支持系统托盘");
            return;
        }

        // 生成托盘图标（16x16 蓝色圆点）
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0x21, 0x96, 0xF3)); // Material Blue
        g2d.fillOval(0, 0, 16, 16);
        g2d.dispose();

        trayIcon = new TrayIcon(image, "服务管理面板", buildMenu());

        try {
            SystemTray.getSystemTray().add(trayIcon);
            // 双击托盘图标显示面板
            trayIcon.addActionListener(e -> mainFrame.setVisible(true));
        } catch (AWTException e) {
            System.err.println("无法添加托盘图标: " + e.getMessage());
        }
    }

    /**
     * 构建托盘右键菜单
     */
    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem showItem = new MenuItem("显示面板");
        showItem.addActionListener(e -> mainFrame.setVisible(true));
        menu.add(showItem);

        menu.addSeparator();

        MenuItem startAllItem = new MenuItem("启动全部");
        startAllItem.addActionListener(e -> mainFrame.startAllServices());
        menu.add(startAllItem);

        MenuItem stopAllItem = new MenuItem("停止全部");
        stopAllItem.addActionListener(e -> mainFrame.stopAllServices());
        menu.add(stopAllItem);

        menu.addSeparator();

        // 开机自启菜单项
        boolean isAutoStart = StartupManager.isAutoStartEnabled();
        autoStartItem = new MenuItem(isAutoStart ? "✓ 开机自启（已启用）" : "开机自启（未启用）");
        autoStartItem.addActionListener(e -> toggleAutoStart());
        menu.add(autoStartItem);

        menu.addSeparator();

        MenuItem exitItem = new MenuItem("退出");
        exitItem.addActionListener(e -> exit());
        menu.add(exitItem);

        return menu;
    }

    /**
     * 切换开机自启
     */
    private void toggleAutoStart() {
        boolean current = StartupManager.isAutoStartEnabled();
        if (current) {
            StartupManager.disableAutoStart();
            autoStartItem.setLabel("开机自启（未启用）");
        } else {
            StartupManager.enableAutoStart();
            autoStartItem.setLabel("✓ 开机自启（已启用）");
        }
    }

    /**
     * 完全退出程序
     */
    private void exit() {
        mainFrame.stopTimer();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        System.exit(0);
    }

    /**
     * 显示气泡通知
     */
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
