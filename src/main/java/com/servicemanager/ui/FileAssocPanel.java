package com.servicemanager.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 文件关联管理面板 — 查看和修改常用文件扩展名的默认打开方式
 */
public class FileAssocPanel extends JPanel {

    private final Consumer<String> logger;

    /** 常用扩展名 → 描述 */
    private static final LinkedHashMap<String, String> COMMON_EXTENSIONS = new LinkedHashMap<>();
    static {
        COMMON_EXTENSIONS.put(".txt",  "文本文件");
        COMMON_EXTENSIONS.put(".md",   "Markdown");
        COMMON_EXTENSIONS.put(".java", "Java 源码");
        COMMON_EXTENSIONS.put(".json", "JSON");
        COMMON_EXTENSIONS.put(".xml",  "XML");
        COMMON_EXTENSIONS.put(".py",   "Python");
        COMMON_EXTENSIONS.put(".js",   "JavaScript");
        COMMON_EXTENSIONS.put(".html", "网页");
        COMMON_EXTENSIONS.put(".css",  "样式表");
        COMMON_EXTENSIONS.put(".pdf",  "PDF 文档");
        COMMON_EXTENSIONS.put(".zip",  "压缩包");
        COMMON_EXTENSIONS.put(".png",  "图片 PNG");
        COMMON_EXTENSIONS.put(".jpg",  "图片 JPG");
    }

    private final AssocTableModel tableModel;
    private final JTable table;

    private static final Color CARD_BORDER = new Color(0xE0, 0xE0, 0xE0);

    public FileAssocPanel(Consumer<String> logger) {
        this.logger = logger;

        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // 标题
        JLabel header = new JLabel("📄 常用文件扩展名 — 默认打开方式");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);

        // 说明
        JLabel hint = new JLabel("修改文件关联需要管理员权限。选择扩展名 → [更改] → 选择程序。");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(Color.GRAY);
        add(hint, BorderLayout.SOUTH);

        // 表格
        tableModel = new AssocTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(34);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setMaxWidth(80);

        // 操作列按钮
        table.getColumnModel().getColumn(3).setCellRenderer(new AssocButtonRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new AssocButtonEditor());

        // 斑马纹
        table.setDefaultRenderer(Object.class, new ZebraAssocRenderer());
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(CARD_BORDER, 1, true));
        add(scrollPane, BorderLayout.CENTER);

        // 加载
        refreshAll();
    }

    // ==========================================
    //  刷新全部关联信息
    // ==========================================
    private void refreshAll() {
        new Thread(() -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String ext = (String) tableModel.getValueAt(i, 0);
                String prog = queryAssoc(ext);
                final int row = i;
                final String program = prog;
                SwingUtilities.invokeLater(() -> tableModel.setProgramAt(row, program));
            }
        }).start();
    }

    /**
     * 查询扩展名当前关联的程序名
     */
    private String queryAssoc(String ext) {
        try {
            // 1. assoc .ext → .ext=progid
            String assocOut = exec("assoc " + ext);
            if (assocOut == null || !assocOut.contains("=")) return "未关联";

            String progId = assocOut.substring(assocOut.indexOf("=") + 1).trim();
            if (progId.isEmpty()) return "未关联";

            // 2. ftype progid → progid=command
            String ftypeOut = exec("ftype " + progId);
            if (ftypeOut == null || !ftypeOut.contains("=")) return progId;

            String cmd = ftypeOut.substring(ftypeOut.indexOf("=") + 1).trim();
            // 提取可执行文件路径（去掉引号和 %1 参数）
            return extractExeName(cmd);

        } catch (Exception e) {
            return "查询失败";
        }
    }

    /**
     * 从 ftype 命令输出中提取程序名
     * "C:\Program Files\Notepad++\notepad++.exe" "%1" → Notepad++.exe
     */
    private String extractExeName(String cmd) {
        if (cmd == null || cmd.isEmpty()) return "未知";
        // 去掉首尾引号，取第一个词
        String first = cmd.trim();
        if (first.startsWith("\"")) {
            int end = first.indexOf("\"", 1);
            if (end > 0) first = first.substring(1, end);
        } else {
            int space = first.indexOf(" ");
            if (space > 0) first = first.substring(0, space);
        }
        // Linux-style paths like /usr/bin/...
        int lastSep = Math.max(first.lastIndexOf("\\"), first.lastIndexOf("/"));
        if (lastSep >= 0) first = first.substring(lastSep + 1);
        return first;
    }

    /**
     * 修改文件关联
     */
    private void changeAssoc(String ext) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择打开 " + ext + " 的程序");
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".exe");
            }
            @Override public String getDescription() { return "可执行文件 (*.exe)"; }
        });

        // 默认打开 Program Files
        chooser.setCurrentDirectory(new File("C:\\Program Files"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String exePath = chooser.getSelectedFile().getAbsolutePath();
        new Thread(() -> {
            String progId = "ServiceManager" + ext.replace(".", "_");
            logger.accept("→ 设置 " + ext + " 默认程序为 " + exePath + " ...");

            // 设置 ftype
            String cmd1 = "ftype " + progId + "=\"" + exePath + "\" \"%1\"";
            String out1 = exec(cmd1);

            // 设置 assoc
            String cmd2 = "assoc " + ext + "=" + progId;
            String out2 = exec(cmd2);

            if (out1 != null && out2 != null) {
                logger.accept("  ✓ " + ext + " 已关联到 " + new File(exePath).getName());
            } else {
                logger.accept("  ✗ 修改失败，请以管理员权限运行");
            }

            // 刷新表格
            SwingUtilities.invokeLater(() -> refreshAll());
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

    // ==========================================
    //  TableModel
    // ==========================================
    static class AssocTableModel extends AbstractTableModel {
        private final String[] columns = {"扩展名", "描述", "当前程序", "操作"};
        private final List<Map.Entry<String, String>> entries;
        private final String[] programs;

        AssocTableModel() {
            entries = new ArrayList<>(COMMON_EXTENSIONS.entrySet());
            programs = new String[entries.size()];
            Arrays.fill(programs, "检测中...");
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            Map.Entry<String, String> entry = entries.get(row);
            switch (col) {
                case 0: return entry.getKey();
                case 1: return entry.getValue();
                case 2: return programs[row];
                case 3: return "更改";
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) { return col == 3; }

        void setProgramAt(int row, String prog) {
            programs[row] = prog;
            fireTableCellUpdated(row, 2);
        }

        String getExtensionAt(int row) {
            return entries.get(row).getKey();
        }
    }

    // ==========================================
    //  斑马纹
    // ==========================================
    static class ZebraAssocRenderer extends DefaultTableCellRenderer {
        private static final Color ZEBRA_ODD  = new Color(0xF5, 0xF7, 0xFA);
        private static final Color ZEBRA_EVEN = Color.WHITE;
        private static final Color SELECTED_BG = new Color(0xE3, 0xF2, 0xFD);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? ZEBRA_EVEN : ZEBRA_ODD);
            } else {
                c.setBackground(SELECTED_BG);
                c.setForeground(Color.BLACK);
            }
            if (c instanceof JLabel) {
                ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            }
            return c;
        }
    }

    // ==========================================
    //  操作列按钮
    // ==========================================
    static class AssocButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            setText(String.valueOf(value));
            setEnabled(true);
            return this;
        }
    }

    class AssocButtonEditor extends DefaultCellEditor {
        private JButton button;
        private int currentRow;

        AssocButtonEditor() {
            super(new JCheckBox());
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int col) {
            button.setText(String.valueOf(value));
            currentRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() { return "更改"; }

        @Override
        public boolean stopCellEditing() {
            String ext = tableModel.getExtensionAt(currentRow);
            changeAssoc(ext);
            return super.stopCellEditing();
        }
    }
}
