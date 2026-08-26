package com.servicemanager;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.service.ServiceOrchestrator;
import com.servicemanager.util.LogBus;
import com.servicemanager.util.LogManager;
import com.servicemanager.web.EmbeddedServer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * 主窗口 — 浏览器承载壳
 * <p>
 * 内嵌 WebView 已退役（老 WebView 渲染正常但鼠标事件无法进入页面）：
 * 本类只负责启动内嵌 HTTP 服务，并用系统默认浏览器打开面板页面。
 * 再次双击桌面快捷方式时由 App 的单实例唤醒通道回调 {@link #showInBrowser()} 重新弹出浏览器。
 */
public class MainWindow extends BorderPane {

    private final EmbeddedServer server = new EmbeddedServer();

    public MainWindow(List<ServiceInfo> services, Stage stage) {
        // 日志系统：双写日志文件 + LogBus（SSE 实时推给前端）
        LogManager.init(LogBus.get()::publish);

        // 业务编排：装载服务、立即刷新、启动 30s 定时刷新
        ServiceOrchestrator.get().init(services);

        // 启动内嵌 HTTP 服务并用系统浏览器打开面板
        try {
            server.start();
            LogManager.log("Web 服务已启动，访问地址: " + server.url());
            showInBrowser();
            setCenter(infoBox());
        } catch (IOException e) {
            LogManager.log("✗ 内嵌 Web 服务启动失败: " + e.getMessage());
            setCenter(errorBox("内嵌 Web 服务启动失败", e.getMessage()));
        }
    }

    /**
     * 用系统默认浏览器打开面板（带 token 完整地址）
     * <p>
     * 调起链二级回退：Desktop.browse（标准 API，正确处理带查询串的 URL）
     * → cmd start 兜底 → 日志留址手动访问。
     */
    public void showInBrowser() {
        String url = server.url();
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            LogManager.log("已在浏览器打开面板: " + url);
            return;
        } catch (Exception e) {
            LogManager.log("Desktop.browse 打开失败，回退 cmd start: " + e.getMessage());
        }
        try {
            // start 后的空串是窗口标题占位，防止 URL 被当成标题吞掉
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
            LogManager.log("已在浏览器打开面板: " + url);
        } catch (Exception e) {
            LogManager.log("✗ 浏览器打开失败，请手动访问: " + url);
        }
    }

    /**
     * 应用退出前释放资源（服务器、定时器）
     */
    public void shutdown() {
        server.stop();
        ServiceOrchestrator.get().shutdown();
    }

    // ==========================================
    //  托盘菜单入口（保持原方法签名供 TrayManager 调用）
    // ==========================================

    public void startAllServices() {
        if (!ServiceOrchestrator.get().startAll()) {
            LogManager.log("⚠ 已有批量任务进行中，忽略本次全部启动请求");
        }
    }

    public void stopAllServices() {
        if (!ServiceOrchestrator.get().stopAll()) {
            LogManager.log("⚠ 已有批量任务进行中，忽略本次全部停止请求");
        }
    }

    // ==========================================
    //  内部工具
    // ==========================================

    /**
     * 正常提示页：告知用户面板在浏览器中
     */
    private VBox infoBox() {
        Label titleLabel = new Label("服务管理面板已在浏览器中打开");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #69db7c; -fx-font-weight: bold;");
        Label detailLabel = new Label(
                "本窗口可以关闭，程序驻留托盘运行；\n再次双击桌面快捷方式会重新弹出浏览器。");
        detailLabel.setStyle("-fx-text-fill: #909399;");
        VBox box = new VBox(12, titleLabel, detailLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * 启动失败时的兜底提示框
     */
    private VBox errorBox(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #f56c6c; -fx-font-weight: bold;");
        Label detailLabel = new Label(detail);
        detailLabel.setStyle("-fx-text-fill: #909399;");
        VBox box = new VBox(12, titleLabel, detailLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
