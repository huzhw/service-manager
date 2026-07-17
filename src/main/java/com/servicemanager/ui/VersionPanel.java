package com.servicemanager.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 版本管理面板 — nvm (Node.js) + pyenv (Python) 版本查看、切换、安装
 */
public class VersionPanel extends JPanel {

    private final Consumer<String> logger;

    // ---- Node.js 组件 ----
    private final JLabel nodeCurrentLabel;
    private final JPanel nodeVersionListPanel;
    private final JButton nodeRefreshBtn;
    private final JButton nodeInstallBtn;

    // ---- Python 组件 ----
    private final JLabel pythonCurrentLabel;
    private final JPanel pythonVersionListPanel;
    private final JButton pythonRefreshBtn;
    private final JButton pythonInstallBtn;

    /** 版本标签样式 */
    private static final Color CHIP_BG = new Color(0xE8, 0xE8, 0xE8);
    private static final Color CHIP_ACTIVE_BG = new Color(0x21, 0x96, 0xF3);
    private static final Color CHIP_ACTIVE_FG = Color.WHITE;
    private static final Color CARD_BORDER = new Color(0xE0, 0xE0, 0xE0);

    public VersionPanel(Consumer<String> logger) {
        this.logger = logger;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // ====== Node.js 卡片 ======
        JPanel nodeCard = createCard("🟢 Node.js (nvm)");
        nodeCurrentLabel = new JLabel("检测中...");
        nodeCurrentLabel.setFont(nodeCurrentLabel.getFont().deriveFont(Font.BOLD, 13f));
        nodeVersionListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        nodeVersionListPanel.setOpaque(false);
        nodeRefreshBtn = new JButton("↻ 刷新");
        nodeInstallBtn = new JButton("+ 安装版本");

        assembleCard(nodeCard, nodeCurrentLabel, nodeVersionListPanel, nodeRefreshBtn, nodeInstallBtn);
        add(nodeCard);
        add(Box.createVerticalStrut(16));

        // ====== Python 卡片 ======
        JPanel pythonCard = createCard("🐍 Python (pyenv)");
        pythonCurrentLabel = new JLabel("检测中...");
        pythonCurrentLabel.setFont(pythonCurrentLabel.getFont().deriveFont(Font.BOLD, 13f));
        pythonVersionListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pythonVersionListPanel.setOpaque(false);
        pythonRefreshBtn = new JButton("↻ 刷新");
        pythonInstallBtn = new JButton("+ 安装版本");

        assembleCard(pythonCard, pythonCurrentLabel, pythonVersionListPanel, pythonRefreshBtn, pythonInstallBtn);
        add(pythonCard);
        add(Box.createVerticalGlue());

        // ---- 事件绑定 ----
        nodeRefreshBtn.addActionListener(e  -> refreshNodeVersions());
        nodeInstallBtn.addActionListener(e  -> installVersion("Node.js", "nvm", "nvm install %s", this::refreshNodeVersions));
        pythonRefreshBtn.addActionListener(e -> refreshPythonVersions());
        pythonInstallBtn.addActionListener(e -> installVersion("Python", "pyenv", "pyenv install %s", this::refreshPythonVersions));

        // 首次加载
        refreshNodeVersions();
        refreshPythonVersions();
    }

    // ==========================================
    //  卡片骨架
    // ==========================================
    private JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout(12, 12));
        card.setBorder(new CompoundBorder(
                new LineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(14, 16, 14, 16)));
        card.setOpaque(true);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        card.add(titleLabel, BorderLayout.NORTH);
        return card;
    }

    private void assembleCard(JPanel card, JLabel currentLabel, JPanel versionList,
                              JButton refreshBtn, JButton installBtn) {
        // 中间：当前版本 + 已安装版本列表
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(currentLabel, BorderLayout.NORTH);
        center.add(versionList, BorderLayout.CENTER);

        // 底部按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.setOpaque(false);
        installBtn.setFocusPainted(false);
        refreshBtn.setFocusPainted(false);
        bottom.add(installBtn);
        bottom.add(refreshBtn);

        card.add(center, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
    }

    // ==========================================
    //  Node.js 版本管理
    // ==========================================
    private void refreshNodeVersions() {
        new Thread(() -> {
            // 获取当前版本
            String current = execReadLine("node --version");
            SwingUtilities.invokeLater(() ->
                    nodeCurrentLabel.setText("当前版本: " + (current != null ? current : "未检测到")));

            // 获取已安装列表
            List<String> versions = parseNvmList();
            final String active = current != null ? current.replace("v", "") : "";
            SwingUtilities.invokeLater(() -> buildVersionChips(nodeVersionListPanel, versions, active,
                    v -> switchNodeVersion(v)));
        }).start();
    }

    private List<String> parseNvmList() {
        List<String> list = new ArrayList<>();
        try {
            String output = exec("nvm list");
            if (output != null) {
                for (String line : output.split("\n")) {
                    line = line.trim();
                    // nvm list 输出格式：  * 18.17.0 (Currently using...)  或  16.14.0
                    if (line.matches(".*\\d+\\.\\d+\\.\\d+.*")) {
                        String ver = line.replaceAll(".*?(\\d+\\.\\d+\\.\\d+).*", "$1");
                        if (!list.contains(ver)) {
                            list.add(ver);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.accept("✗ nvm list 解析失败: " + e.getMessage());
        }
        return list;
    }

    private void switchNodeVersion(String version) {
        new Thread(() -> {
            logger.accept("→ 切换 Node.js 到 " + version + " ...");
            String output = exec("nvm use " + version);
            if (output != null && output.contains("now using")) {
                logger.accept("  ✓ Node.js 已切换至 " + version);
            } else if (output != null) {
                logger.accept("  " + output.trim());
            }
            SwingUtilities.invokeLater(this::refreshNodeVersions);
        }).start();
    }

    // ==========================================
    //  Python 版本管理
    // ==========================================
    private void refreshPythonVersions() {
        new Thread(() -> {
            String current = execReadLine("python --version");
            // python --version 输出到 stderr，格式：Python 3.9.13
            if (current != null && current.toLowerCase().startsWith("python ")) {
                current = current.substring(7).trim();
            }
            final String cur = current;
            SwingUtilities.invokeLater(() ->
                    pythonCurrentLabel.setText("当前版本: " + (cur != null ? cur : "未检测到")));

            List<String> versions = parsePyenvVersions();
            final String active = cur;
            SwingUtilities.invokeLater(() -> buildVersionChips(pythonVersionListPanel, versions, active,
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
                        // 去掉前面的 * 标记
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
            String out1 = exec("pyenv global " + version);
            String out2 = exec("pyenv rehash");
            logger.accept("  ✓ Python 已切换至 " + version);
            SwingUtilities.invokeLater(this::refreshPythonVersions);
        }).start();
    }

    // ==========================================
    //  安装版本（通用）
    // ==========================================
    private void installVersion(String label, String tool, String cmdTemplate, Runnable onDone) {
        String version = JOptionPane.showInputDialog(this,
                "输入要安装的 " + label + " 版本号：\n例如: 18.17.0",
                "安装 " + label + " 版本", JOptionPane.QUESTION_MESSAGE);
        if (version == null || version.trim().isEmpty()) {
            return;
        }
        final String ver = version.trim();
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
            SwingUtilities.invokeLater(onDone);
        }).start();
    }

    // ==========================================
    //  版本标签组件
    // ==========================================
    /**
     * 用版本号列表构建可点击的标签按钮
     */
    private void buildVersionChips(JPanel panel, List<String> versions, String activeVersion,
                                   Consumer<String> onClick) {
        panel.removeAll();
        if (versions.isEmpty()) {
            JLabel empty = new JLabel("（未检测到已安装版本）");
            empty.setForeground(Color.GRAY);
            panel.add(empty);
        } else {
            for (String v : versions) {
                boolean isActive = v.equals(activeVersion);
                JButton chip = new JButton(v);
                chip.setFocusPainted(false);
                chip.setFont(chip.getFont().deriveFont(12f));
                chip.setMargin(new Insets(4, 12, 4, 12));
                chip.setContentAreaFilled(true);
                chip.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(isActive ? CHIP_ACTIVE_BG : new Color(0xCC, 0xCC, 0xCC), 1),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)));
                chip.setBackground(isActive ? CHIP_ACTIVE_BG : CHIP_BG);
                chip.setForeground(isActive ? CHIP_ACTIVE_FG : Color.BLACK);
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                // 标记当前版本
                if (isActive) {
                    chip.setText("● " + v);
                }

                // 悬停效果
                chip.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        if (!isActive) {
                            chip.setBackground(new Color(0xDD, 0xDD, 0xDD));
                        }
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        if (!isActive) {
                            chip.setBackground(CHIP_BG);
                        }
                    }
                });

                chip.addActionListener(e -> onClick.accept(v));
                panel.add(chip);
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    // ==========================================
    //  命令执行工具
    // ==========================================
    /**
     * 执行命令并返回标准输出
     */
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

    /**
     * 执行命令并返回第一行（用于版本号查询）
     */
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
