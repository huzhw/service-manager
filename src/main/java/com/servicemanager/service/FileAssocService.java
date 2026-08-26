package com.servicemanager.service;

import com.servicemanager.util.CmdExec;
import com.servicemanager.util.LogManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件关联服务 — 查看和修改常用文件扩展名的默认打开方式
 * <p>
 * 业务逻辑自原 ui.FileAssocPanel 抽取。查询改为一次 assoc 全量转储
 * + 按 ProgID 去重 ftype，避免逐扩展名循环执行外部命令。
 */
public class FileAssocService {

    /** 常用扩展名 → 描述（保持展示顺序） */
    public static final Map<String, String> EXTENSIONS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // 文本 / 代码
        m.put(".txt", "文本文件");
        m.put(".md", "Markdown");
        m.put(".java", "Java 源码");
        m.put(".json", "JSON");
        m.put(".xml", "XML");
        m.put(".py", "Python");
        m.put(".js", "JavaScript");
        m.put(".html", "网页");
        m.put(".css", "样式表");
        // Office
        m.put(".doc", "Word 文档 (97-2003)");
        m.put(".docx", "Word 文档");
        m.put(".xls", "Excel 表格 (97-2003)");
        m.put(".xlsx", "Excel 表格");
        m.put(".ppt", "PowerPoint (97-2003)");
        m.put(".pptx", "PowerPoint");
        m.put(".csv", "CSV 表格");
        m.put(".vsd", "Visio (97-2003)");
        m.put(".vsdx", "Visio 绘图");
        // 视频
        m.put(".mp4", "MP4 视频");
        m.put(".avi", "AVI 视频");
        m.put(".mkv", "MKV 视频");
        m.put(".mov", "MOV 视频");
        m.put(".wmv", "WMV 视频");
        m.put(".flv", "FLV 视频");
        m.put(".rmvb", "RMVB 视频");
        m.put(".webm", "WebM 视频");
        m.put(".ts", "TS 视频流");
        m.put(".m3u8", "m3u8 播放列表");
        m.put(".mpg", "MPEG 视频");
        m.put(".mpeg", "MPEG 视频");
        m.put(".3gp", "3GP 视频");
        m.put(".rm", "RM 视频");
        m.put(".m4v", "M4V 视频");
        // 其他
        m.put(".pdf", "PDF 文档");
        m.put(".zip", "压缩包");
        m.put(".png", "图片 PNG");
        m.put(".jpg", "图片 JPG");
        EXTENSIONS = Collections.unmodifiableMap(m);
    }

    /** 单行关联信息 */
    public static class Row {
        public String ext;      // 扩展名，如 .md
        public String desc;     // 中文描述
        public String program;  // 当前默认打开程序名
    }

    /**
     * 查询全部常用扩展名的当前关联程序。
     * 实现：assoc 无参全量转储一次 → 过滤出关注的扩展名 → 对去重后的 ProgID 逐一 ftype。
     */
    public static List<Row> listAll() {
        // 1. 全量 ext → progId 映射（一次外部调用）
        Map<String, String> extToProgId = new LinkedHashMap<>();
        String assocAll = CmdExec.exec("assoc");
        if (assocAll != null) {
            for (String line : assocAll.split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                extToProgId.put(line.substring(0, eq).trim().toLowerCase(),
                        line.substring(eq + 1).trim());
            }
        }

        // 2. 关注扩展名 → progId，收集需要反查的 progId 集合
        Set<String> progIds = new HashSet<>();
        Map<String, String> targetExtProgId = new LinkedHashMap<>();
        for (String ext : EXTENSIONS.keySet()) {
            String progId = extToProgId.get(ext);
            targetExtProgId.put(ext, progId);
            if (progId != null && !progId.isEmpty()) {
                progIds.add(progId);
            }
        }

        // 3. 去重后的 progId → 程序名（每个只查一次）
        Map<String, String> progIdToName = new LinkedHashMap<>();
        for (String progId : progIds) {
            progIdToName.put(progId, queryFtype(progId));
        }

        // 4. 组装结果行
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : EXTENSIONS.entrySet()) {
            Row r = new Row();
            r.ext = e.getKey();
            r.desc = e.getValue();
            String progId = targetExtProgId.get(e.getKey());
            if (progId == null || progId.isEmpty()) {
                r.program = "未关联";
            } else {
                r.program = progIdToName.getOrDefault(progId, progId);
            }
            rows.add(r);
        }
        return rows;
    }

    /**
     * 设置扩展名默认打开方式（ftype 注册程序 + assoc 挂接扩展名）
     *
     * @param ext     扩展名（必须在常用清单内，如 .md）
     * @param exePath exe 完整路径
     * @return 成功返回 null，失败返回错误原因
     */
    public static String setAssoc(String ext, String exePath) {
        // 参数校验：ext 白名单、路径必须是存在的 exe、禁止引号注入
        if (ext == null || !EXTENSIONS.containsKey(ext.toLowerCase())) {
            return "不支持的扩展名: " + ext;
        }
        if (exePath == null || exePath.contains("\"") || exePath.contains("'")) {
            return "路径含非法字符";
        }
        File exe = new File(exePath.trim());
        if (!exe.isFile() || !exePath.trim().toLowerCase().endsWith(".exe")) {
            return "exe 路径无效或文件不存在";
        }

        String path = exe.getAbsolutePath();
        LogManager.log("→ 设置 " + ext + " 默认程序为 " + exe.getName() + " ...");
        String progId = "ServiceManager" + ext.replace(".", "_");
        String out1 = CmdExec.exec("ftype " + progId + "=\"" + path + "\" \"%1\"");
        String out2 = CmdExec.exec("assoc " + ext + "=" + progId);

        if (out1 != null && out2 != null) {
            LogManager.log("  ✓ " + ext + " 已关联到 " + exe.getName());
            return null;
        }
        LogManager.log("  ✗ 修改失败，请以管理员权限运行");
        return "修改失败，请确认程序以管理员权限运行";
    }

    /**
     * ftype 反查 ProgID 的启动命令并提取 exe 名
     */
    private static String queryFtype(String progId) {
        String ftypeOut = CmdExec.exec("ftype " + progId);
        if (ftypeOut == null || !ftypeOut.contains("=")) {
            return progId;
        }
        String cmd = ftypeOut.substring(ftypeOut.indexOf("=") + 1).trim();
        return extractExeName(cmd);
    }

    /**
     * 从启动命令串提取 exe 文件名（支持带引号路径）
     */
    private static String extractExeName(String cmd) {
        if (cmd == null || cmd.isEmpty()) {
            return "未知";
        }
        String first = cmd.trim();
        if (first.startsWith("\"")) {
            int end = first.indexOf("\"", 1);
            if (end > 0) {
                first = first.substring(1, end);
            }
        } else {
            int space = first.indexOf(' ');
            if (space > 0) {
                first = first.substring(0, space);
            }
        }
        int lastSep = Math.max(first.lastIndexOf('\\'), first.lastIndexOf('/'));
        if (lastSep >= 0) {
            first = first.substring(lastSep + 1);
        }
        return first;
    }
}
