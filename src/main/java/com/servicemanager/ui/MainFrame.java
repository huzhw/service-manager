package com.servicemanager.ui;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.model.ServiceType;
import com.servicemanager.service.ProcessController;
import com.servicemanager.service.ServiceController;
import com.servicemanager.service.WindowsServiceController;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主面板窗口
 */
public class MainFrame extends JFrame {

    private static final Color COLOR_RUNNING = new Color(0x4C, 0xAF, 0x50);   // 绿色
    private static final Color COLOR_STOPPED = new Color(0xF4, 0x43, 0x36);   // 红色
    private static final Color COLOR_UNKNOWN = new Color(0x9E, 0x9E, 0x9E);   // 灰色

    private final List<ServiceInfo> services;
    private final ServiceTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;

    private final WindowsServiceController winController = new WindowsServiceController();
    private final ProcessController procController = new ProcessController();

    /** 自动刷新定时器 */
    private Timer refreshTimer;

    public MainFrame(List<ServiceInfo> services) {
        this.services = services;

        setTitle("服务管理面板");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE); // 关闭→隐藏到托盘
        setSize(800, 520);
        setLocationRelativeTo(null);

        // 顶部工具栏
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton startAllBtn = new JButton("▶ 启动全部");
        JButton stopAllBtn = new JButton("■ 停止全部");
        JButton refreshBtn = new JButton("↻ 刷新");
        toolBar.add(startAllBtn);
        toolBar.add(stopAllBtn);
        toolBar.addSeparator();
        toolBar.add(refreshBtn);
        add(toolBar, BorderLayout.NORTH);

        // 中部表格
        tableModel = new ServiceTableModel(services);
        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.getColumnModel().getColumn(0).setMaxWidth(40);   // 序号
        table.getColumnModel().getColumn(1).setPreferredWidth(140); // 服务名
        table.getColumnModel().getColumn(2).setMaxWidth(90);   // 类型
        table.getColumnModel().getColumn(3).setMaxWidth(60);   // 端口
        table.getColumnModel().getColumn(4).setMaxWidth(110);  // 状态
        table.getColumnModel().getColumn(5).setMaxWidth(80);   // 操作

        // 状态列渲染（带颜色圆点）
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        // 操作列渲染 + 编辑（按钮）
        table.getColumnModel().getColumn(5).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ButtonEditor());

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 底部状态栏
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusLabel = new JLabel("就绪");
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ========== 事件绑定 ==========

        startAllBtn.addActionListener(e -> startAllServices());
        stopAllBtn.addActionListener(e -> stopAllServices());
        refreshBtn.addActionListener(e -> refreshAllStatus());

        // 30 秒自动刷新
        refreshTimer = new Timer(30000, e -> refreshAllStatus());
        refreshTimer.start();

        // 首次刷新
        refreshAllStatus();
    }

    /**
     * 刷新全部服务状态
     */
    public void refreshAllStatus() {
        new Thread(() -> {
            int runningCount = 0;
            for (ServiceInfo svc : services) {
                ServiceController ctrl = getController(svc);
                String status = ctrl.getStatus(svc);
                svc.setStatus(status);
                if ("RUNNING".equals(status)) {
                    runningCount++;
                }
            }
            final int count = runningCount;
            SwingUtilities.invokeLater(() -> {
                tableModel.fireTableDataChanged();
                statusLabel.setText(String.format("共 %d 个服务，%d 个运行中", services.size(), count));
            });
        }).start();
    }

    /**
     * 按顺序启动全部
     */
    public void startAllServices() {
        new Thread(() -> {
            // 按 startOrder 排序
            List<ServiceInfo> sorted = services.stream()
                    .sorted(Comparator.comparingInt(ServiceInfo::getStartOrder))
                    .collect(Collectors.toList());

            for (ServiceInfo svc : sorted) {
                if ("RUNNING".equals(svc.getStatus())) {
                    continue;
                }
                setStatusText("正在启动: " + svc.getName());
                ServiceController ctrl = getController(svc);
                boolean ok = ctrl.start(svc);
                if (ok) {
                    svc.setStatus("RUNNING");
                }
                tableModel.fireTableDataChanged();
                try {
                    Thread.sleep(1500); // 间隔避免资源争抢
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            refreshAllStatus();
        }).start();
    }

    /**
     * 按顺序停止全部
     */
    public void stopAllServices() {
        new Thread(() -> {
            // 按 stopOrder 排序（默认 stopOrder=0 的排在前面先停）
            List<ServiceInfo> sorted = services.stream()
                    .sorted(Comparator.comparingInt(ServiceInfo::getStopOrder).reversed())
                    .collect(Collectors.toList());

            for (ServiceInfo svc : sorted) {
                if ("STOPPED".equals(svc.getStatus())) {
                    continue;
                }
                setStatusText("正在停止: " + svc.getName());
                ServiceController ctrl = getController(svc);
                ctrl.stop(svc);
                svc.setStatus("STOPPED");
                svc.setPid(0);
                tableModel.fireTableDataChanged();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            refreshAllStatus();
        }).start();
    }

    private void setStatusText(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /**
     * 根据服务类型获取对应的控制器
     */
    private ServiceController getController(ServiceInfo info) {
        if (info.getType() == ServiceType.WINDOWS_SERVICE) {
            return winController;
        }
        return procController;
    }

    /** 停止定时器 */
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

        @Override
        public int getRowCount() {
            return list.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            ServiceInfo svc = list.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return svc.getName();
                case 2: return svc.getType().getLabel();
                case 3: return svc.getPort() > 0 ? String.valueOf(svc.getPort()) : "-";
                case 4: return svc.getStatus();
                case 5: return "RUNNING".equals(svc.getStatus()) ? "停止" : "启动";
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 5; // 只有操作列可编辑（点击按钮）
        }

        public ServiceInfo getServiceAt(int row) {
            return list.get(row);
        }
    }

    // ==========================================
    //  状态列渲染（绿色/红色/灰色圆点）
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
    //  操作列按钮编辑器（处理点击）
    // ==========================================
    class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String label;
        private int currentRow;

        ButtonEditor() {
            super(new JCheckBox());
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> {
                fireEditingStopped();
                // 按钮点击逻辑在 stopCellEditing 中处理
            });
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
            // 触发实际启停操作
            ServiceInfo svc = tableModel.getServiceAt(currentRow);
            new Thread(() -> {
                ServiceController ctrl = getController(svc);
                if ("RUNNING".equals(svc.getStatus())) {
                    setStatusText("正在停止: " + svc.getName());
                    ctrl.stop(svc);
                } else {
                    setStatusText("正在启动: " + svc.getName());
                    ctrl.start(svc);
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
