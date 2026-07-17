package com.servicemanager.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * 端口工具面板 — 查找端口占用进程并强制终止
 */
public class PortToolPanel extends JPanel {

    private final Consumer<String> logger;

    private final JTextField portInput;
    private final JButton findBtn;
    private final JButton killBtn;
    private final JLabel resultLabel;

    private String currentPid;
    private String currentProcName;

    /** 常用端口列表（来自 kill-档案端口.bat） */
    private static final int[] COMMON_PORTS = {
            3000, 3001, 8051, 8021, 8087, 8095, 8091, 8080, 8000, 8686, 10535
    };

    private static final Color CARD_BORDER = new Color(0xE0, 0xE0, 0xE0);

    public PortToolPanel(Consumer<String> logger) {
        this.logger = logger;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // ====== 自定义端口查找 ======
        JPanel customPanel = createCard("🔍 查找端口");

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        inputRow.setOpaque(false);
        inputRow.add(new JLabel("端口号："));
        portInput = new JTextField(8);
        inputRow.add(portInput);
        findBtn = new JButton("查找");
        findBtn.setFocusPainted(false);
        killBtn = new JButton("终止进程");
        killBtn.setFocusPainted(false);
        killBtn.setEnabled(false);
        inputRow.add(findBtn);
        inputRow.add(killBtn);

        resultLabel = new JLabel(" ");
        resultLabel.setFont(resultLabel.getFont().deriveFont(12f));

        customPanel.add(inputRow, BorderLayout.NORTH);
        customPanel.add(resultLabel, BorderLayout.CENTER);
        add(customPanel);
        add(Box.createVerticalStrut(16));

        // ====== 常用端口快捷区 ======
        JPanel quickPanel = createCard("⚡ 常用端口快捷查杀");
        JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        chipsPanel.setOpaque(false);

        for (int port : COMMON_PORTS) {
            JButton chip = new JButton(String.valueOf(port));
            chip.setFocusPainted(false);
            chip.setFont(chip.getFont().deriveFont(11f));
            chip.setMargin(new Insets(3, 10, 3, 10));
            chip.setToolTipText("查找并终止端口 " + port);
            chip.addActionListener(e -> quickKill(port, chip));
            chipsPanel.add(chip);
        }
        quickPanel.add(chipsPanel, BorderLayout.CENTER);
        add(quickPanel);
        add(Box.createVerticalGlue());

        // ---- 事件绑定 ----
        findBtn.addActionListener(e -> findPort());
        killBtn.addActionListener(e -> killPort());
        portInput.addActionListener(e -> findPort());
    }

    // ==========================================
    //  卡片骨架
    // ==========================================
    private JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        card.setOpaque(true);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        card.add(titleLabel, BorderLayout.NORTH);
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
            return;
        }

        currentPid = null;
        currentProcName = null;
        killBtn.setEnabled(false);
        resultLabel.setText("检测中...");

        new Thread(() -> {
            String pid = findPidByPort(port);
            if (pid != null) {
                currentPid = pid;
                currentProcName = findProcName(pid);
                String text = "⚠ 端口 " + port + " 被占用 — PID: " + pid
                        + (currentProcName != null ? " (" + currentProcName + ")" : "");
                SwingUtilities.invokeLater(() -> {
                    resultLabel.setText(text);
                    resultLabel.setForeground(new Color(0xF4, 0x43, 0x36));
                    killBtn.setEnabled(true);
                });
                logger.accept("发现端口 " + port + " 被 PID " + pid + " 占用");
            } else {
                SwingUtilities.invokeLater(() -> {
                    resultLabel.setText("✓ 端口 " + port + " 未被占用");
                    resultLabel.setForeground(new Color(0x4C, 0xAF, 0x50));
                    killBtn.setEnabled(false);
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
            // 刷新状态
            SwingUtilities.invokeLater(() -> findPort());
        }).start();
    }

    private void quickKill(int port, JButton chip) {
        chip.setEnabled(false);
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
            SwingUtilities.invokeLater(() -> chip.setEnabled(true));
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
                // netstat 输出格式: TCP  0.0.0.0:8080  0.0.0.0:0  LISTENING  12345
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    return parts[parts.length - 1]; // PID 是最后一列
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
                    // tasklist 输出格式: java.exe  12345  Console  1  1,234,567 K
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
