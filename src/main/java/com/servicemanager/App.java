package com.servicemanager;

import com.servicemanager.config.ServiceConfigLoader;
import com.servicemanager.model.ServiceInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.List;

/**
 * JavaFX 入口 — Service Manager 2.0
 */
public class App extends Application {

    private static final int SINGLE_INSTANCE_PORT = 19953;
    private static ServerSocket lockSocket;

    private List<ServiceInfo> services;

    @Override
    public void start(Stage stage) {
        // 加载服务配置
        services = ServiceConfigLoader.load();

        // 主窗口
        MainWindow mainWindow = new MainWindow(services, stage);

        // Scene
        Scene scene = new Scene(mainWindow, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("服务管理面板 2.0");

        // 应用图标
        try (InputStream is = getClass().getResourceAsStream("/icon.png")) {
            if (is != null) stage.getIcons().add(new Image(is));
        } catch (Exception e) { /* ignore */ }

        // 系统托盘（用 Swing 互操作）
        Platform.setImplicitExit(false);
        SwingUtilities.invokeLater(() -> {
            com.servicemanager.ui.TrayManager tray = new com.servicemanager.ui.TrayManager(mainWindow);
        });

        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();
    }

    @Override
    public void stop() {
        if (lockSocket != null) {
            try { lockSocket.close(); } catch (IOException e) { }
        }
        Platform.exit();
        System.exit(0);
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
            return false;
        }
    }
}
