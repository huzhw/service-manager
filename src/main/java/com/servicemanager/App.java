package com.servicemanager;

import com.servicemanager.config.ServiceConfigLoader;
import com.servicemanager.model.ServiceInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * JavaFX 入口 — Service Manager 2.0
 * <p>
 * 窗口尺寸持久化：默认高度为初始设计的 70%（560px），
 * 关闭时把位置和大小写入 work/window.properties，下次启动恢复。
 * 保存入口为静态 {@link #saveWindowState()}，点 X 与托盘退出两条路径都会调用。
 */
public class App extends Application {

    private static final int SINGLE_INSTANCE_PORT = 19953;
    private static ServerSocket lockSocket;

    /** 窗口状态文件（项目内，随 work 目录走） */
    private static final File BOUNDS_FILE = new File("work", "window.properties");

    /** 默认窗口尺寸 */
    private static final double DEFAULT_W = 1280;
    private static final double DEFAULT_H = 700;

    /** 关闭前的最小合法尺寸，防止拖成极小后存盘 */
    private static final double MIN_W = 500;
    private static final double MIN_H = 360;

    /** 主舞台引用（静态，供托盘路径保存用） */
    private static Stage mainStage;

    private List<ServiceInfo> services;
    private MainWindow mainWindow;

    @Override
    public void start(Stage stage) {
        mainStage = stage;

        // 加载服务配置
        services = ServiceConfigLoader.load();

        // 主窗口（WebView 壳 + 内嵌 Web 服务）
        mainWindow = new MainWindow(services, stage);

        // 恢复上次窗口尺寸；无记录则用默认值，并夹紧到屏幕范围内
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double[] saved = loadBounds();
        // 防呆：存档宽或高达屏幕 92% 以上，视为"最大化/拉满后关闭"的误操作，整份丢弃回默认值
        if (saved != null && (saved[2] > vb.getWidth() * 0.92 || saved[3] > vb.getHeight() * 0.92)) {
            saved = null;
        }
        double w = clamp(saved != null ? saved[2] : DEFAULT_W, MIN_W, vb.getWidth());
        double h = clamp(saved != null ? saved[3] : DEFAULT_H, MIN_H, vb.getHeight());

        Scene scene = new Scene(mainWindow, w, h);
        stage.setScene(scene);
        stage.setTitle("服务管理面板 2.0");
        if (saved != null) {
            // 位置合法性：至少有 80px 落在屏幕内，否则放弃恢复位置
            if (saved[0] > -w + 80 && saved[0] < vb.getWidth() - 80) {
                stage.setX(saved[0]);
            }
            if (saved[1] > -h + 80 && saved[1] < vb.getHeight() - 80) {
                stage.setY(saved[1]);
            }
        }

        // 应用图标（Java2D 绘制齿轮图标）
        List<java.awt.Image> awtIcons = com.servicemanager.ui.AppIcon.createWindowIcons();
        for (java.awt.Image awtImg : awtIcons) {
            java.awt.image.BufferedImage buf = (java.awt.image.BufferedImage) awtImg;
            javafx.scene.image.Image fxImg = javafx.embed.swing.SwingFXUtils.toFXImage(buf, null);
            stage.getIcons().add(fxImg);
        }

        // 系统托盘（用 Swing 互操作）
        Platform.setImplicitExit(false);
        SwingUtilities.invokeLater(() -> {
            com.servicemanager.ui.TrayManager tray = new com.servicemanager.ui.TrayManager(mainWindow);
        });

        // 单实例唤醒监听：再次双击桌面快捷方式 → 重新弹出浏览器
        startWakeListener(mainWindow);

        // 点 X 只藏窗口（程序驻留托盘，面板在浏览器中）；彻底退出走托盘 Exit
        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
        });
        // 启动不显示主窗口：无窗驻留托盘（浏览器即面板）。
        // 兜底：4 秒后托盘仍不可用才显示窗口，避免程序变"幽灵进程"失去控制入口
        Thread fallback = new Thread(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                return;
            }
            Platform.runLater(() -> {
                if (!com.servicemanager.ui.TrayManager.isTrayReady()) {
                    stage.show();
                }
            });
        }, "window-fallback");
        fallback.setDaemon(true);
        fallback.start();
    }

    @Override
    public void stop() {
        saveWindowState();
        // 停内嵌 Web 服务与定时刷新线程
        if (mainWindow != null) {
            mainWindow.shutdown();
        }
        if (lockSocket != null) {
            try { lockSocket.close(); } catch (IOException e) { }
        }
        Platform.exit();
        System.exit(0);
    }

    /**
     * 保存当前窗口边界到 work/window.properties。
     * 静态方法：点 X（stop 流程）与托盘退出（绕过 stop）都能调。
     */
    public static synchronized void saveWindowState() {
        Stage stage = mainStage;
        if (stage == null) {
            return;
        }
        // 最大化/全屏状态下不落盘：防止把近全屏尺寸当成用户习惯记住
        if (stage.isMaximized() || stage.isFullScreen()) {
            return;
        }
        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (Double.isNaN(x) || w <= MIN_W || h <= MIN_H || Double.isNaN(w)) {
            return; // 尚未布局或尺寸非法，跳过
        }
        try {
            File dir = BOUNDS_FILE.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Properties p = new Properties();
            p.setProperty("x", String.valueOf(x));
            p.setProperty("y", String.valueOf(y));
            p.setProperty("w", String.valueOf(w));
            p.setProperty("h", String.valueOf(h));
            try (OutputStream out = new FileOutputStream(BOUNDS_FILE)) {
                p.store(out, "Service Manager window bounds");
            }
        } catch (Exception e) {
            // 持久化失败不影响退出
        }
    }

    /**
     * 读取上次的窗口边界 [x,y,w,h]；记录不完整返回 null
     */
    private static double[] loadBounds() {
        if (!BOUNDS_FILE.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(BOUNDS_FILE)) {
            Properties p = new Properties();
            p.load(in);
            return new double[]{
                    Double.parseDouble(p.getProperty("x", "0")),
                    Double.parseDouble(p.getProperty("y", "0")),
                    Double.parseDouble(p.getProperty("w", "0")),
                    Double.parseDouble(p.getProperty("h", "0"))
            };
        } catch (Exception e) {
            return null;
        }
    }

    /** 数值夹取：非法值回退到 min */
    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v) || v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    public static void main(String[] args) {
        if (!acquireSingleInstanceLock()) {
            JOptionPane.showMessageDialog(null,
                    "服务管理面板已在运行中。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }
        launch(args);
    }

    private static boolean acquireSingleInstanceLock() {
        try {
            lockSocket = new ServerSocket(SINGLE_INSTANCE_PORT);
            return true;
        } catch (IOException e) {
            // 已有实例在跑：发唤醒信号让旧实例重新弹出浏览器，然后本进程静默退出；
            // 信号也失败才弹提示兜底
            if (sendWakeSignal()) {
                System.exit(0);
            }
            JOptionPane.showMessageDialog(null,
                    "服务管理面板已在运行中。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
            return false;
        }
    }

    /**
     * 唤醒已运行实例：往单实例锁端口发 SHOW 指令，
     * 旧实例收到后会用当前有效地址重新打开浏览器面板
     */
    private static boolean sendWakeSignal() {
        try (Socket s = new Socket("127.0.0.1", SINGLE_INSTANCE_PORT);
             OutputStream out = s.getOutputStream()) {
            out.write("SHOW".getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 常驻监听锁端口：收到 SHOW 即在 FX 线程重新弹出浏览器
     */
    private static void startWakeListener(MainWindow mainWindow) {
        Thread t = new Thread(() -> {
            while (lockSocket != null && !lockSocket.isClosed()) {
                try (Socket s = lockSocket.accept()) {
                    // 任何连接即视为唤醒信号（锁端口只有本程序启动器会碰），
                    // 免去读字节比对，秒级响应
                    Platform.runLater(mainWindow::showInBrowser);
                } catch (IOException e) {
                    break; // 退出时锁端口被关闭
                }
            }
        }, "single-instance-wake");
        t.setDaemon(true);
        t.start();
    }
}
