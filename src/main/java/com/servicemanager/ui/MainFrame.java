package com.servicemanager.ui;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.model.ServiceType;
import com.servicemanager.service.ProcessController;
import com.servicemanager.service.ServiceController;
import com.servicemanager.service.WindowsServiceController;
import com.servicemanager.util.LogManager;
import com.servicemanager.util.PortChecker;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (!isCellSelected(row, col)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF5, 0xF7, 0xFA));
                } else {
                    c.setBackground(new Color(0xE3, 0xF2, 0xFD));
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                }
                return c;
            }
        };
        table.setRowHeight(38);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setMinWidth(60);
        table.getColumnModel().getColumn(4).setMaxWidth(50);
        table.getColumnModel().getColumn(4).setMinWidth(50);
        table.getColumnModel().getColumn(5).setMinWidth(70);
        table.getColumnModel().getColumn(6).setMinWidth(120);
        table.getColumnModel().getColumn(7).setMaxWidth(80);
        table.getColumnModel().getColumn(7).setMinWidth(80);

        // 服务名列渲染（分类色标）
        table.getColumnModel().getColumn(1).setCellRenderer(new NameRenderer());
        // 目录列渲染 + 编辑
        table.getColumnModel().getColumn(4).setCellRenderer(new DirButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new DirButtonEditor());
        // 状态列渲染
        table.getColumnModel().getColumn(6).setCellRenderer(new StatusRenderer());
        // 操作列渲染 + 编辑
        table.getColumnModel().getColumn(7).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(7).setCellEditor(new ButtonEditor());

        // 操作列单击直接触发（不用双击进入编辑再点按钮）
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 7 && row >= 0) {
                    ServiceInfo svc = tableModel.getServiceAt(row);
                    boolean stopping = "RUNNING".equals(svc.getStatus());
                    int confirm = JOptionPane.showConfirmDialog(MainFrame.this,
                            (stopping ? "确定停止 " : "确定启动 ") + svc.getName() + "？",
                            "确认操作", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        triggerSingleAction(svc, stopping);
                    }
                }
            }
        });

        // 右键菜单
        JPopupMenu popupMenu = buildPopupMenu();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            private void showPopup(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    popupMenu.show(table, e.getX(), e.getY());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 服务管理面板
        JPanel servicePanel = new JPanel(new BorderLayout());
        servicePanel.add(scrollPane, BorderLayout.CENTER);

        // 版本管理面板（共享日志回调）
        versionPanel = new VersionPanel(LogManager::log);

        // 标签页
        JTabbedPane tabbedPane = new JTabbedPane();
        // 端口工具面板
        PortToolPanel portToolPanel = new PortToolPanel(LogManager::log);

        // 文件关联面板
        FileAssocPanel fileAssocPanel = new FileAssocPanel(LogManager::log);

        tabbedPane.addTab("🖥  服务管理", servicePanel);
        tabbedPane.addTab("📦  版本管理", versionPanel);
        tabbedPane.addTab("🔌  端口工具", portToolPanel);
        tabbedPane.addTab("📄  文件关联", fileAssocPanel);
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
        startAllBtn.addActionListener(e -> {
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(MainFrame.this,
                    "确定要按顺序启动全部服务？", "确认", JOptionPane.YES_NO_OPTION)) {
                startAllServices();
            }
        });
        stopAllBtn.addActionListener(e -> {
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(MainFrame.this,
                    "确定要按顺序停止全部服务？", "确认", JOptionPane.YES_NO_OPTION)) {
                stopAllServices();
            }
        });
        refreshBtn.addActionListener(e -> refreshAllStatus());
        themeBtn.addActionListener(e -> toggleTheme(themeBtn));

        // 30 秒自动刷新
        refreshTimer = new Timer(30000, e -> refreshAllStatus());
        refreshTimer.start();

        // 初始化日志系统（文件 + UI 双写）
        LogManager.init(this::appendLogUi);

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
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Theme failed: " + e.getMessage());
        }
    }

    private void toggleTheme(JButton themeBtn) {
        darkMode = !darkMode;
        prefs.put(PREF_THEME, darkMode ? "dark" : "light");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(this);
            themeBtn.setText(darkMode ? "☀  Light" : "🌙  Dark");
            appendLog(darkMode ? "Switched to light theme" : "Switched to dark theme");
        } catch (Exception e) {
            appendLog("✗ Theme switch failed: " + e.getMessage());
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

    /** UI 日志回调（仅更新面板，由 LogManager 调用） */
    private void appendLogUi(String msg) {
        String time = TIME_FMT.format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] %s%n", time, msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** 写日志（UI + 文件双写，委托给 LogManager） */
    private void appendLog(String msg) {
        LogManager.log(msg);
    }

    // ==========================================
    //  右键菜单
    // ==========================================
    private JPopupMenu buildPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem startItem = new JMenuItem("▶ 启动");
        JMenuItem stopItem  = new JMenuItem("■ 停止");
        JMenuItem detailItem = new JMenuItem("ℹ 查看详情");

        menu.add(startItem);
        menu.add(stopItem);
        menu.addSeparator();
        menu.add(detailItem);

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    ServiceInfo svc = tableModel.getServiceAt(row);
                    boolean running = "RUNNING".equals(svc.getStatus());
                    startItem.setEnabled(!running);
                    stopItem.setEnabled(running);
                }
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        startItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) triggerSingleAction(tableModel.getServiceAt(row), false);
        });
        stopItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) triggerSingleAction(tableModel.getServiceAt(row), true);
        });
        detailItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) showDetailDialog(tableModel.getServiceAt(row));
        });

        return menu;
    }

    private void triggerSingleAction(ServiceInfo svc, boolean stopping) {
        new Thread(() -> {
            List<ServiceInfo> group = getGroupMembers(svc);
            // 同组按顺序操作
            if (stopping) {
                group.sort(Comparator.comparingInt(ServiceInfo::getStopOrder));
            } else {
                group.sort(Comparator.comparingInt(ServiceInfo::getStartOrder));
            }
            for (ServiceInfo member : group) {
                ServiceController ctrl = getController(member);
                if (stopping) {
                    if ("STOPPED".equals(member.getStatus())) continue;
                    setStatusText("正在停止: " + member.getName());
                    member.setStatus("STOPPING");
                    tableModel.fireTableDataChanged();
                    appendLog("← 停止 " + member.getName() + " ...");
                    boolean ok = ctrl.stop(member);
                    if (ok) member.setStartTime(0);
                    appendLog(ok ? "  ✓ " + member.getName() + " 已停止"
                                 : "  ✗ " + member.getName() + " 停止失败");
                } else {
                    if ("RUNNING".equals(member.getStatus())) continue;
                    setStatusText("正在启动: " + member.getName());
                    member.setStatus("STARTING");
                    tableModel.fireTableDataChanged();
                    appendLog("→ 启动 " + member.getName() + " ...");
                    boolean ok = ctrl.start(member);
                    if (ok) member.setStartTime(System.currentTimeMillis());
                    appendLog(ok ? "  ✓ " + member.getName() + " 启动成功"
                                 : "  ✗ " + member.getName() + " 启动失败");
                }
                try { Thread.sleep(800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            }
            refreshAllStatus();
        }).start();
    }

    private void showDetailDialog(ServiceInfo svc) {
        String statusText = svc.getStatus();
        if ("RUNNING".equals(statusText) && svc.getStartTime() > 0) {
            statusText = "运行中 (" + formatDuration(svc.getStartTime()) + ")";
        }

        String info = String.format(
                "服务名：%s\n类型：%s\n分类：%s\n端口：%s\n状态：%s\nPID：%s\n" +
                "标识：%s\n工作目录：%s\n进程名：%s",
                svc.getName(), svc.getType().getLabel(), svc.getCategory(),
                svc.getPort() > 0 ? String.valueOf(svc.getPort()) : "-",
                statusText,
                svc.getPid() > 0 ? String.valueOf(svc.getPid()) : "-",
                svc.getIdentifier(),
                svc.getWorkingDir() != null ? svc.getWorkingDir() : "-",
                svc.getProcessName() != null ? svc.getProcessName() : "-");

        JOptionPane.showMessageDialog(this, info,
                svc.getName() + " — 详情", JOptionPane.INFORMATION_MESSAGE);
    }

    static String formatDuration(long startTimeMs) {
        if (startTimeMs <= 0) return "";
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed < 60_000) return (elapsed / 1000) + "s";
        else if (elapsed < 3_600_000) return (elapsed / 60_000) + "m";
        long hours = elapsed / 3_600_000;
        long mins = (elapsed % 3_600_000) / 60_000;
        return hours + "h " + mins + "m";
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
                svc.setStatus("STARTING");
                tableModel.fireTableDataChanged();
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
                svc.setStatus("STOPPING");
                tableModel.fireTableDataChanged();
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

    /** 获取同组的所有服务（包括自身） */
    private List<ServiceInfo> getGroupMembers(ServiceInfo svc) {
        String group = svc.getGroupName();
        if (group == null || group.isEmpty()) {
            List<ServiceInfo> self = new java.util.ArrayList<>();
            self.add(svc);
            return self;
        }
        List<ServiceInfo> members = new java.util.ArrayList<>();
        for (ServiceInfo s : services) {
            if (group.equals(s.getGroupName())) {
                members.add(s);
            }
        }
        return members;
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
        private final String[] columns = {"#", "服务名", "类型", "端口", "目录", "版本", "状态", "操作"};
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
                case 4: return "📂";
                case 5: return svc.getVersion() != null ? svc.getVersion() : "-";
                case 6: return svc;
                case 7: return "RUNNING".equals(svc.getStatus()) ? "停止" : "启动";
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 4 || col == 7;
        }

        public ServiceInfo getServiceAt(int row) {
            return list.get(row);
        }
    }

    // ==========================================
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
            label.setHorizontalAlignment(SwingConstants.CENTER);

            String status;
            ServiceInfo svc = null;
            if (value instanceof ServiceInfo) {
                svc = (ServiceInfo) value;
                status = svc.getStatus();
            } else {
                status = String.valueOf(value);
            }

            switch (status) {
                case "RUNNING":
                    if (svc != null && svc.getStartTime() > 0) {
                        String dur = formatDuration(svc.getStartTime());
                        label.setText("● 运行中 " + dur);
                    } else {
                        label.setText("● 运行中");
                    }
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
                case "PORT_UNREACHABLE":
                    label.setText("⚠ 端口不通");
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
    //  目录列按钮 — 打开资源管理器
    // ==========================================
    static class DirButtonRenderer extends JButton implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            setText(String.valueOf(value));
            setEnabled(true);
            setToolTipText("打开目录");
            return this;
        }
    }

    class DirButtonEditor extends DefaultCellEditor {
        private JButton button;
        private int currentRow;

        DirButtonEditor() {
            super(new JCheckBox());
            button = new JButton("📂");
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
        public Object getCellEditorValue() {
            return "📂";
        }

        @Override
        public boolean stopCellEditing() {
            ServiceInfo svc = tableModel.getServiceAt(currentRow);
            String dir = svc.getWorkingDir();
            if (dir != null && !dir.isEmpty()) {
                try {
                    Runtime.getRuntime().exec("explorer " + dir);
                } catch (Exception ex) { /* ignore */ }
            }
            return super.stopCellEditing();
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
            triggerSingleAction(svc, "RUNNING".equals(svc.getStatus()));
            return super.stopCellEditing();
        }
    }
}
