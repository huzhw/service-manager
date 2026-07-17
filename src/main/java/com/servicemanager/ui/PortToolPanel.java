package com.servicemanager.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * 端口工具面板 — JavaFX 实现
 * 查找端口占用进程并强制终止
 */
public class PortToolPanel extends VBox {

    private final Consumer<String> logger;

    private final TextField portInput;
    private final Button findBtn;
    private final Button killBtn;
    private final Label resultLabel;

    private String currentPid;
    private String currentProcName;

    /** 常用端口列表 */
    private static final int[] COMMON_PORTS = {
            3000, 3001, 8051, 8021, 8087, 8095, 8091, 8080, 8000, 8686, 10535
    };

    public PortToolPanel(Consumer<String> logger) {
        this.logger = logger;

        setSpacing(16);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #f0f2f5;");

        // ====== 自定义端口查找 ======
        VBox customPanel = createCard("🔍 查找端口");

        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.getChildren().add(new Label("端口号："));

        portInput = new TextField();
        portInput.setPrefWidth(100);
        inputRow.getChildren().add(portInput);

        findBtn = new Button("查找");
        findBtn.getStyleClass().addAll("port-btn", "primary");
        killBtn = new Button("终止进程");
        killBtn.getStyleClass().addAll("port-btn", "danger");
        killBtn.setDisable(true);
        inputRow.getChildren().addAll(findBtn, killBtn);

        resultLabel = new Label(" ");
        resultLabel.setStyle("-fx-font-size: 13px;");

        customPanel.getChildren().addAll(inputRow, resultLabel);
        getChildren().add(customPanel);

        // ====== 常用端口快捷区 ======
        VBox quickPanel = createCard("⚡ 常用端口快捷查杀");
        FlowPane chipsPanel = new FlowPane(6, 6);
        chipsPanel.setPrefWrapLength(560);

        for (int port : COMMON_PORTS) {
            Button chip = new Button(String.valueOf(port));
            chip.getStyleClass().add("port-chip");
            chip.setTooltip(new Tooltip("查找并终止端口 " + port));
            chip.setOnAction(e -> quickKill(port, chip));
            chipsPanel.getChildren().add(chip);
        }
        quickPanel.getChildren().add(chipsPanel);
        getChildren().add(quickPanel);

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(spacer);

        // ---- 事件 ----
        findBtn.setOnAction(e -> findPort());
        killBtn.setOnAction(e -> killPort());
        portInput.setOnAction(e -> findPort());
    }

    // ==========================================
    //  卡片骨架
    // ==========================================
    private VBox createCard(String title) {
        VBox card = new VBox(10);
        card.getStyleClass().add("port-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        card.getChildren().add(titleLabel);
        return card;
    }

    // ==========================================
    //  端口查找
    // ==========================================
    private void findPort() {
        String portStr = portInput.getText().trim();
        if (portStr.isEmpty()) return;

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            resultLabel.setText("请输入有效端口号");
            resultLabel.getStyleClass().setAll("port-result-danger");
            return;
        }

        currentPid = null;
        currentProcName = null;
        killBtn.setDisable(true);
        resultLabel.setText("检测中...");
        resultLabel.getStyleClass().removeAll("port-result-danger", "port-result-ok");

        new Thread(() -> {
            String pid = findPidByPort(port);
            if (pid != null) {
                currentPid = pid;
                currentProcName = findProcName(pid);
                String text = "⚠ 端口 " + port + " 被占用 — PID: " + pid
                        + (currentProcName != null ? " (" + currentProcName + ")" : "");
                Platform.runLater(() -> {
                    resultLabel.setText(text);
                    resultLabel.getStyleClass().setAll("port-result-danger");
                    killBtn.setDisable(false);
                });
                logger.accept("发现端口 " + port + " 被 PID " + pid + " 占用");
            } else {
                Platform.runLater(() -> {
                    resultLabel.setText("✓ 端口 " + port + " 未被占用");
                    resultLabel.getStyleClass().setAll("port-result-ok");
                    killBtn.setDisable(true);
                });
            }
        }).start();
    }

    private void killPort() {
        if (currentPid == null) return;
        new Thread(() -> {
            logger.accept("→ 强制终止 PID " + currentPid
                    + (currentProcName != null ? " (" + currentProcName + ")" : "") + " ...");
            String output = exec("taskkill /F /PID " + currentPid);
            if (output != null && output.contains("成功")) {
                logger.accept("  ✓ 已终止 PID " + currentPid);
            } else {
                logger.accept("  ✗ 终止失败: " + (output != null ? output.trim() : "无输出"));
            }
            Platform.runLater(() -> findPort());
        }).start();
    }

    private void quickKill(int port, Button chip) {
        chip.setDisable(true);
        new Thread(() -> {
            String pid = findPidByPort(port);
            if (pid != null) {
                String procName = findProcName(pid);
                logger.accept("→ 端口 " + port + " → PID " + pid
                        + (procName != null ? " (" + procName + ")" : "") + " → taskkill ...");
                String output = exec("taskkill /F /PID " + pid);
                if (output != null && output.contains("成功")) {
                    logger.accept("  ✓ 端口 " + port + " 已释放");
                } else {
                    logger.accept("  ✗ 端口 " + port + " 终止失败");
                }
            } else {
                logger.accept("  ✓ 端口 " + port + " 未被占用");
            }
            Platform.runLater(() -> chip.setDisable(false));
        }).start();
    }

    // ==========================================
    //  命令工具
    // ==========================================
    private String findPidByPort(int port) {
        String output = exec("netstat -ano | findstr \":" + port + " \" | findstr \"LISTENING\"");
        if (output != null) {
            for (String line : output.split("\n")) {
                line = line.trim();
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    return parts[parts.length - 1];
                }
            }
        }
        return null;
    }

    private String findProcName(String pid) {
        String output = exec("tasklist /FI \"PID eq " + pid + "\" /NH");
        if (output != null) {
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.contains(pid)) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 1) {
                        return parts[0];
                    }
                }
            }
        }
        return null;
    }

    private String exec(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
