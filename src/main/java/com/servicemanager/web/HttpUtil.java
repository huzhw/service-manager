package com.servicemanager.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 工具集 — 响应输出、请求解析
 */
public final class HttpUtil {

    /** 共享 Gson 实例（线程安全） */
    public static final Gson GSON = new GsonBuilder().create();

    private HttpUtil() {
    }

    /**
     * 输出 JSON 响应（UTF-8）
     */
    public static void json(HttpExchange ex, int code, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 输出成功响应：{"ok":true, ...附加字段}
     */
    public static void ok(HttpExchange ex, Map<String, Object> extra) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        if (extra != null) {
            body.putAll(extra);
        }
        json(ex, 200, body);
    }

    /**
     * 输出错误响应：{"ok":false,"error":msg}
     */
    public static void error(HttpExchange ex, int code, String msg) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", msg);
        json(ex, code, body);
    }

    /**
     * 解析查询串为键值对（URL 解码）
     */
    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(pair), "");
            } else {
                map.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    /**
     * 读取请求体文本（UTF-8）
     */
    public static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
