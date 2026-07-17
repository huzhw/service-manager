package com.servicemanager;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.service.ProcessController;
import com.servicemanager.service.WindowsServiceController;
import com.servicemanager.ui.VersionPanel;
import com.servicemanager.ui.PortToolPanel;
import com.servicemanager.ui.FileAssocPanel;
import com.servicemanager.util.LogManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.swing.*;
import java.util.List;

/**
 * 主窗口 — 侧边栏导航 + 内容区
 */
public class MainWindow extends AnchorPane {

    private final List<ServiceInfo> services;
    private final Stage stage;
    private final StackPane contentArea;
    private final Label statusLabel;
    private final Label refreshLabel;

    private ServiceTable serviceTable;
    private VBox sidebar;
    private Button activeNavBtn;

    private final WindowsServiceController winCtrl = new WindowsServiceController();
    private final ProcessController procCtrl = new ProcessController();
    private Timeline refreshTimer;

    public MainWindow(List<ServiceInfo> services, Stage stage) {
        this.services = services;
        this.stage = stage;

        setStyle("-fx-background-color: #f0f2f5;");

        // Sidebar
        sidebar = buildSidebar();
        getChildren().add(sidebar);
        AnchorPane.setTopAnchor(sidebar, 0.0);
        AnchorPane.setLeftAnchor(sidebar, 0.0);
        AnchorPane.setBottomAnchor(sidebar, 0.0);

        // Content
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #f0f2f5;");
        getChildren().add(contentArea);
        AnchorPane.setTopAnchor(contentArea, 0.0);
        AnchorPane.setLeftAnchor(contentArea, 210.0);
        AnchorPane.setRightAnchor(contentArea, 0.0);
        AnchorPane.setBottomAnchor(contentArea, 0.0);

        // Bottom bar
        HBox bottomBar = new HBox();
        bottomBar.getStyleClass().add("status-bar");

        statusLabel = new Label("就绪");
        statusLabel.getStyleClass().add("status-label");

        refreshLabel = new Label("");
        refreshLabel.getStyleClass().add("status-label");

        Button logBtn = new Button("📋 日志");
        logBtn.getStyleClass().addAll("toolbar-btn", "secondary");
        logBtn.setOnAction(e -> toggleLog());

        HBox rightBox = new HBox(10, refreshLabel, logBtn);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        bottomBar.getChildren().addAll(statusLabel, rightBox);

        // Log panel (hidden by default)
        logPanel = new StackPane();
        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logPanel.getChildren().add(logArea);
        logPanel.setVisible(false);

        VBox bottomArea = new VBox(0, logPanel, bottomBar);
        getChildren().add(bottomArea);
        AnchorPane.setLeftAnchor(bottomArea, 210.0);
        AnchorPane.setRightAnchor(bottomArea, 0.0);
        AnchorPane.setBottomAnchor(bottomArea, 0.0);

        // Init log system
        LogManager.init(this::appendLogUi);

        // Show service tab + initial refresh
        showServiceTab();
        refreshAllStatus();

        // Auto refresh every 30s
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshAllStatus()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private StackPane logPanel;
    private TextArea logArea;

    private VBox buildSidebar() {
        VBox nav = new VBox();
        nav.getStyleClass().add("sidebar");
        nav.setFillWidth(true);

        Label title = new Label("⚙  服务管理面板");
        title.getStyleClass().add("sidebar-title");
        nav.getChildren().add(title);

        String[][] items = {
                {"🖥  服务管理", "service"},
                {"📦  版本管理", "version"},
                {"🔌  端口工具", "port"},
                {"📄  文件关联", "fileassoc"},
        };

        for (String[] item : items) {
            Button btn = new Button(item[0]);
            btn.getStyleClass().add("sidebar-btn");
            btn.setOnAction(e -> {
                if (activeNavBtn != null) {
                    activeNavBtn.getStyleClass().remove("selected");
                }
                btn.getStyleClass().add("selected");
                activeNavBtn = btn;

                switch (item[1]) {
                    case "service": showServiceTab(); break;
                    case "version": showVersionTab(); break;
                    case "port": showPortTab(); break;
                    case "fileassoc": showFileAssocTab(); break;
                }
            });
            nav.getChildren().add(btn);

            if (item[1].equals("service")) {
                btn.getStyleClass().add("selected");
                activeNavBtn = btn;
            }
        }

        // Spacer to push buttons to top
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        nav.getChildren().add(spacer);

        return nav;
    }

    // ==========================================
    //  Tab switching
    // ==========================================

    private void showServiceTab() {
        if (serviceTable == null) {
            serviceTable = new ServiceTable(services, this);
        }
        // Toolbar
        Button startAllBtn = new Button("▶  全部启动");
        startAllBtn.getStyleClass().addAll("toolbar-btn", "primary");
        startAllBtn.setOnAction(e -> startAllServices());

        Button stopAllBtn = new Button("■  全部停止");
        stopAllBtn.getStyleClass().addAll("toolbar-btn", "danger");
        stopAllBtn.setOnAction(e -> stopAllServices());

        Button refreshBtn = new Button("↻  刷新");
        refreshBtn.getStyleClass().addAll("toolbar-btn", "secondary");
        refreshBtn.setOnAction(e -> refreshAllStatus());

        HBox toolbar = new HBox(10, startAllBtn, stopAllBtn, refreshBtn);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(serviceTable, Priority.ALWAYS);
        VBox panel = new VBox(0, toolbar, serviceTable);
        panel.setStyle("-fx-background-color: #f0f2f5;");
        contentArea.getChildren().setAll(panel);
    }

    private void showVersionTab() {
        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            VersionPanel vp = new VersionPanel(LogManager::log);
            node.setContent(vp);
        });
        contentArea.getChildren().setAll(new ScrollPane(node));
    }

    private void showPortTab() {
        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            PortToolPanel pp = new PortToolPanel(LogManager::log);
            node.setContent(pp);
        });
        contentArea.getChildren().setAll(new ScrollPane(node));
    }

    private void showFileAssocTab() {
        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            FileAssocPanel fp = new FileAssocPanel(LogManager::log);
            node.setContent(fp);
        });
        contentArea.getChildren().setAll(new ScrollPane(node));
    }

    // ==========================================
    //  Service actions
    // ==========================================

    void refreshAllStatus() {
        new Thread(() -> {
            int runningCount = 0;
            for (ServiceInfo svc : services) {
                com.servicemanager.service.ServiceController ctrl =
                        svc.getType() == com.servicemanager.model.ServiceType.WINDOWS_SERVICE
                                ? winCtrl : procCtrl;
                String status = ctrl.getStatus(svc);
                if ("RUNNING".equals(status) && svc.getPort() > 0) {
                    if (!com.servicemanager.util.PortChecker.isPortOpen(svc.getPort())) {
                        status = "PORT_UNREACHABLE";
                    }
                }
                svc.setStatus(status);
                if ("RUNNING".equals(status)) runningCount++;
            }
            final int count = runningCount;
            Platform.runLater(() -> {
                if (serviceTable != null) serviceTable.refreshTable();
                statusLabel.setText("共 " + services.size() + " 个服务，运行中 " + count + " 个");
                refreshLabel.setText("刷新: " + java.time.LocalTime.now().toString().substring(0, 8));
            });
        }).start();
    }

    public void startAllServices() {
        new Thread(() -> {
            appendLog("→ 批量启动...");
            List<ServiceInfo> sorted = services.stream()
                    .sorted(java.util.Comparator.comparingInt(ServiceInfo::getStartOrder))
                    .collect(java.util.stream.Collectors.toList());
            int ok = 0, fail = 0;
            for (ServiceInfo svc : sorted) {
                if ("RUNNING".equals(svc.getStatus())) continue;
                appendLog("→ 启动 " + svc.getName() + " ...");
                com.servicemanager.service.ServiceController ctrl =
                        svc.getType() == com.servicemanager.model.ServiceType.WINDOWS_SERVICE
                                ? winCtrl : procCtrl;
                boolean result = ctrl.start(svc);
                if (result) {
                    svc.setStatus("RUNNING");
                    svc.setStartTime(System.currentTimeMillis());
                    appendLog("  ✓ " + svc.getName() + " 启动成功");
                    ok++;
                } else {
                    appendLog("  ✗ " + svc.getName() + " 失败");
                    fail++;
                }
                Platform.runLater(() -> { if (serviceTable != null) serviceTable.refreshTable(); });
                try { Thread.sleep(1500); } catch (InterruptedException e) { break; }
            }
            appendLog("批量完成: 成功 " + ok + ", 失败 " + fail);
            refreshAllStatus();
        }).start();
    }

    public void stopAllServices() {
        new Thread(() -> {
            appendLog("← 批量停止...");
            List<ServiceInfo> sorted = services.stream()
                    .sorted(java.util.Comparator.comparingInt(ServiceInfo::getStopOrder).reversed())
                    .collect(java.util.stream.Collectors.toList());
            int ok = 0, fail = 0;
            for (ServiceInfo svc : sorted) {
                if ("STOPPED".equals(svc.getStatus())) continue;
                appendLog("← 停止 " + svc.getName() + " ...");
                com.servicemanager.service.ServiceController ctrl =
                        svc.getType() == com.servicemanager.model.ServiceType.WINDOWS_SERVICE
                                ? winCtrl : procCtrl;
                boolean result = ctrl.stop(svc);
                if (result) {
                    svc.setStatus("STOPPED");
                    svc.setPid(0);
                    svc.setStartTime(0);
                    appendLog("  ✓ " + svc.getName() + " stopped");
                    ok++;
                } else {
                    appendLog("  ✗ " + svc.getName() + " 失败");
                    fail++;
                }
                Platform.runLater(() -> { if (serviceTable != null) serviceTable.refreshTable(); });
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
            appendLog("批量完成: 成功 " + ok + ", 失败 " + fail);
            refreshAllStatus();
        }).start();
    }

    void triggerSingleAction(ServiceInfo svc, boolean stopping) {
        new Thread(() -> {
            List<ServiceInfo> group = getGroupMembers(svc);
            if (stopping) {
                group.sort(java.util.Comparator.comparingInt(ServiceInfo::getStopOrder));
            } else {
                group.sort(java.util.Comparator.comparingInt(ServiceInfo::getStartOrder));
            }
            for (ServiceInfo member : group) {
                com.servicemanager.service.ServiceController ctrl =
                        member.getType() == com.servicemanager.model.ServiceType.WINDOWS_SERVICE
                                ? winCtrl : procCtrl;
                if (stopping) {
                    if ("STOPPED".equals(member.getStatus())) continue;
                    member.setStatus("STOPPING");
                    Platform.runLater(() -> { if (serviceTable != null) serviceTable.refreshTable(); });
                    appendLog("← 停止 " + member.getName() + " ...");
                    boolean ok = ctrl.stop(member);
                    if (ok) member.setStartTime(0);
                    appendLog(ok ? "  ✓ " + member.getName() + " 已停止"
                            : "  ✗ " + member.getName() + " 失败");
                } else {
                    if ("RUNNING".equals(member.getStatus())) continue;
                    member.setStatus("STARTING");
                    Platform.runLater(() -> { if (serviceTable != null) serviceTable.refreshTable(); });
                    appendLog("→ 启动 " + member.getName() + " ...");
                    boolean ok = ctrl.start(member);
                    if (ok) member.setStartTime(System.currentTimeMillis());
                    appendLog(ok ? "  ✓ " + member.getName() + " 启动成功"
                            : "  ✗ " + member.getName() + " 失败");
                }
                Platform.runLater(() -> { if (serviceTable != null) serviceTable.refreshTable(); });
                try { Thread.sleep(800); } catch (InterruptedException e) { return; }
            }
            refreshAllStatus();
        }).start();
    }

    private List<ServiceInfo> getGroupMembers(ServiceInfo svc) {
        String group = svc.getGroupName();
        if (group == null || group.isEmpty()) {
            List<ServiceInfo> self = new java.util.ArrayList<>();
            self.add(svc);
            return self;
        }
        List<ServiceInfo> members = new java.util.ArrayList<>();
        for (ServiceInfo s : services) {
            if (group.equals(s.getGroupName())) members.add(s);
        }
        return members;
    }

    private void toggleLog() {
        boolean visible = !logPanel.isVisible();
        logPanel.setVisible(visible);
        if (visible) {
            logArea.clear();
        }
    }

    private void appendLog(String msg) {
        String time = java.time.LocalTime.now().toString().substring(0, 8);
        final String line = time + "  " + msg + "\n";
        appendLogUi(line);
    }

    private void appendLogUi(String line) {
        Platform.runLater(() -> {
            logArea.appendText(line);
        });
    }
}
