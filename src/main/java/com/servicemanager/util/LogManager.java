package com.servicemanager.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * 统一日志管理器
 * <p>
 * 日志双写：① UI 面板回调 ② ./logs/ 目录文件（每日轮转，保留 3 天）。
 */
public class LogManager {

    private static final File LOG_DIR = new File("logs");
    private static final File LOG_FILE = new File(LOG_DIR, "service-manager.log");

    /** 日志保留天数 */
    private static final int KEEP_DAYS = 3;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Consumer<String> uiLogger;
    private static String currentDate;
    private static final Object LOCK = new Object();

    /**
     * 初始化（MainFrame 构造时调用一次）
     */
    public static void init(Consumer<String> uiLogger) {
        LogManager.uiLogger = uiLogger;
        if (!LOG_DIR.exists()) {
            LOG_DIR.mkdirs();
        }
        currentDate = DATE_FMT.format(new Date());
        log("日志系统就绪，保留 " + KEEP_DAYS + " 天，目录: " + LOG_DIR.getAbsolutePath());
    }

    /**
     * 写日志（线程安全）
     */
    public static void log(String msg) {
        // UI 输出
        if (uiLogger != null) {
            uiLogger.accept(msg);
        }

        // 文件输出
        String time = TIME_FMT.format(new Date());
        String line = "[" + time + "] " + msg + "\n";

        synchronized (LOCK) {
            try {
                rotateIfNeeded();
                try (FileOutputStream fos = new FileOutputStream(LOG_FILE, true);
                     OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    w.write(line);
                    w.flush();
                }
            } catch (Exception e) {
                System.err.println("日志写入失败: " + e.getMessage());
            }
        }
    }

    /**
     * 跨天则归档当日日志 + 清理过期
     */
    private static void rotateIfNeeded() {
        String today = DATE_FMT.format(new Date());
        if (today.equals(currentDate)) return;

        // 归档昨天的日志
        if (LOG_FILE.exists() && LOG_FILE.length() > 0) {
            File archived = new File(LOG_DIR, "service-manager-" + currentDate + ".log");
            LOG_FILE.renameTo(archived);
        }
        currentDate = today;

        // 清理 3 天前的日志
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600 * 1000;
        File[] files = LOG_DIR.listFiles((dir, name) ->
                name.startsWith("service-manager-") && name.endsWith(".log"));
        if (files != null) {
            for (File f : files) {
                if (f.lastModified() < cutoff) {
                    f.delete();
                }
            }
        }
    }
}
