package com.servicemanager;

import com.servicemanager.model.ServiceInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Callback;

import java.util.Map;

/**
 * JavaFX 服务管理表格
 */
public class ServiceTable extends TableView<ServiceInfo> {

    private static final Map<String, Color> CAT_COLORS = Map.of(
            "数据库", Color.web("#4caf50"),
            "缓存", Color.web("#ff9800"),
            "搜索引擎", Color.web("#e91e63"),
            "对象存储", Color.web("#2196f3"),
            "Web服务器", Color.web("#9c27b0"),
            "注册中心", Color.web("#00bcd4")
    );
    private static final Color COLOR_UNKNOWN = Color.web("#607d8b");
    private static final Color COLOR_RUNNING = Color.web("#4caf50");
    private static final Color COLOR_STOPPED = Color.web("#ef5350");
    private static final Color COLOR_WARNING = Color.web("#ff9800");

    private final ObservableList<ServiceInfo> data;
    private final MainWindow mainWindow;

    @SuppressWarnings("unchecked")
    public ServiceTable(java.util.List<ServiceInfo> services, MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.data = FXCollections.observableArrayList(services);
        setItems(data);
        getStyleClass().add("table-view");
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);

        // Columns
        TableColumn<ServiceInfo, Integer> idxCol = new TableColumn<>("#");
        idxCol.setCellValueFactory(cell -> javafx.beans.binding.Bindings.createObjectBinding(
                () -> data.indexOf(cell.getValue()) + 1));
        idxCol.setPrefWidth(40);
        idxCol.setMinWidth(40);
        idxCol.setMaxWidth(40);

        TableColumn<ServiceInfo, String> nameCol = new TableColumn<>("服务名");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(120);
        nameCol.setCellFactory(col -> new TableCell<ServiceInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    ServiceInfo svc = getTableView().getItems().get(getIndex());
                    Color catColor = CAT_COLORS.getOrDefault(svc.getCategory(), COLOR_UNKNOWN);
                    Circle dot = new Circle(3, catColor);
                    setGraphic(new HBox(4, dot, new Label(item)));
                    setText(null);
                }
            }
        });

        TableColumn<ServiceInfo, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getType().getLabel()));
        typeCol.setPrefWidth(80);

        TableColumn<ServiceInfo, String> portCol = new TableColumn<>("端口");
        portCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPort() > 0 ? String.valueOf(cell.getValue().getPort()) : "-"));
        portCol.setPrefWidth(45);
        // center alignment handled by cell factory

        TableColumn<ServiceInfo, Void> dirCol = new TableColumn<>("目录");
        dirCol.setPrefWidth(45);
        dirCol.setMinWidth(45);
        dirCol.setMaxWidth(45);
        dirCol.setCellFactory(col -> new TableCell<ServiceInfo, Void>() {
            private final Button btn = new Button("📂");
            {
                btn.getStyleClass().add("dir-btn");
                btn.setOnAction(e -> {
                    ServiceInfo svc = getTableView().getItems().get(getIndex());
                    String dir = svc.getWorkingDir();
                    if (dir != null && !dir.isEmpty()) {
                        try {
                            Runtime.getRuntime().exec("explorer " + dir);
                        } catch (Exception ex) { /* ignore */ }
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                ServiceInfo svc = getTableView().getItems().get(getIndex());
                String dir = svc.getWorkingDir();
                setGraphic((dir != null && !dir.isEmpty()) ? btn : null);
            }
        });

        TableColumn<ServiceInfo, String> verCol = new TableColumn<>("版本");
        verCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getVersion() != null ? cell.getValue().getVersion() : "-"));
        verCol.setPrefWidth(55);

        TableColumn<ServiceInfo, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        statusCol.setPrefWidth(85);
        statusCol.setCellFactory(col -> new TableCell<ServiceInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(formatStatus(item));
                    badge.getStyleClass().clear();
                    badge.getStyleClass().add("status-badge");
                    switch (item) {
                        case "RUNNING":
                            badge.setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #2e7d32;");
                            break;
                        case "STOPPED":
                            badge.setStyle("-fx-background-color: #f5f5f5; -fx-text-fill: #757575;");
                            break;
                        case "STARTING": case "STOPPING":
                            badge.setStyle("-fx-background-color: #fff3e0; -fx-text-fill: #e65100;");
                            break;
                        default:
                            badge.setStyle("-fx-background-color: #fce4ec; -fx-text-fill: #c62828;");
                            break;
                    }
                    setGraphic(badge);
                }
            }
        });

        TableColumn<ServiceInfo, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(55);
        actionCol.setCellFactory(col -> new TableCell<ServiceInfo, Void>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().add("action-btn");
                btn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                ServiceInfo svc = getTableView().getItems().get(getIndex());
                boolean running = "RUNNING".equals(svc.getStatus());
                btn.setText(running ? "停止" : "启动");
                btn.getStyleClass().removeAll("danger");
                if (running) btn.getStyleClass().add("danger");
                btn.setOnAction(e -> {
                    String label = running ? "停止" : "启动";
                    ButtonType yesBtn = new ButtonType("是");
                    ButtonType noBtn = new ButtonType("否");
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            (running ? "确定停止 " : "确定启动 ") + svc.getName() + "？",
                            yesBtn, noBtn);
                    confirm.setTitle("确认操作");
                    confirm.showAndWait().ifPresent(r -> {
                        if (r == yesBtn) {
                            mainWindow.triggerSingleAction(svc, running);
                        }
                    });
                });
                setGraphic(btn);
            }
        });

        getColumns().addAll(idxCol, nameCol, typeCol, portCol, dirCol, verCol, statusCol, actionCol);
    }

    private String formatStatus(String status) {
        if (status == null) return "● 未知";
        switch (status) {
            case "RUNNING": return "● 运行中";
            case "STOPPED": return "● 已停止";
            case "STARTING": return "◐ 启动中";
            case "STOPPING": return "◐ 停止中";
            case "PORT_UNREACHABLE": return "⚠ 端口不通";
            default: return "● 未知";
        }
    }

    void refreshTable() {
        // Gentle refresh: just re-set items to trigger repaint
        Platform.runLater(() -> {
            ObservableList<ServiceInfo> items = getItems();
            getItems().setAll(new java.util.ArrayList<>(items));
        });
    }
}
