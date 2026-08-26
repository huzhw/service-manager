package com.servicemanager.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态资源处理器 — 从 classpath 的 /web 目录对外提供前端构建产物
 * <p>
 * 规则：
 * ① "/" 与无扩展名路径回退到 index.html（SPA 兼容）
 * ② index.html 缺失（未执行前端构建）时返回内置引导页提示
 * ③ 拒绝含 ".." 的路径，防目录穿越
 */
public class StaticHandler {

    /** 扩展名 → Content-Type */
    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("js", "application/javascript; charset=utf-8");
        MIME.put("mjs", "application/javascript; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("svg", "image/svg+xml");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("ico", "image/x-icon");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("map", "application/json; charset=utf-8");
    }

    /**
     * 处理一次静态资源请求
     */
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.contains("..")) {
            HttpUtil.error(ex, 403, "非法路径");
            return;
        }
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        // 默认首页改用内置极简原生页（零框架，规避老 WebView 事件层失效问题）；
        // React 构建产物仍保留在 /web 下，删掉此分支即可整体回退
        if ("/index.html".equals(path)) {
            respond(ex, 200, SimplePage.HTML.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8");
            return;
        }

        byte[] body = readResource("/web" + path);
        if (body == null && !hasExtension(path)) {
            // SPA 无扩展名路由回退
            body = readResource("/web/index.html");
        }
        if (body == null && "/index.html".equals(path)) {
            // 前端尚未构建：返回内置引导页
            body = placeholderHtml().getBytes(StandardCharsets.UTF_8);
            respond(ex, 200, body, "text/html; charset=utf-8");
            return;
        }
        if (body == null) {
            HttpUtil.error(ex, 404, "资源不存在: " + path);
            return;
        }
        respond(ex, 200, body, contentType(path));
    }

    // ==========================================
    //  内部工具
    // ==========================================

    /**
     * 读 classpath 资源为字节数组；不存在返回 null
     */
    private byte[] readResource(String resPath) throws IOException {
        try (InputStream in = StaticHandler.class.getResourceAsStream(resPath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash && dot < path.length() - 1;
    }

    private static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
        return MIME.getOrDefault(ext, "application/octet-stream");
    }

    private static void respond(HttpExchange ex, int code, byte[] body, String type)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /**
     * 前端未构建时的占位页
     */
    private static String placeholderHtml() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<title>Service Manager</title>"
                + "<style>body{background:#0a0e17;color:#c9d4e3;font-family:'Microsoft YaHei',sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}"
                + ".box{border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.03);"
                + "padding:48px 64px;border-radius:16px;text-align:center}"
                + "h1{color:#5b8def;font-size:20px}code{color:#34d399}</style></head>"
                + "<body><div class='box'><h1>⚙ 服务管理面板</h1>"
                + "<p>前端资源尚未构建，界面不可用。</p>"
                + "<p>请在项目目录执行 <code>build-web.bat</code> 后重启本程序。</p>"
                + "</div></body></html>";
    }
}
