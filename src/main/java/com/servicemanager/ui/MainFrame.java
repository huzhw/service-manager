package com.servicemanager.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.servicemanager.model.ServiceInfo;
import com.servicemanager.model.ServiceType;
import com.servicemanager.service.ProcessController;
import com.servicemanager.service.ServiceController;
import com.servicemanager.service.WindowsServiceController;
import com.servicemanager.util.PortChecker;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * 主面板窗口
 */
public class MainFrame extends JFrame {

    // ========== 颜色常量 ==========

    private static final Color COLOR_RUNNING = new Color(0x4C, 0xAF, 0x50);
    private static final Color COLOR_STOPPED = new Color(0xF4, 0x43, 0x36);
    private static final Color COLOR_UNKNOWN = new Color(0x9E, 0x9E, 0x9E);

    /** 分类颜色映射 */
    private static final Map<String, Color> CATEGORY_COLORS = new HashMap<>();
    static {
        CATEGORY_COLORS.put("数据库",     new Color(0x21, 0x96, 0xF3)); // 蓝
        CATEGORY_COLORS.put("缓存",       new Color(0xFF, 0x98, 0x00)); // 橙
        CATEGORY_COLORS.put("搜索引擎",   new Color(0x4C, 0xAF, 0x50)); // 绿
        CATEGORY_COLORS.put("对象存储",   new Color(0x00, 0x96, 0x88)); // 青
        CATEGORY_COLORS.put("Web服务器",  new Color(0x9C, 0x27, 0xB0)); // 紫
        CATEGORY_COLORS.put("注册中心",   new Color(0xF4, 0x43, 0x36)); // 红
    }

    /** 主题持久化 key */
    private static final String PREF_THEME = "theme";
    private static final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    // ========== 字段 ==========

    private final List<ServiceInfo> services;
    private final ServiceTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final JLabel lastRefreshLabel;
    private final JTextArea logArea;
    private final JPanel logPanel;
    private final JButton logToggleBtn;
    private final VersionPanel versionPanel;

    private final WindowsServiceController winController = new WindowsServiceController();
    private final ProcessController procController = new ProcessController();

    private Timer refreshTimer;
    private boolean darkMode;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    // ========== 构造 ==========

    public MainFrame(List<ServiceInfo> services) {
        this.services = services;

        // 读取主题偏好
        darkMode = "dark".equals(prefs.get(PREF_THEME, "light"));
        applyTheme();

        setTitle("服务管理面板");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(820, 580);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);

        // ---- 顶部工具栏 ----
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton startAllBtn  = createToolBarButton("▶  启动全部");
        JButton stopAllBtn   = createToolBarButton("■  停止全部");
        JButton refreshBtn   = createToolBarButton("↻  刷新");
        JButton themeBtn     = createToolBarButton(darkMode ? "☀  亮色" : "🌙  暗色");

        toolBar.add(startAllBtn);
        toolBar.add(Box.createHorizontalStrut(6));
        toolBar.add(stopAllBtn);
        toolBar.addSeparator();
        toolBar.add(refreshBtn);
        toolBar.add(Box.createHorizontalStrut(6));
        toolBar.add(themeBtn);
        add(toolBar, BorderLayout.NORTH);

        // ---- 中部表格 ----
        tableModel = new ServiceTableModel(services);
        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        table.getColumnModel().getColumn(4).setMaxWidth(110);
        table.getColumnModel().getColumn(5).setMaxWidth(80);

        // 服务名列渲染（分类色标）
        table.getColumnModel().getColumn(1).setCellRenderer(new NameRenderer());
        // 状态列渲染
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        // 操作列渲染 + 编辑
        table.getColumnModel().getColumn(5).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ButtonEditor());

        // 斑马纹 + 隐藏网格
        table.setDefaultRenderer(Object.class, new ZebraRenderer());
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 服务管理面板
        JPanel servicePanel = new JPanel(new BorderLayout());
        servicePanel.add(scrollPane, BorderLayout.CENTER);

        // 版本管理面板（共享日志回调）
        versionPanel = new VersionPanel(this::appendLog);

        // 标签页
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("🖥  服务管理", servicePanel);
        tabbedPane.addTab("📦  版本管理", versionPanel);
        add(tabbedPane, BorderLayout.CENTER);

        // ---- 底部日志面板（默认折叠） ----
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setRows(5);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createEmptyBorder());

        logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE0, 0xE0, 0xE0)),
                BorderFactory.createEmptyBorder(4, 0, 0, 0)));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        logPanel.setVisible(false);
        add(logPanel, BorderLayout.SOUTH);

        // ---- 底部状态栏 ----
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusLabel = new JLabel("就绪");
        bottomBar.add(statusLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        lastRefreshLabel = new JLabel("");
        rightPanel.add(lastRefreshLabel);

        logToggleBtn = new JButton("📋 日志");
        logToggleBtn.setFocusPainted(false);
        logToggleBtn.setMargin(new Insets(2, 8, 2, 8));
        logToggleBtn.setFont(logToggleBtn.getFont().deriveFont(11f));
        logToggleBtn.addActionListener(e -> toggleLogPanel());
        rightPanel.add(logToggleBtn);

        bottomBar.add(rightPanel, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // ---- 事件绑定 ----
        startAllBtn.addActionListener(e -> startAllServices());
        stopAllBtn.addActionListener(e -> stopAllServices());
        refreshBtn.addActionListener(e -> refreshAllStatus());
        themeBtn.addActionListener(e -> toggleTheme(themeBtn));

        // 30 秒自动刷新
        refreshTimer = new Timer(30000, e -> refreshAllStatus());
        refreshTimer.start();

        // 首次刷新
        refreshAllStatus();
        appendLog("服务管理面板已启动");
    }

    // ==========================================
    //  工具栏按钮
    // ==========================================
    private JButton createToolBarButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(6, 14, 6, 14));
        return btn;
    }

    // ==========================================
    //  主题切换
    // ==========================================
    private void applyTheme() {
        try {
            if (darkMode) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatIntelliJLaf());
            }
        } catch (Exception e) {
            System.err.println("主题加载失败: " + e.getMessage());
        }
    }

    private void toggleTheme(JButton themeBtn) {
        darkMode = !darkMode;
        prefs.put(PREF_THEME, darkMode ? "dark" : "light");
        try {
            if (darkMode) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatIntelliJLaf());
            }
            FlatLaf.updateUI();
            themeBtn.setText(darkMode ? "☀  亮色" : "🌙  暗色");
            appendLog(darkMode ? "已切换为暗色主题" : "已切换为亮色主题");
        } catch (Exception e) {
            appendLog("✗ 主题切换失败: " + e.getMessage());
        }
    }

    // ==========================================
    //  日志面板
    // ==========================================
    private void toggleLogPanel() {
        boolean visible = !logPanel.isVisible();
        logPanel.setVisible(visible);
        logToggleBtn.setText(visible ? "📋 日志 ▲" : "📋 日志");
        revalidate();
    }

    private void appendLog(String msg) {
        String time = TIME_FMT.format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] %s%n", time, msg));
            // 自动滚到底部
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ==========================================
    //  服务操作
    // ==========================================
    public void refreshAllStatus() {
        new Thread(() -> {
            int runningCount = 0;
            for (ServiceInfo svc : services) {
                ServiceController ctrl = getController(svc);
                String status = ctrl.getStatus(svc);
                // 端口双重验证：进程存活但端口不通则标记警告
                if ("RUNNING".equals(status) && svc.getPort() > 0) {
                    if (!PortChecker.isPortOpen(svc.getPort())) {
                        status = "PORT_UNREACHABLE";
                    }
                }
                svc.setStatus(status);
                if ("RUNNING".equals(status)) {
                    runningCount++;
                }
            }
            final int count = runningCount;
            final String time = TIME_FMT.format(new Date());
            SwingUtilities.invokeLater(() -> {
                tableModel.fireTableDataChanged();
                statusLabel.setText(String.format("共 %d 个服务，%d 个运行中", services.size(), count));
                lastRefreshLabel.setText("上次刷新: " + time);
            });
        }).start();
    }

    public void startAllServices() {
        new Thread(() -> {
            appendLog("→ 开始批量启动...");
            List<ServiceInfo> sorted = services.stream()
                    .sorted(Comparator.comparingInt(ServiceInfo::getStartOrder))
                    .collect(Collectors.toList());

            int ok = 0, fail = 0;
            for (ServiceInfo svc : sorted) {
                if ("RUNNING".equals(svc.getStatus())) {
                    continue;
                }
                setStatusText("正在启动: " + svc.getName());
                appendLog("→ 启动 " + svc.getName() + " ...");
                ServiceController ctrl = getController(svc);
                boolean result = ctrl.start(svc);
                if (result) {
                    svc.setStatus("RUNNING");
                    svc.setStartTime(System.currentTimeMillis());
                    appendLog("  ✓ " + svc.getName() + " 启动成功");
                    ok++;
                } else {
                    appendLog("  ✗ " + svc.getName() + " 启动失败");
                    fail++;
                }
                tableModel.fireTableDataChanged();
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            appendLog(String.format("批量启动完成: 成功 %d, 失败 %d", ok, fail));
            refreshAllStatus();
        }).start();
    }

    public void stopAllServices() {
        new Thread(() -> {
            appendLog("← 开始批量停止...");
            List<ServiceInfo> sorted = services.stream()
                    .sorted(Comparator.comparingInt(ServiceInfo::getStopOrder).reversed())
                    .collect(Collectors.toList());

            int ok = 0, fail = 0;
            for (ServiceInfo svc : sorted) {
                if ("STOPPED".equals(svc.getStatus())) {
                    continue;
                }
                setStatusText("正在停止: " + svc.getName());
                appendLog("← 停止 " + svc.getName() + " ...");
                ServiceController ctrl = getController(svc);
                boolean result = ctrl.stop(svc);
                if (result) {
                    svc.setStatus("STOPPED");
                    svc.setPid(0);
                    svc.setStartTime(0);
                    appendLog("  ✓ " + svc.getName() + " 已停止");
                    ok++;
                } else {
                    appendLog("  ✗ " + svc.getName() + " 停止失败");
                    fail++;
                }
                tableModel.fireTableDataChanged();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            appendLog(String.format("批量停止完成: 成功 %d, 失败 %d", ok, fail));
            refreshAllStatus();
        }).start();
    }

    // ==========================================
    //  辅助
    // ==========================================
    private void setStatusText(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private ServiceController getController(ServiceInfo info) {
        if (info.getType() == ServiceType.WINDOWS_SERVICE) {
            return winController;
        }
        return procController;
    }

    public void stopTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }

    // ==========================================
    //  TableModel
    // ==========================================
    static class ServiceTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "服务名", "类型", "端口", "状态", "操作"};
        private final List<ServiceInfo> list;

        ServiceTableModel(List<ServiceInfo> list) {
            this.list = list;
        }

        @Override public int getRowCount() { return list.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ServiceInfo svc = list.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return svc;
                case 2: return svc.getType().getLabel();
                case 3: return svc.getPort() > 0 ? String.valueOf(svc.getPort()) : "-";
                case 4: return svc.getStatus();
                case 5: return "RUNNING".equals(svc.getStatus()) ? "停止" : "启动";
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 5;
        }

        public ServiceInfo getServiceAt(int row) {
            return list.get(row);
        }
    }

    // ==========================================
    //  斑马纹渲染器
    // ==========================================
    static class ZebraRenderer extends DefaultTableCellRenderer {
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
    //  服务名列渲染（分类色标 + 服务名）
    // ==========================================
    static class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            if (value instanceof ServiceInfo) {
                ServiceInfo svc = (ServiceInfo) value;
                Color catColor = CATEGORY_COLORS.getOrDefault(svc.getCategory(), COLOR_UNKNOWN);
                label.setText("  " + svc.getName()); // 给色标留空间
                label.setIcon(new DotIcon(catColor));
            }
            label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 8));
            return label;
        }
    }

    /**
     * 10×10 小圆点图标，用于分类色标
     */
    static class DotIcon implements Icon {
        private final Color color;
        DotIcon(Color color) { this.color = color; }
        @Override public int getIconWidth()  { return 10; }
        @Override public int getIconHeight() { return 10; }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x + 1, y + 3, 8, 8);
            g2.dispose();
        }
    }

    // ==========================================
    //  状态列渲染
    // ==========================================
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            String status = String.valueOf(value);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            switch (status) {
                case "RUNNING":
                    label.setText("● 运行中");
                    label.setForeground(COLOR_RUNNING);
                    break;
                case "STOPPED":
                    label.setText("● 已停止");
                    label.setForeground(COLOR_STOPPED);
                    break;
                case "STARTING":
                    label.setText("◐ 启动中");
                    label.setForeground(new Color(0xFF, 0x98, 0x00));
                    break;
                case "STOPPING":
                    label.setText("◐ 停止中");
                    label.setForeground(new Color(0xFF, 0x98, 0x00));
                    break;
                default:
                    label.setText("● 未知");
                    label.setForeground(COLOR_UNKNOWN);
            }
            return label;
        }
    }

    // ==========================================
    //  操作列按钮渲染器
    // ==========================================
    static class ButtonRenderer extends JButton implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            setText(String.valueOf(value));
            setEnabled(true);
            return this;
        }
    }

    // ==========================================
    //  操作列按钮编辑器
    // ==========================================
    class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String label;
        private int currentRow;

        ButtonEditor() {
            super(new JCheckBox());
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int col) {
            label = String.valueOf(value);
            button.setText(label);
            currentRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            ServiceInfo svc = tableModel.getServiceAt(currentRow);
            new Thread(() -> {
                ServiceController ctrl = getController(svc);
                if ("RUNNING".equals(svc.getStatus())) {
                    setStatusText("正在停止: " + svc.getName());
                    appendLog("← 停止 " + svc.getName() + " ...");
                    boolean ok = ctrl.stop(svc);
                    if (ok) {
                        svc.setStartTime(0);
                    }
                    appendLog(ok ? "  ✓ " + svc.getName() + " 已停止"
                                 : "  ✗ " + svc.getName() + " 停止失败");
                } else {
                    setStatusText("正在启动: " + svc.getName());
                    appendLog("→ 启动 " + svc.getName() + " ...");
                    boolean ok = ctrl.start(svc);
                    if (ok) {
                        svc.setStartTime(System.currentTimeMillis());
                    }
                    appendLog(ok ? "  ✓ " + svc.getName() + " 启动成功"
                                 : "  ✗ " + svc.getName() + " 启动失败");
                }
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                refreshAllStatus();
            }).start();
            return super.stopCellEditing();
        }
    }
}
