package com.servicemanager.web.api;

import com.google.gson.reflect.TypeToken;
import com.servicemanager.service.VersionService;
import com.servicemanager.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 版本管理 API — nvm (Node.js) / pyenv (Python)
 *
 * GET  /api/versions/node     Node 快照 {current, versions[]}
 * GET  /api/versions/python   Python 快照
 * POST /api/versions/switch   {"tool":"node|python","version":"x.y.z"}
 * POST /api/versions/install  {"tool":"node|python","version":"x.y.z"}
 */
public final class VersionsApi {

    private static final Type REQ_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private VersionsApi() {
    }

    /**
     * Node.js 版本快照
     */
    public static void node(HttpExchange ex) throws IOException {
        VersionService.Snapshot s = VersionService.nodeSnapshot();
        HttpUtil.ok(ex, snapshotExtra(s));
    }

    /**
     * Python 版本快照
     */
    public static void python(HttpExchange ex) throws IOException {
        VersionService.Snapshot s = VersionService.pythonSnapshot();
        HttpUtil.ok(ex, snapshotExtra(s));
    }

    /**
     * 切换版本（同步执行；nvm use 自带 2 秒等待校验）
     */
    public static void switchVersion(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String tool = req.getOrDefault("tool", "");
        String version = req.getOrDefault("version", "").trim();
        if (!validVersion(version)) {
            HttpUtil.error(ex, 400, "版本号格式无效: " + version);
            return;
        }
        String result;
        if ("node".equals(tool)) {
            result = VersionService.switchNode(version);
        } else if ("python".equals(tool)) {
            result = VersionService.switchPython(version);
        } else {
            HttpUtil.error(ex, 400, "未知工具: " + tool);
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", result);
        HttpUtil.ok(ex, extra);
    }

    /**
     * 安装版本（耗时操作放后台线程，立即返回；完成后日志可见）
     */
    public static void installVersion(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String tool = req.getOrDefault("tool", "");
        String version = req.getOrDefault("version", "").trim();
        if (!validVersion(version)) {
            HttpUtil.error(ex, 400, "版本号格式无效: " + version);
            return;
        }
        boolean isNode = "node".equals(tool);
        if (!isNode && !"python".equals(tool)) {
            HttpUtil.error(ex, 400, "未知工具: " + tool);
            return;
        }
        Thread worker = new Thread(() -> {
            if (isNode) {
                VersionService.installNode(version);
            } else {
                VersionService.installPython(version);
            }
            com.servicemanager.util.LogManager.log(
                    "✓ 安装流程结束: " + tool + " " + version + "，可刷新查看");
        }, "ver-install");
        worker.setDaemon(true);
        worker.start();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", "安装已在后台开始，完成后可在日志中查看进度");
        HttpUtil.ok(ex, extra);
    }

    // ==========================================
    //  内部工具
    // ==========================================

    private static Map<String, Object> snapshotExtra(VersionService.Snapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool", s.tool);
        m.put("current", s.current);
        m.put("versions", s.versions);
        return m;
    }

    /** 宽松校验：数字段版本号，如 18.17.0 / 3.11.9 / 22 */
    private static boolean validVersion(String v) {
        return v.matches("\\d+(\\.\\d+){0,3}");
    }
}
