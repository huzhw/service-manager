package com.servicemanager.web.api;

import com.google.gson.reflect.TypeToken;
import com.servicemanager.model.ServiceInfo;
import com.servicemanager.service.ServiceOrchestrator;
import com.servicemanager.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务管理 API — 列表查询、启停动作提交
 *
 * GET  /api/services          服务列表 + 汇总
 * POST /api/services/action   {"action":"start|stop|start-all|stop-all","name":"..."}
 */
public final class ServicesApi {

    /** 请求体类型：{"action":"...","name":"..."} */
    private static final Type REQ_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private ServicesApi() {
    }

    /**
     * 服务列表：返回前端表格所需的全部字段与统计
     */
    public static void list(HttpExchange ex) throws IOException {
        ServiceOrchestrator orch = ServiceOrchestrator.get();
        List<Map<String, Object>> rows = new ArrayList<>();
        int running = 0;
        int abnormal = 0;
        for (ServiceInfo svc : orch.getServices()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", svc.getName());
            row.put("type", svc.getType().name());
            row.put("typeLabel", svc.getType().getLabel());
            row.put("category", svc.getCategory());
            row.put("port", svc.getPort());
            row.put("status", svc.getStatus());
            row.put("pid", svc.getPid());
            row.put("version", svc.getVersion());
            row.put("workingDir", svc.getWorkingDir());
            row.put("groupName", svc.getGroupName());
            row.put("startTime", svc.getStartTime());
            rows.add(row);
            if ("RUNNING".equals(svc.getStatus())) {
                running++;
            } else if (!"STOPPED".equals(svc.getStatus())) {
                abnormal++;
            }
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("services", rows);
        extra.put("total", rows.size());
        extra.put("running", running);
        extra.put("stopped", Math.max(0, rows.size() - running - abnormal));
        extra.put("abnormal", abnormal);
        HttpUtil.ok(ex, extra);
    }

    /**
     * 提交启停动作：长任务立即返回 202，前端靠轮询列表看进度
     */
    public static void action(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String action = req.getOrDefault("action", "");
        String name = req.getOrDefault("name", "");
        ServiceOrchestrator orch = ServiceOrchestrator.get();

        boolean submitted;
        switch (action) {
            case "start-all":
                submitted = orch.startAll();
                break;
            case "stop-all":
                submitted = orch.stopAll();
                break;
            case "start":
            case "stop": {
                ServiceInfo svc = findByName(name);
                if (svc == null) {
                    HttpUtil.error(ex, 404, "服务不存在: " + name);
                    return;
                }
                submitted = orch.triggerGroupAction(svc, "stop".equals(action));
                break;
            }
            default:
                HttpUtil.error(ex, 400, "未知动作: " + action);
                return;
        }
        if (!submitted) {
            HttpUtil.error(ex, 409, "已有批量任务进行中，请稍候");
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", "任务已提交");
        HttpUtil.json(ex, 202, mergeOk(extra));
    }

    /**
     * 打开服务工作目录的资源管理器窗口（对应旧版表格的目录按钮）
     */
    public static void openDir(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        ServiceInfo svc = findByName(req.getOrDefault("name", ""));
        if (svc == null) {
            HttpUtil.error(ex, 404, "服务不存在");
            return;
        }
        String dir = svc.getWorkingDir();
        if (dir == null || dir.isEmpty()) {
            HttpUtil.error(ex, 400, "该服务未配置工作目录");
            return;
        }
        if (!new java.io.File(dir).isDirectory()) {
            HttpUtil.error(ex, 400, "目录不存在: " + dir);
            return;
        }
        try {
            new ProcessBuilder("explorer", dir).start();
        } catch (IOException e) {
            HttpUtil.error(ex, 500, "打开失败: " + e.getMessage());
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", "已打开 " + dir);
        HttpUtil.ok(ex, extra);
    }

    // ==========================================
    //  内部工具
    // ==========================================

    private static ServiceInfo findByName(String name) {
        for (ServiceInfo svc : ServiceOrchestrator.get().getServices()) {
            if (svc.getName().equals(name)) {
                return svc;
            }
        }
        return null;
    }

    private static Map<String, Object> mergeOk(Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.putAll(extra);
        return body;
    }
}
