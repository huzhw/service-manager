package com.servicemanager;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.servicemanager.config.ServiceConfig;
import com.servicemanager.model.ServiceInfo;
import com.servicemanager.ui.MainFrame;
import com.servicemanager.ui.TrayManager;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

/**
 * 程序入口
 */
public class App {

    /** 单实例锁端口 */
    private static final int SINGLE_INSTANCE_PORT = 19953;

    public static void main(String[] args) {
        // 单实例检查
        if (!acquireSingleInstanceLock()) {
            JOptionPane.showMessageDialog(null,
                    "服务管理面板已在运行中，请查看系统托盘图标。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }

        // 设置 FlatLaf 主题
        try {
            UIManager.setLookAndFeel(new FlatIntelliJLaf());
        } catch (Exception e) {
            // 降级到系统默认 LAF
            System.err.println("FlatLaf 加载失败，使用默认主题: " + e.getMessage());
        }

        // 加载服务配置
        List<ServiceInfo> services = ServiceConfig.buildServices();

        // 启动 UI
        SwingUtilities.invokeLater(() -> {
            // 创建主面板
            MainFrame mainFrame = new MainFrame(services);
            mainFrame.setVisible(true);

            // 创建系统托盘
            TrayManager trayManager = new TrayManager(mainFrame);
        });
    }

    /**
     * 通过 ServerSocket 占端口实现单实例锁
     */
    private static boolean acquireSingleInstanceLock() {
        try {
            ServerSocket socket = new ServerSocket(SINGLE_INSTANCE_PORT);
            // 不关闭 socket，保持占用直到进程退出
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
