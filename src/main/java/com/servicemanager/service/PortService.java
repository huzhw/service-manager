package com.servicemanager.service;

import com.servicemanager.util.CmdExec;
import com.servicemanager.util.LogManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 端口工具服务 — 端口占用查询、进程强杀、常用端口批量查杀
 * <p>
 * 业务逻辑自原 ui.PortToolPanel 抽取；netstat 改为单次全量转储解析，
 * 避免"每个端口跑一次 netstat"的重复外部 IO。
 */
public class PortService {

    /** 常用端口快捷查杀清单 */
    public static final int[] COMMON_PORTS = {
            3000, 3001, 8051, 8021, 8087, 8095, 8091, 8080, 8000, 8686, 10535
    };

    /** 单个端口的占用情况 */
    public static class Usage {
        public int port;
        public boolean occupied;
        public String pid;         // LISTENING 进程 PID
        public String processName; // 进程名
    }

    /**
     * 查询单个端口的占用进程
     */
    public static Usage find(int port) {
        Usage u = new Usage();
        u.port = port;
        Map<Integer, String> listeners = listenMap();
        String pid = listeners.get(port);
        if (pid != null) {
            u.occupied = true;
            u.pid = pid;
            u.processName = findProcName(pid);
        }
        return u;
    }

    /**
     * 扫描常用端口占用情况（一次 netstat 全量解析 + 按 PID 去重查进程名）
     */
    public static List<Usage> scanCommon() {
        Map<Integer, String> listeners = listenMap();
        List<Usage> result = new ArrayList<>();
        for (int port : COMMON_PORTS) {
            Usage u = new Usage();
            u.port = port;
            String pid = listeners.get(port);
            if (pid != null) {
                u.occupied = true;
                u.pid = pid;
                u.processName = procNameCache(pid);
            }
            result.add(u);
        }
        return result;
    }

    /**
     * 强杀指定 PID
     *
     * @return 成功返回 null，失败返回错误信息
     */
    public static String killPid(String pid) {
        LogManager.log("→ 强制终止 PID " + pid + " ...");
        String output = CmdExec.exec("taskkill /F /PID " + pid);
        if (output != null && output.contains("成功")) {
            LogManager.log("  ✓ 已终止 PID " + pid);
            return null;
        }
        String err = output != null ? output.replace("\n", " ") : "无输出";
        LogManager.log("  ✗ 终止失败: " + err.trim());
        return err.trim();
    }

    /**
     * 杀掉占用指定端口的进程
     *
     * @return 该端口杀完后的最新占用状态
     */
    public static Usage killPort(int port) {
        Usage before = find(port);
        if (before.occupied && before.pid != null) {
            killPid(before.pid);
        }
        return find(port);
    }

    /**
     * 一键批量查杀常用端口
     *
     * @return 汇总结果描述
     */
    public static String killAllCommon() {
        Map<Integer, String> listeners = listenMap();
        int killed = 0;
        int clean = 0;
        int failed = 0;
        for (int port : COMMON_PORTS) {
            String pid = listeners.get(port);
            if (pid == null) {
                clean++;
                continue;
            }
            String name = findProcName(pid);
            LogManager.log("→ 端口 " + port + " → PID " + pid
                    + (name != null ? " (" + name + ")" : "") + " → taskkill ...");
            String err = killPid(pid);
            if (err == null) {
                killed++;
            } else {
                failed++;
            }
        }
        String summary = "已释放 " + killed + " 个端口"
                + (clean > 0 ? "，" + clean + " 个原本空闲" : "")
                + (failed > 0 ? "，" + failed + " 个失败" : "");
        LogManager.log("✓ 批量查杀完成: " + summary);
        return summary;
    }

    // ==========================================
    //  内部工具
    // ==========================================

    /**
     * 一次 netstat -ano 全量转储，解析出 [监听端口 → PID] 映射。
     * 相比原实现"每端口一次 netstat"，避免循环内重复外部 IO。
     */
    private static Map<Integer, String> listenMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        String output = CmdExec.exec("netstat -ano");
        if (output == null) {
            return map;
        }
        for (String line : output.split("\n")) {
            line = line.trim();
            // 只关心 LISTENING 行：协议 本地地址 远程地址 状态 PID
            if (!line.contains("LISTENING")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 5) {
                continue;
            }
            String localAddr = parts[parts.length - 3];
            String state = parts[parts.length - 2];
            String pid = parts[parts.length - 1];
            if (!"LISTENING".equals(state)) {
                continue;
            }
            int colon = localAddr.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            try {
                map.putIfAbsent(Integer.parseInt(localAddr.substring(colon + 1)), pid);
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    /** 同一轮扫描内的 PID → 进程名缓存，避免同一 PID 重复 tasklist */
    private static final Map<String, String> PROC_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static String procNameCache(String pid) {
        return PROC_CACHE.computeIfAbsent(pid, PortService::findProcName);
    }

    /**
     * tasklist 反查 PID 对应的进程名
     */
    private static String findProcName(String pid) {
        String output = CmdExec.exec("tasklist /FI \"PID eq " + pid + "\" /NH");
        if (output != null) {
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.contains(pid)) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 1) {
                        return parts[0];
                    }
                }
            }
        }
        return null;
    }
}
