package com.servicemanager.web.api;

import com.google.gson.reflect.TypeToken;
import com.servicemanager.service.PortService;
import com.servicemanager.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 端口工具 API — 占用查询、进程强杀、常用端口批量查杀
 *
 * GET  /api/ports/find?port=8080   单端口占用查询
 * GET  /api/ports/common           常用端口扫描
 * POST /api/ports/kill             {"pid":"1234"} 强杀 PID
 * POST /api/ports/kill-port        {"port":8080} 释放端口
 * POST /api/ports/kill-common      一键批量查杀常用端口
 */
public final class PortsApi {

    private static final Type REQ_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private PortsApi() {
    }

    /**
     * 单端口查询
     */
    public static void find(HttpExchange ex) throws IOException {
        Integer port = portParam(ex);
        if (port == null) {
            return; // portParam 已输出错误
        }
        PortService.Usage u = PortService.find(port);
        HttpUtil.ok(ex, usageExtra(u));
    }

    /**
     * 常用端口扫描
     */
    public static void common(HttpExchange ex) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PortService.Usage u : PortService.scanCommon()) {
            rows.add(usageExtra(u));
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ports", rows);
        HttpUtil.ok(ex, extra);
    }

    /**
     * 强杀指定 PID
     */
    public static void killPid(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String pid = req.getOrDefault("pid", "").trim();
        if (!pid.matches("\\d{1,7}")) {
            HttpUtil.error(ex, 400, "PID 无效: " + pid);
            return;
        }
        String err = PortService.killPid(pid);
        Map<String, Object> extra = new LinkedHashMap<>();
        if (err == null) {
            extra.put("message", "已终止 PID " + pid);
            HttpUtil.ok(ex, extra);
        } else {
            HttpUtil.error(ex, 500, err);
        }
    }

    /**
     * 杀掉占用端口的进程并返回最新状态
     */
    public static void killPort(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String raw = req.getOrDefault("port", "").trim();
        Integer port = parsePort(raw);
        if (port == null) {
            HttpUtil.error(ex, 400, "端口号无效: " + raw);
            return;
        }
        PortService.Usage u = PortService.killPort(port);
        Map<String, Object> extra = usageExtra(u);
        extra.put("message", u.occupied ? "释放失败，端口仍被占用" : "端口 " + port + " 已释放");
        HttpUtil.ok(ex, extra);
    }

    /**
     * 一键批量查杀常用端口（同步执行，约几秒）
     */
    public static void killCommon(HttpExchange ex) throws IOException {
        // 批量查杀是连续外部 IO，放后台线程避免占死 HTTP 线程池？——保持同步：
        // 原实现即顺序执行，总耗时可接受（11 个端口 × 毫秒级命令）
        String summary = PortService.killAllCommon();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PortService.Usage u : PortService.scanCommon()) {
            rows.add(usageExtra(u));
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", summary);
        extra.put("ports", rows);
        HttpUtil.ok(ex, extra);
    }

    // ==========================================
    //  内部工具
    // ==========================================

    private static Map<String, Object> usageExtra(PortService.Usage u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", u.port);
        m.put("occupied", u.occupied);
        m.put("pid", u.pid);
        m.put("processName", u.processName);
        return m;
    }

    /** 从查询串取合法端口；非法时直接输出 400 并返回 null */
    private static Integer portParam(HttpExchange ex) throws IOException {
        String raw = HttpUtil.query(ex).getOrDefault("port", "").trim();
        Integer port = parsePort(raw);
        if (port == null) {
            HttpUtil.error(ex, 400, "端口号无效: " + raw);
        }
        return port;
    }

    private static Integer parsePort(String raw) {
        if (!raw.matches("\\d{1,5}")) {
            return null;
        }
        try {
            int p = Integer.parseInt(raw);
            return (p >= 1 && p <= 65535) ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
