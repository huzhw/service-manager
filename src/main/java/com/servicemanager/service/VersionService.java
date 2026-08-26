package com.servicemanager.service;

import com.servicemanager.util.CmdExec;
import com.servicemanager.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 版本管理服务 — nvm (Node.js) / pyenv (Python) 版本查看、切换、安装
 * <p>
 * 业务逻辑自原 ui.VersionPanel 抽取，供 REST API 调用。
 */
public class VersionService {

    /** 版本快照：当前版本 + 已安装版本列表 */
    public static class Snapshot {
        public String tool;          // node | python
        public String current;       // 当前生效版本，null = 未检测到
        public List<String> versions = new ArrayList<>();
    }

    // ==========================================
    //  Node.js (nvm)
    // ==========================================

    /**
     * 查询 Node.js 当前版本与 nvm 已装列表
     */
    public static Snapshot nodeSnapshot() {
        Snapshot s = new Snapshot();
        s.tool = "node";
        String cur = CmdExec.execLine("node --version");
        if (cur != null && cur.startsWith("v")) {
            cur = cur.substring(1);
        }
        s.current = cur;
        s.versions = listNvmVersions();
        return s;
    }

    /**
     * 切换 Node.js 版本（nvm use 异步生效，等待后校验）
     *
     * @return 结果描述
     */
    public static String switchNode(String version) {
        LogManager.log("→ 切换 Node.js 到 " + version + " ...");
        CmdExec.exec("start /min nvm use " + version + " 2>&1");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        String current = CmdExec.execLine("node --version");
        if (current != null && current.contains(version)) {
            LogManager.log("  ✓ Node.js 已切换至 " + version);
            return "已切换至 " + version;
        }
        String msg = "切换后当前版本: " + (current != null ? current : "未知") + "，请检查 nvm 是否正常";
        LogManager.log("  ⚠ " + msg);
        return msg;
    }

    /**
     * 安装 Node.js 版本（耗时操作，调用方放后台线程执行）
     */
    public static String installNode(String version) {
        LogManager.log("→ nvm install " + version + " ...（可能需要几分钟）");
        return CmdExec.exec("nvm install " + version);
    }

    /**
     * 从 nvm settings.txt 解析安装根目录
     */
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
            } catch (Exception ignored) {
            }
        }
        String home = System.getenv("NVM_HOME");
        return (home != null) ? home : "F:\\nvm";
    }

    /**
     * 扫描 nvm 根目录下的 v* 目录得到已装版本，降序排列
     */
    private static List<String> listNvmVersions() {
        List<String> list = new ArrayList<>();
        File nvmRoot = new File(getNvmRoot());
        File[] dirs = nvmRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("v"));
        if (dirs != null) {
            for (File d : dirs) {
                list.add(d.getName().substring(1));
            }
        }
        list.sort(VersionService::compareVersionsDesc);
        return list;
    }

    // ==========================================
    //  Python (pyenv)
    // ==========================================

    /**
     * 查询 Python 当前版本与 pyenv 已装列表
     */
    public static Snapshot pythonSnapshot() {
        Snapshot s = new Snapshot();
        s.tool = "python";
        String cur = CmdExec.execLine("python --version");
        if (cur != null && cur.toLowerCase().startsWith("python ")) {
            cur = cur.substring(7).trim();
        }
        s.current = cur;
        s.versions = parsePyenvVersions();
        return s;
    }

    /**
     * 切换 Python 全局版本
     */
    public static String switchPython(String version) {
        LogManager.log("→ 切换 Python 到 " + version + " ...");
        CmdExec.exec("pyenv global " + version);
        CmdExec.exec("pyenv rehash");
        LogManager.log("  ✓ Python 已切换至 " + version);
        return "已切换至 " + version;
    }

    /**
     * 安装 Python 版本（耗时操作，调用方放后台线程执行）
     */
    public static String installPython(String version) {
        LogManager.log("→ pyenv install " + version + " ...（可能需要几分钟）");
        return CmdExec.exec("pyenv install " + version + " -q");
    }

    /**
     * 解析 pyenv versions 输出中的 x.y.z 版本号
     */
    private static List<String> parsePyenvVersions() {
        List<String> list = new ArrayList<>();
        String output = CmdExec.exec("pyenv versions");
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
        list.sort(VersionService::compareVersionsDesc);
        return list;
    }

    // ==========================================
    //  通用
    // ==========================================

    /**
     * 版本号比较器（降序）：按数字段逐段比较
     */
    private static int compareVersionsDesc(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = segment(pa, i);
            int vb = segment(pb, i);
            if (va != vb) {
                return Integer.compare(vb, va);
            }
        }
        return 0;
    }

    /** 取分段数字，越界或非数字段记 0 */
    private static int segment(String[] parts, int idx) {
        if (idx >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[idx].replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
