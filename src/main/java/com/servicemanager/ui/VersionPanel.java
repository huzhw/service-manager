package com.servicemanager.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 版本管理面板 — JavaFX 实现
 * nvm (Node.js) + pyenv (Python) 版本查看、切换、安装
 */
public class VersionPanel extends VBox {

    private final Consumer<String> logger;

    // ---- Node.js UI ----
    private final Label nodeCurrentLabel;
    private final FlowPane nodeVersionFlow;
    private final Button nodeRefreshBtn;
    private final Button nodeInstallBtn;

    // ---- Python UI ----
    private final Label pythonCurrentLabel;
    private final FlowPane pythonVersionFlow;
    private final Button pythonRefreshBtn;
    private final Button pythonInstallBtn;

    public VersionPanel(Consumer<String> logger) {
        this.logger = logger;

        setSpacing(16);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #f0f2f5;");

        // ====== Node.js 卡片 ======
        VBox nodeCard = createCard("🟢 Node.js (nvm)");
        nodeCurrentLabel = new Label("检测中...");
        nodeCurrentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        nodeVersionFlow = new FlowPane(8, 4);
        nodeVersionFlow.setPrefWrapLength(560);
        nodeRefreshBtn = new Button("↻ 刷新");
        nodeInstallBtn = new Button("+ 安装版本");

        assembleCard(nodeCard, nodeCurrentLabel, nodeVersionFlow, nodeRefreshBtn, nodeInstallBtn);
        getChildren().add(nodeCard);

        // ====== Python 卡片 ======
        VBox pythonCard = createCard("🐍 Python (pyenv)");
        pythonCurrentLabel = new Label("检测中...");
        pythonCurrentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        pythonVersionFlow = new FlowPane(8, 4);
        pythonVersionFlow.setPrefWrapLength(560);
        pythonRefreshBtn = new Button("↻ 刷新");
        pythonInstallBtn = new Button("+ 安装版本");

        assembleCard(pythonCard, pythonCurrentLabel, pythonVersionFlow, pythonRefreshBtn, pythonInstallBtn);
        getChildren().add(pythonCard);

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(spacer);

        // ---- 事件 ----
        nodeRefreshBtn.setOnAction(e -> refreshNodeVersions());
        nodeInstallBtn.setOnAction(e -> installVersion("Node.js", "nvm", "nvm install %s", this::refreshNodeVersions));
        pythonRefreshBtn.setOnAction(e -> refreshPythonVersions());
        pythonInstallBtn.setOnAction(e -> installVersion("Python", "pyenv", "pyenv install %s", this::refreshPythonVersions));

        // 首次加载
        refreshNodeVersions();
        refreshPythonVersions();
    }

    // ==========================================
    //  卡片骨架
    // ==========================================
    private VBox createCard(String title) {
        VBox card = new VBox(12);
        card.getStyleClass().add("version-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        card.getChildren().add(titleLabel);
        return card;
    }

    private void assembleCard(VBox card, Label currentLabel, FlowPane versionFlow,
                              Button refreshBtn, Button installBtn) {
        // 中间：当前版本 + 已安装版本列表
        VBox center = new VBox(8);
        center.getChildren().addAll(currentLabel, versionFlow);

        // 底部按钮
        HBox bottom = new HBox(8);
        bottom.setAlignment(Pos.CENTER_LEFT);
        installBtn.getStyleClass().addAll("port-btn", "primary");
        refreshBtn.getStyleClass().addAll("port-btn", "secondary");
        bottom.getChildren().addAll(installBtn, refreshBtn);

        card.getChildren().addAll(center, bottom);
    }

    // ==========================================
    //  Node.js 版本管理
    // ==========================================
    private static String getNvmRoot() {
        File settingsFile = new File("F:\\nvm\\settings.txt");
        if (settingsFile.exists()) {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(settingsFile), "GBK"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("root:")) {
                        return line.substring(5).trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        String home = System.getenv("NVM_HOME");
        return (home != null) ? home : "F:\\nvm";
    }

    private void refreshNodeVersions() {
        new Thread(() -> {
            String current = execReadLine("node --version");
            Platform.runLater(() ->
                    nodeCurrentLabel.setText("当前版本: " + (current != null ? current : "未检测到")));

            List<String> versions = listNvmVersions();
            final String active = current != null ? current.replace("v", "") : "";
            Platform.runLater(() -> buildVersionChips(nodeVersionFlow, versions, active,
                    v -> switchNodeVersion(v)));
        }).start();
    }

    private List<String> listNvmVersions() {
        List<String> list = new ArrayList<>();
        File nvmRoot = new File(getNvmRoot());
        File[] dirs = nvmRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("v"));
        if (dirs != null) {
            for (File d : dirs) {
                String name = d.getName();
                if (name.startsWith("v")) {
                    list.add(name.substring(1));
                }
            }
        }
        list.sort((a, b) -> compareVersions(b, a));
        return list;
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private void switchNodeVersion(String version) {
        new Thread(() -> {
            logger.accept("→ 切换 Node.js 到 " + version + " ...");
            exec("start /min nvm use " + version + " 2>&1");
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            String current = execReadLine("node --version");
            if (current != null && current.contains(version)) {
                logger.accept("  ✓ Node.js 已切换至 " + version);
            } else {
                logger.accept("  ⚠ 切换后当前版本: " + (current != null ? current : "未知")
                        + "，请检查 nvm 是否正常");
            }
            Platform.runLater(this::refreshNodeVersions);
        }).start();
    }

    // ==========================================
    //  Python 版本管理
    // ==========================================
    private void refreshPythonVersions() {
        new Thread(() -> {
            String current = execReadLine("python --version");
            if (current != null && current.toLowerCase().startsWith("python ")) {
                current = current.substring(7).trim();
            }
            final String cur = current;
            Platform.runLater(() ->
                    pythonCurrentLabel.setText("当前版本: " + (cur != null ? cur : "未检测到")));

            List<String> versions = parsePyenvVersions();
            final String active = cur;
            Platform.runLater(() -> buildVersionChips(pythonVersionFlow, versions, active,
                    v -> switchPythonVersion(v)));
        }).start();
    }

    private List<String> parsePyenvVersions() {
        List<String> list = new ArrayList<>();
        try {
            String output = exec("pyenv versions");
            if (output != null) {
                for (String line : output.split("\n")) {
                    line = line.trim();
                    if (line.matches(".*\\d+\\.\\d+\\.\\d+.*")) {
                        String ver = line.replaceAll(".*?(\\d+\\.\\d+\\.\\d+).*", "$1");
                        if (!list.contains(ver)) {
                            list.add(ver);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.accept("✗ pyenv versions 解析失败: " + e.getMessage());
        }
        return list;
    }

    private void switchPythonVersion(String version) {
        new Thread(() -> {
            logger.accept("→ 切换 Python 到 " + version + " ...");
            exec("pyenv global " + version);
            exec("pyenv rehash");
            logger.accept("  ✓ Python 已切换至 " + version);
            Platform.runLater(this::refreshPythonVersions);
        }).start();
    }

    // ==========================================
    //  安装版本（通用）
    // ==========================================
    private void installVersion(String label, String tool, String cmdTemplate, Runnable onDone) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("安装 " + label + " 版本");
        dialog.setHeaderText("输入要安装的 " + label + " 版本号：");
        dialog.setContentText("例如: 18.17.0");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().trim().isEmpty()) {
            return;
        }
        final String ver = result.get().trim();
        new Thread(() -> {
            String cmd = String.format(cmdTemplate, ver);
            logger.accept("→ 执行 " + tool + " " + cmd.replace(tool + " ", "") + " ...");
            logger.accept("  (可能需要几分钟，请耐心等待)");
            String output = exec(cmd);
            if (output != null) {
                for (String line : output.split("\n")) {
                    logger.accept("  " + line.trim());
                }
            }
            logger.accept("✓ " + label + " " + ver + " 安装完成");
            Platform.runLater(onDone);
        }).start();
    }

    // ==========================================
    //  版本标签组件
    // ==========================================
    private void buildVersionChips(FlowPane panel, List<String> versions, String activeVersion,
                                   Consumer<String> onClick) {
        panel.getChildren().clear();
        if (versions.isEmpty()) {
            Label empty = new Label("（未检测到已安装版本）");
            empty.setStyle("-fx-text-fill: #909399; -fx-font-size: 12px;");
            panel.getChildren().add(empty);
        } else {
            for (String v : versions) {
                boolean isActive = v.equals(activeVersion);
                Button chip = new Button(isActive ? "● " + v : v);
                chip.getStyleClass().add("version-chip");
                if (isActive) {
                    chip.getStyleClass().add("active");
                }
                chip.setOnAction(e -> onClick.accept(v));
                panel.getChildren().add(chip);
            }
        }
    }

    // ==========================================
    //  命令执行工具
    // ==========================================
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
            logger.accept("✗ 执行失败 [" + cmd + "]: " + e.getMessage());
            return null;
        }
    }

    private String execReadLine(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line = reader.readLine();
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
