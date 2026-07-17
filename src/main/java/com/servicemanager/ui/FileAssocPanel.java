package com.servicemanager.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 文件关联管理面板 — JavaFX 实现
 * 查看和修改常用文件扩展名的默认打开方式
 */
public class FileAssocPanel extends VBox {

    private final Consumer<String> logger;

    /** 常用扩展名 → 描述 */
    private static final LinkedHashMap<String, String> EXTENSIONS = new LinkedHashMap<>();
    static {
        // 文本 / 代码
        EXTENSIONS.put(".txt",  "文本文件");
        EXTENSIONS.put(".md",   "Markdown");
        EXTENSIONS.put(".java", "Java 源码");
        EXTENSIONS.put(".json", "JSON");
        EXTENSIONS.put(".xml",  "XML");
        EXTENSIONS.put(".py",   "Python");
        EXTENSIONS.put(".js",   "JavaScript");
        EXTENSIONS.put(".html", "网页");
        EXTENSIONS.put(".css",  "样式表");
        // Office
        EXTENSIONS.put(".doc",  "Word 文档 (97-2003)");
        EXTENSIONS.put(".docx", "Word 文档");
        EXTENSIONS.put(".xls",  "Excel 表格 (97-2003)");
        EXTENSIONS.put(".xlsx", "Excel 表格");
        EXTENSIONS.put(".ppt",  "PowerPoint (97-2003)");
        EXTENSIONS.put(".pptx", "PowerPoint");
        EXTENSIONS.put(".csv",  "CSV 表格");
        EXTENSIONS.put(".vsd",  "Visio (97-2003)");
        EXTENSIONS.put(".vsdx", "Visio 绘图");
        // 其他
        EXTENSIONS.put(".pdf",  "PDF 文档");
        EXTENSIONS.put(".zip",  "压缩包");
        EXTENSIONS.put(".png",  "图片 PNG");
        EXTENSIONS.put(".jpg",  "图片 JPG");
    }

    private final TableView<AssocEntry> table;
    private final ObservableList<AssocEntry> entries;

    public FileAssocPanel(Consumer<String> logger) {
        this.logger = logger;

        setSpacing(12);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #f0f2f5;");

        // 标题
        Label header = new Label("📄 常用文件扩展名 — 默认打开方式");
        header.getStyleClass().add("card-title");
        getChildren().add(header);

        // 表格
        entries = FXCollections.observableArrayList();
        for (Map.Entry<String, String> e : EXTENSIONS.entrySet()) {
            entries.add(new AssocEntry(e.getKey(), e.getValue()));
        }

        table = new TableView<>(entries);
        table.getStyleClass().add("assoc-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 扩展名列
        TableColumn<AssocEntry, String> extCol = new TableColumn<>("扩展名");
        extCol.setCellValueFactory(cell -> cell.getValue().extProperty());
        extCol.setPrefWidth(80);
        extCol.setMaxWidth(100);

        // 描述列
        TableColumn<AssocEntry, String> descCol = new TableColumn<>("描述");
        descCol.setCellValueFactory(cell -> cell.getValue().descProperty());
        descCol.setPrefWidth(150);

        // 当前程序列
        TableColumn<AssocEntry, String> progCol = new TableColumn<>("当前程序");
        progCol.setCellValueFactory(cell -> cell.getValue().programProperty());
        progCol.setPrefWidth(300);

        // 操作列
        TableColumn<AssocEntry, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(80);
        actionCol.setMaxWidth(80);
        actionCol.setCellFactory(col -> new TableCell<AssocEntry, Void>() {
            private final Button btn = new Button("更改");
            {
                btn.getStyleClass().add("assoc-action-btn");
                btn.setOnAction(e -> {
                    AssocEntry entry = getTableView().getItems().get(getIndex());
                    changeAssoc(entry.getExt());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(extCol, descCol, progCol, actionCol);

        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(table);

        // 说明
        Label hint = new Label("修改文件关联需要管理员权限。选择扩展名 → [更改] → 选择程序。");
        hint.getStyleClass().add("assoc-hint");
        getChildren().add(hint);

        // 异步加载
        refreshAll();
    }

    // ==========================================
    //  数据模型
    // ==========================================
    public static class AssocEntry {
        private final String ext;
        private final String desc;
        private final SimpleStringProperty program;

        AssocEntry(String ext, String desc) {
            this.ext = ext;
            this.desc = desc;
            this.program = new SimpleStringProperty("检测中...");
        }

        public String getExt() { return ext; }
        public String getDesc() { return desc; }
        public String getProgram() { return program.get(); }
        public void setProgram(String value) { program.set(value); }

        javafx.beans.property.StringProperty extProperty() {
            return new SimpleStringProperty(ext);
        }
        javafx.beans.property.StringProperty descProperty() {
            return new SimpleStringProperty(desc);
        }
        javafx.beans.property.StringProperty programProperty() {
            return program;
        }
    }

    // ==========================================
    //  刷新全部关联信息
    // ==========================================
    private void refreshAll() {
        new Thread(() -> {
            for (AssocEntry entry : entries) {
                String prog = queryAssoc(entry.getExt());
                Platform.runLater(() -> entry.setProgram(prog));
            }
        }).start();
    }

    private String queryAssoc(String ext) {
        try {
            String assocOut = exec("assoc " + ext);
            if (assocOut == null || !assocOut.contains("=")) return "未关联";

            String progId = assocOut.substring(assocOut.indexOf("=") + 1).trim();
            if (progId.isEmpty()) return "未关联";

            String ftypeOut = exec("ftype " + progId);
            if (ftypeOut == null || !ftypeOut.contains("=")) return progId;

            String cmd = ftypeOut.substring(ftypeOut.indexOf("=") + 1).trim();
            return extractExeName(cmd);
        } catch (Exception e) {
            return "查询失败";
        }
    }

    private String extractExeName(String cmd) {
        if (cmd == null || cmd.isEmpty()) return "未知";
        String first = cmd.trim();
        if (first.startsWith("\"")) {
            int end = first.indexOf("\"", 1);
            if (end > 0) first = first.substring(1, end);
        } else {
            int space = first.indexOf(" ");
            if (space > 0) first = first.substring(0, space);
        }
        int lastSep = Math.max(first.lastIndexOf("\\"), first.lastIndexOf("/"));
        if (lastSep >= 0) first = first.substring(lastSep + 1);
        return first;
    }

    private void changeAssoc(String ext) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择打开 " + ext + " 的程序");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("可执行文件 (*.exe)", "*.exe"));
        chooser.setInitialDirectory(new File("C:\\Program Files"));

        File selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected == null) return;

        String exePath = selected.getAbsolutePath();
        new Thread(() -> {
            String progId = "ServiceManager" + ext.replace(".", "_");
            logger.accept("→ 设置 " + ext + " 默认程序为 " + exePath + " ...");

            String cmd1 = "ftype " + progId + "=\"" + exePath + "\" \"%1\"";
            String out1 = exec(cmd1);
            String cmd2 = "assoc " + ext + "=" + progId;
            String out2 = exec(cmd2);

            if (out1 != null && out2 != null) {
                logger.accept("  ✓ " + ext + " 已关联到 " + new File(exePath).getName());
            } else {
                logger.accept("  ✗ 修改失败，请以管理员权限运行");
            }
            Platform.runLater(() -> refreshAll());
        }).start();
    }

    // ==========================================
    //  命令工具
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
                    sb.append(line);
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
