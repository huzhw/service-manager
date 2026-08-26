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
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * 主窗口 — WebView 壳
 * <p>
 * 旧 JavaFX 面板整体退役：窗口内嵌 WebView 加载 React 前端，
 * 前后端通过 127.0.0.1 内嵌 HTTP 服务通信。托盘能力保留。
 */
public class MainWindow extends BorderPane {

    private final EmbeddedServer server = new EmbeddedServer();

    public MainWindow(List<ServiceInfo> services, Stage stage) {
        // 日志系统：双写日志文件 + LogBus（SSE 实时推给前端）
        LogManager.init(LogBus.get()::publish);

        // 业务编排：装载服务、立即刷新、启动 30s 定时刷新
        ServiceOrchestrator.get().init(services);

        // 启动内嵌 HTTP 服务并让 WebView 加载前端
        try {
            server.start();
            LogManager.log("Web 服务已启动，访问地址: " + server.url());
            WebView webView = new WebView();
            // 渲染自检：加载成功后安装全局错误捕获，随后分两次采样
            // （字符集 / 脚本清单 / 资源加载量 / root 挂载情况 / 运行时错误）
            webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldS, newS) -> {
                if (newS == javafx.concurrent.Worker.State.SUCCEEDED) {
                    try {
                        webView.getEngine().executeScript(
                                "window.__errs=[];window.onerror=function(m,s,l,c){"
                                        + "window.__errs.push(m+' @'+(s||'').split('/').pop()+':'+l);};");
                        Object info1 = webView.getEngine().executeScript(
                                "document.characterSet + ' | scripts=' + document.scripts.length"
                                        + " + ' | res=' + (function(){try{return performance.getEntriesByType("
                                        + "'resource').map(function(r){return r.name.split('/').pop()+':'+r.transferSize}).join(',')}"
                                        + "catch(e){return 'n/a'}})()");
                        LogManager.log("WebView 自检①: " + info1);
                        // 3 秒后二次采样：给异步挂载留时间
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ignored) {
                            }
                            javafx.application.Platform.runLater(() -> {
                                try {
                                    Object info2 = webView.getEngine().executeScript(
                                            "'root=' + (function(){var r=document.getElementById('root');"
                                                    + "return r ? r.childElementCount : 'none'})()"
                                                    + " + ' | brand=' + encodeURIComponent((function(){"
                                                    + "var b=document.querySelector('.brand-text');"
                                                    + "return b ? b.textContent : 'none'})()).substring(0,30)"
                                                    + " + ' | errs=' + window.__errs.join(' ;; ')");
                                    LogManager.log("WebView 自检②: " + info2);
                                    // 自检③：canvas 字形像素比对——判断中文字体是否真的渲染出来
                                    Object info3 = webView.getEngine().executeScript(
                                            "(function(){function bmp(ch){var c=document.createElement('canvas');"
                                                    + "c.width=80;c.height=80;var x=c.getContext('2d');"
                                                    + "x.font='60px Microsoft YaHei';x.fillStyle='#fff';"
                                                    + "x.fillRect(0,0,80,80);x.fillStyle='#000';x.fillText(ch,10,65);"
                                                    + "return x.getImageData(0,0,80,80).data;}"
                                                    + "var d1=bmp('\\u670D'),d2=bmp('\\u7BA1'),diff=0,dark1=0;"
                                                    + "for(var i=0;i<d1.length;i+=4){if(d1[i]<120){dark1++;"
                                                    + "if(Math.abs(d1[i]-d2[i])>30)diff++;}}"
                                                    + "return 'dark=' + dark1 + ' diff=' + diff;})()");
                                    LogManager.log("WebView 自检③(字形像素): " + info3);
                                } catch (Exception e) {
                                    LogManager.log("WebView 自检②失败: " + e.getMessage());
                                }
                            });
                        }, "webview-probe").start();
                    } catch (Exception e) {
                        LogManager.log("WebView 自检①失败: " + e.getMessage());
                    }
                }
            });
            webView.getEngine().load(server.url());
            setCenter(webView);
        } catch (IOException e) {
            LogManager.log("✗ 内嵌 Web 服务启动失败: " + e.getMessage());
            setCenter(errorBox("内嵌 Web 服务启动失败", e.getMessage()));
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
