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
    private final java.util.Map<Integer, Button> chipMap = new java.util.LinkedHashMap<>();

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

        Button killAllBtn = new Button("🗑 一键全部杀掉");
        killAllBtn.getStyleClass().addAll("port-btn", "danger");
        killAllBtn.setStyle(killAllBtn.getStyle() + "-fx-font-size: 13px;");

        Label batchResultLabel = new Label("");
        batchResultLabel.setStyle("-fx-font-size: 12px; -fx-padding: 4 0 0 0;");

        killAllBtn.setOnAction(e -> killAllCommonPorts(killAllBtn, batchResultLabel));

        FlowPane chipsPanel = new FlowPane(6, 6);
        chipsPanel.setPrefWrapLength(560);

        for (int port : COMMON_PORTS) {
            Button chip = new Button(String.valueOf(port));
            chip.getStyleClass().add("port-chip");
            chip.setTooltip(new Tooltip("查找并终止端口 " + port));
            chip.setOnAction(e -> quickKill(port, chip));
            chipsPanel.getChildren().add(chip);
            chipMap.put(port, chip);
        }
        quickPanel.getChildren().addAll(killAllBtn, batchResultLabel, chipsPanel);
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

    /**
     * 一键杀掉所有常用端口占用的进程，实时显示进度
     */
    private void killAllCommonPorts(Button btn, Label statusLabel) {
        btn.setDisable(true);
        new Thread(() -> {
            int killed = 0, clean = 0;
            for (int i = 0; i < COMMON_PORTS.length; i++) {
                int port = COMMON_PORTS[i];
                final int progress = i + 1;
                Platform.runLater(() ->
                        statusLabel.setText("查杀中 " + progress + "/" + COMMON_PORTS.length + " — 端口 " + port + " ..."));

                String pid = findPidByPort(port);
                if (pid != null) {
                    String procName = findProcName(pid);
                    logger.accept("→ 端口 " + port + " → PID " + pid
                            + (procName != null ? " (" + procName + ")" : "") + " → taskkill ...");
                    String output = exec("taskkill /F /PID " + pid);
                    if (output != null && output.contains("成功")) {
                        logger.accept("  ✓ 端口 " + port + " 已释放");
                        killed++;
                        Platform.runLater(() -> {
                            Button chip = chipMap.get(port);
                            if (chip != null) {
                                chip.setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #2e7d32;"
                                        + " -fx-font-size: 11px; -fx-padding: 4 12 4 12;"
                                        + " -fx-background-radius: 10; -fx-border-color: #a5d6a7;"
                                        + " -fx-border-width: 1; -fx-border-radius: 10;");
                                chip.setText("✓ " + port);
                            }
                        });
                    } else {
                        logger.accept("  ✗ 端口 " + port + " 终止失败");
                    }
                } else {
                    clean++;
                    Platform.runLater(() -> {
                        Button chip = chipMap.get(port);
                        if (chip != null) {
                            chip.setStyle("-fx-background-color: #e8e8e8; -fx-text-fill: #9e9e9e;"
                                    + " -fx-font-size: 11px; -fx-padding: 4 12 4 12;"
                                    + " -fx-background-radius: 10; -fx-border-color: #e0e0e0;"
                                    + " -fx-border-width: 1; -fx-border-radius: 10;");
                        }
                    });
                }
            }
            final int done = killed, free = clean;
            Platform.runLater(() -> {
                btn.setDisable(false);
                if (done > 0) {
                    statusLabel.setText("✓ 已释放 " + done + " 个端口" + (free > 0 ? "，" + free + " 个原本空闲" : ""));
                    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4caf50; -fx-padding: 4 0 0 0;");
                } else {
                    statusLabel.setText("全部 " + free + " 个端口均未被占用");
                    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #909399; -fx-padding: 4 0 0 0;");
                }
            });
            logger.accept("✓ 批量查杀完成: 已释放 " + done + " 个, 原本空闲 " + free + " 个");
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
