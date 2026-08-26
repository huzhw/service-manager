package com.servicemanager.web.api;

import com.google.gson.reflect.TypeToken;
import com.servicemanager.service.FileAssocService;
import com.servicemanager.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件关联 API — 常用扩展名默认打开方式查看与修改
 *
 * GET  /api/fileassoc       全部扩展名当前关联
 * POST /api/fileassoc/set   {"ext":".md","exePath":"C:\\...\\xx.exe"}
 */
public final class FileAssocApi {

    private static final Type REQ_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private FileAssocApi() {
    }

    /**
     * 全部关联列表（首次调用约需数秒：一次 assoc + 若干 ftype）
     */
    public static void list(HttpExchange ex) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FileAssocService.Row r : FileAssocService.listAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ext", r.ext);
            m.put("desc", r.desc);
            m.put("program", r.program);
            rows.add(m);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("rows", rows);
        HttpUtil.ok(ex, extra);
    }

    /**
     * 设置关联（需要管理员权限运行后端）
     */
    public static void set(HttpExchange ex, String body) throws IOException {
        Map<String, String> req = HttpUtil.GSON.fromJson(body, REQ_TYPE);
        String ext = req.getOrDefault("ext", "").trim();
        String exePath = req.getOrDefault("exePath", "").trim();
        String err = FileAssocService.setAssoc(ext, exePath);
        if (err != null) {
            HttpUtil.error(ex, 400, err);
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", ext + " 已关联到 " + exePath);
        HttpUtil.ok(ex, extra);
    }
}
