package com.servicemanager.ui;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Docker 管理面板 — JavaFX 实现
 * Docker 引擎启停、人大金仓 Oracle 兼容版管理、容器/镜像列表
 */
public class DockerPanel extends VBox {

    private final Consumer<String> logger;
    private static final Gson GSON = new Gson();

    // Docker 引擎
    private final Circle engineDot;
    private final Label engineLabel;
    private final Button engineStartBtn;
    private final Button engineStopBtn;

    // 人大金仓
    private final Circle kingbaseDot;
    private final Label kingbaseLabel;
    private final Button kingbaseInstallBtn;
    private final Button kingbaseStartBtn;
    private final Button kingbaseStopBtn;
    private final Button kingbaseLogBtn;

    // 其他容器
    private final TableView<ContainerEntry> containerTable;
    private final ObservableList<ContainerEntry> containerList;

    // 镜像
    private final TableView<ImageEntry> imageTable;
    private final ObservableList<ImageEntry> imageList;

    private static final String KINGBASE_NAME = "kingbase";
    private static final String KINGBASE_IMAGE = "yhl452493373/kingbase_v009r001c010b0004_single_x86:v1";
    private static final String DOCKER_DESKTOP_PATH = "F:\\Program Files\\Docker\\Docker\\Docker Desktop.exe";

    public DockerPanel(Consumer<String> logger) {
        this.logger = logger;

        setSpacing(16);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #f0f2f5;");

        // ====== Docker 引擎卡片 ======
        VBox engineCard = createCard("🔧 Docker 引擎");

        engineDot = new Circle(5, Color.GRAY);
        engineLabel = new Label("检测中...");
        engineLabel.setStyle("-fx-font-size: 13px;");

        engineStartBtn = new Button("启动 Docker");
        engineStartBtn.getStyleClass().addAll("port-btn", "primary");
        engineStopBtn = new Button("停止 Docker");
        engineStopBtn.getStyleClass().addAll("port-btn", "danger");

        HBox engineRow = new HBox(10, engineDot, engineLabel, engineStartBtn, engineStopBtn);
        engineRow.setAlignment(Pos.CENTER_LEFT);

        engineStartBtn.setOnAction(e -> startDockerEngine());
        engineStopBtn.setOnAction(e -> stopDockerEngine());

        engineCard.getChildren().add(engineRow);
        getChildren().add(engineCard);

        // ====== 人大金仓卡片 ======
        VBox kingbaseCard = createCard("📦 人大金仓 (KingbaseES) — Oracle 兼容 · 端口 54321");

        kingbaseDot = new Circle(5, Color.GRAY);
        kingbaseLabel = new Label("检测中...");
        kingbaseLabel.setStyle("-fx-font-size: 13px;");

        kingbaseInstallBtn = new Button("安装金仓");
        kingbaseInstallBtn.getStyleClass().addAll("port-btn", "primary");
        kingbaseStartBtn = new Button("启动");
        kingbaseStartBtn.getStyleClass().addAll("port-btn", "primary");
        kingbaseStopBtn = new Button("停止");
        kingbaseStopBtn.getStyleClass().addAll("port-btn", "danger");
        kingbaseLogBtn = new Button("日志");
        kingbaseLogBtn.getStyleClass().addAll("port-btn", "secondary");

        kingbaseInstallBtn.setOnAction(e -> installKingbase());
        kingbaseStartBtn.setOnAction(e -> kingbaseAction("start"));
        kingbaseStopBtn.setOnAction(e -> kingbaseAction("stop"));
        kingbaseLogBtn.setOnAction(e -> kingbaseAction("logs"));

        HBox kingbaseRow = new HBox(10, kingbaseDot, kingbaseLabel);
        kingbaseRow.setAlignment(Pos.CENTER_LEFT);
        HBox kingbaseBtns = new HBox(8, kingbaseInstallBtn, kingbaseStartBtn, kingbaseStopBtn, kingbaseLogBtn);
        kingbaseBtns.setAlignment(Pos.CENTER_LEFT);

        kingbaseCard.getChildren().addAll(kingbaseRow, kingbaseBtns);
        getChildren().add(kingbaseCard);

        // ====== 其他容器 ======
        VBox containerCard = createCard("🐋 容器列表");
        containerList = FXCollections.observableArrayList();
        containerTable = buildContainerTable();
        containerTable.setItems(containerList);
        VBox.setVgrow(containerTable, Priority.ALWAYS);
        containerCard.getChildren().add(containerTable);
        getChildren().add(containerCard);

        // ====== 镜像 ======
        VBox imageCard = createCard("💿 镜像列表");
        imageList = FXCollections.observableArrayList();
        imageTable = buildImageTable();
        imageTable.setItems(imageList);
        VBox.setVgrow(imageTable, Priority.ALWAYS);
        imageCard.getChildren().add(imageTable);
        getChildren().add(imageCard);

        // 首次刷新
        refreshAll();
    }

    // ==========================================
    //  卡片骨架
    // ==========================================
    private VBox createCard(String title) {
        VBox card = new VBox(10);
        card.getStyleClass().add("docker-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        card.getChildren().add(titleLabel);
        return card;
    }

    // ==========================================
    //  全部刷新
    // ==========================================
    private void refreshAll() {
        new Thread(() -> {
            checkEngineStatus();
            checkKingbaseStatus();
            refreshContainers();
            refreshImages();
        }).start();
    }

    // ==========================================
    //  Docker 引擎
    // ==========================================
    private void checkEngineStatus() {
        boolean running = isDockerRunning();
        Platform.runLater(() -> {
            if (running) {
                engineDot.setFill(Color.web("#4caf50"));
                engineLabel.setText("Docker 引擎运行中");
                engineStartBtn.setDisable(true);
                engineStopBtn.setDisable(false);
            } else {
                engineDot.setFill(Color.web("#ef5350"));
                engineLabel.setText("Docker 引擎未运行");
                engineStartBtn.setDisable(false);
                engineStopBtn.setDisable(true);
            }
        });
    }

    private boolean isDockerRunning() {
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startDockerEngine() {
        engineStartBtn.setDisable(true);
        engineLabel.setText("正在启动 Docker...");
        new Thread(() -> {
            try {
                new ProcessBuilder("cmd", "/c", "start", "\"\"", "\"" + DOCKER_DESKTOP_PATH + "\"")
                        .start();
                // 轮询等待引擎就绪，最多等 60 秒
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(2000);
                    if (isDockerRunning()) {
                        logger.accept("✓ Docker 引擎已启动");
                        break;
                    }
                }
            } catch (Exception e) {
                logger.accept("✗ 启动 Docker 失败: " + e.getMessage());
            }
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    private void stopDockerEngine() {
        engineStopBtn.setDisable(true);
        engineLabel.setText("正在停止 Docker...");
        new Thread(() -> {
            exec("taskkill /IM \"Docker Desktop.exe\" /F");
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            logger.accept("✓ Docker 引擎已停止");
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    // ==========================================
    //  人大金仓管理
    // ==========================================
    private void checkKingbaseStatus() {
        String json = execSingle("docker ps -a --filter name=" + KINGBASE_NAME + " --format \"{{json .}}\"");
        boolean installed = json != null && !json.trim().isEmpty();
        boolean running = installed && json.contains("\"running\"");

        Platform.runLater(() -> {
            kingbaseInstallBtn.setDisable(installed);
            if (running) {
                kingbaseDot.setFill(Color.web("#4caf50"));
                kingbaseLabel.setText("金仓运行中 — localhost:54321 · Oracle 兼容模式 · system/12345678ab");
                kingbaseStartBtn.setDisable(true);
                kingbaseStopBtn.setDisable(false);
                kingbaseLogBtn.setDisable(false);
            } else if (installed) {
                kingbaseDot.setFill(Color.web("#ef5350"));
                kingbaseLabel.setText("金仓已安装（已停止） — 端口 54321");
                kingbaseStartBtn.setDisable(false);
                kingbaseStopBtn.setDisable(true);
                kingbaseLogBtn.setDisable(false);
            } else {
                kingbaseDot.setFill(Color.GRAY);
                kingbaseLabel.setText("金仓未安装 — 点击 [安装金仓] 一键部署 Oracle 兼容版");
                kingbaseStartBtn.setDisable(true);
                kingbaseStopBtn.setDisable(true);
                kingbaseLogBtn.setDisable(true);
            }
        });
    }

    private void installKingbase() {
        kingbaseInstallBtn.setDisable(true);
        kingbaseLabel.setText("正在拉取镜像并安装金仓...（可能需要几分钟）");
        new Thread(() -> {
            logger.accept("→ 开始安装人大金仓 (Oracle 兼容模式) ...");
            logger.accept("→ 拉取镜像 " + KINGBASE_IMAGE + " ...");

            // 先拉镜像
            String pullOut = exec("docker pull " + KINGBASE_IMAGE);
            if (pullOut != null) {
                for (String line : pullOut.split("\n")) {
                    logger.accept("  " + line.trim());
                }
            }

            // 启动容器
            logger.accept("→ 创建并启动金仓容器 ...");
            String runOut = exec("docker run -d --name " + KINGBASE_NAME
                    + " -p 54321:54321"
                    + " -e NEED_START=yes"
                    + " -e DB_MODE=oracle"
                    + " -e DB_PASSWORD=12345678ab"
                    + " --restart unless-stopped"
                    + " " + KINGBASE_IMAGE);

            if (runOut != null && !runOut.trim().isEmpty()) {
                logger.accept("✓ 金仓容器已创建: " + runOut.trim());
                logger.accept("✓ 连接信息: localhost:54321, system/12345678ab, 数据库 test");
                logger.accept("✓ Oracle 兼容模式已启用");
            } else {
                logger.accept("✗ 金仓容器启动失败，请检查 Docker 是否正常");
            }
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    private void kingbaseAction(String action) {
        new Thread(() -> {
            switch (action) {
                case "start":
                    logger.accept("→ 启动金仓 ...");
                    exec("docker start " + KINGBASE_NAME);
                    // 等待数据库就绪
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    logger.accept("✓ 金仓已启动 — localhost:54321");
                    break;
                case "stop":
                    logger.accept("← 停止金仓 ...");
                    exec("docker stop " + KINGBASE_NAME);
                    logger.accept("✓ 金仓已停止");
                    break;
                case "logs":
                    logger.accept("--- 金仓最近日志 ---");
                    String logs = exec("docker logs --tail 30 " + KINGBASE_NAME);
                    if (logs != null) {
                        for (String line : logs.split("\n")) {
                            logger.accept("  " + line.trim());
                        }
                    }
                    logger.accept("--- 日志结束 ---");
                    break;
            }
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    // ==========================================
    //  容器列表
    // ==========================================
    private void refreshContainers() {
        List<ContainerEntry> list = new ArrayList<>();
        String output = exec("docker ps -a --format \"{{json .}}\"");
        if (output != null) {
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    DockerContainer dc = GSON.fromJson(line, DockerContainer.class);
                    String ports = dc.Ports != null ? dc.Ports : "-";
                    String name = dc.Names != null ? dc.Names : dc.ID;
                    list.add(new ContainerEntry(name, dc.Image, ports, dc.State));
                } catch (Exception ignored) {}
            }
        }
        // 将金仓容器排在最前
        list.sort((a, b) -> {
            if (KINGBASE_NAME.equals(a.getName())) return -1;
            if (KINGBASE_NAME.equals(b.getName())) return 1;
            return 0;
        });
        Platform.runLater(() -> containerList.setAll(list));
    }

    @SuppressWarnings("unchecked")
    private TableView<ContainerEntry> buildContainerTable() {
        TableView<ContainerEntry> table = new TableView<>();
        table.getStyleClass().add("assoc-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ContainerEntry, String> nameCol = new TableColumn<>("容器名");
        nameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        nameCol.setPrefWidth(120);

        TableColumn<ContainerEntry, String> imageCol = new TableColumn<>("镜像");
        imageCol.setCellValueFactory(cell -> cell.getValue().imageProperty());
        imageCol.setPrefWidth(250);

        TableColumn<ContainerEntry, String> portCol = new TableColumn<>("端口");
        portCol.setCellValueFactory(cell -> cell.getValue().portsProperty());
        portCol.setPrefWidth(150);

        TableColumn<ContainerEntry, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell -> cell.getValue().stateProperty());
        statusCol.setPrefWidth(80);

        TableColumn<ContainerEntry, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(160);
        actionCol.setCellFactory(col -> new TableCell<ContainerEntry, Void>() {
            private final HBox box = new HBox(4);
            private final Button startBtn = smallBtn("启动", "primary");
            private final Button stopBtn = smallBtn("停止", "danger");
            private final Button restartBtn = smallBtn("重启", "secondary");
            private final Button logBtn = smallBtn("日志", "secondary");

            {
                box.setAlignment(Pos.CENTER);
                startBtn.setOnAction(e -> containerAction(getIndex(), "start"));
                stopBtn.setOnAction(e -> containerAction(getIndex(), "stop"));
                restartBtn.setOnAction(e -> containerAction(getIndex(), "restart"));
                logBtn.setOnAction(e -> containerAction(getIndex(), "logs"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                ContainerEntry entry = getTableView().getItems().get(getIndex());
                boolean running = "running".equalsIgnoreCase(entry.getState());
                box.getChildren().setAll(running
                        ? new Button[]{stopBtn, restartBtn, logBtn}
                        : new Button[]{startBtn, logBtn});
                setGraphic(box);
            }
        });

        table.getColumns().addAll(nameCol, imageCol, portCol, statusCol, actionCol);
        return table;
    }

    private Button smallBtn(String text, String style) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-cursor: hand;");
        if ("primary".equals(style)) {
            btn.setStyle(btn.getStyle() + "-fx-background-color: #409eff; -fx-text-fill: white;");
        } else if ("danger".equals(style)) {
            btn.setStyle(btn.getStyle() + "-fx-background-color: #f56c6c; -fx-text-fill: white;");
        } else {
            btn.setStyle(btn.getStyle() + "-fx-background-color: #e8e8e8; -fx-text-fill: #303133;");
        }
        return btn;
    }

    private void containerAction(int index, String action) {
        if (index < 0 || index >= containerList.size()) return;
        String name = containerList.get(index).getName();
        new Thread(() -> {
            logger.accept("→ docker " + action + " " + name + " ...");
            exec("docker " + action + " " + name);
            logger.accept("✓ " + name + " " + action + " 完成");
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    // ==========================================
    //  镜像列表
    // ==========================================
    private void refreshImages() {
        List<ImageEntry> list = new ArrayList<>();
        String output = exec("docker images --format \"{{json .}}\"");
        if (output != null) {
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    DockerImage di = GSON.fromJson(line, DockerImage.class);
                    list.add(new ImageEntry(di.Repository, di.Tag, di.Size));
                } catch (Exception ignored) {}
            }
        }
        Platform.runLater(() -> imageList.setAll(list));
    }

    @SuppressWarnings("unchecked")
    private TableView<ImageEntry> buildImageTable() {
        TableView<ImageEntry> table = new TableView<>();
        table.getStyleClass().add("assoc-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ImageEntry, String> nameCol = new TableColumn<>("镜像名");
        nameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        nameCol.setPrefWidth(300);

        TableColumn<ImageEntry, String> tagCol = new TableColumn<>("标签");
        tagCol.setCellValueFactory(cell -> cell.getValue().tagProperty());
        tagCol.setPrefWidth(100);

        TableColumn<ImageEntry, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(cell -> cell.getValue().sizeProperty());
        sizeCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, tagCol, sizeCol);
        return table;
    }

    // ==========================================
    //  UI 数据模型
    // ==========================================
    public static class ContainerEntry {
        private final String name, image, ports, state;

        ContainerEntry(String name, String image, String ports, String state) {
            this.name = name;
            this.image = image;
            this.ports = ports;
            this.state = state;
        }

        public String getName() { return name; }
        public String getImage() { return image; }
        public String getPorts() { return ports; }
        public String getState() { return state; }

        SimpleStringProperty nameProperty() { return new SimpleStringProperty(name); }
        SimpleStringProperty imageProperty() { return new SimpleStringProperty(image); }
        SimpleStringProperty portsProperty() { return new SimpleStringProperty(ports); }
        SimpleStringProperty stateProperty() { return new SimpleStringProperty(state); }
    }

    public static class ImageEntry {
        private final String name, tag, size;

        ImageEntry(String name, String tag, String size) {
            this.name = name;
            this.tag = tag;
            this.size = size;
        }

        public String getName() { return name; }
        public String getTag() { return tag; }
        public String getSize() { return size; }

        SimpleStringProperty nameProperty() { return new SimpleStringProperty(name); }
        SimpleStringProperty tagProperty() { return new SimpleStringProperty(tag); }
        SimpleStringProperty sizeProperty() { return new SimpleStringProperty(size); }
    }

    // ==========================================
    //  Docker JSON DTO
    // ==========================================
    private static class DockerContainer {
        @SerializedName("ID") String ID;
        @SerializedName("Image") String Image;
        @SerializedName("Names") String Names;
        @SerializedName("Ports") String Ports;
        @SerializedName("State") String State;
    }

    private static class DockerImage {
        @SerializedName("Repository") String Repository;
        @SerializedName("Tag") String Tag;
        @SerializedName("Size") String Size;
    }

    // ==========================================
    //  命令执行
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
            return null;
        }
    }

    /** 执行命令并返回第一行（有内容时） */
    private String execSingle(String cmd) {
        String output = exec(cmd);
        if (output != null && !output.isEmpty()) {
            int idx = output.indexOf('\n');
            return idx > 0 ? output.substring(0, idx).trim() : output.trim();
        }
        return null;
    }
}
