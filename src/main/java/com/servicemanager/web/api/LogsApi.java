package com.servicemanager.web.api;

import com.servicemanager.util.LogBus;
import com.servicemanager.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志 API — 历史回放 + SSE 实时推流
 *
 * GET /api/logs/recent   最近 N 行
 * GET /api/logs/stream   SSE：先发历史快照，再持续推送；15s 心跳保活
 */
public final class LogsApi {

    /** 心跳周期（秒） */
    private static final int HEARTBEAT_SECONDS = 15;

    /** 全部活跃 SSE 连接 */
    private static final CopyOnWriteArrayList<SseClient> CLIENTS = new CopyOnWriteArrayList<>();

    /** 共享心跳调度器（懒加载） */
    private static volatile ScheduledExecutorService heartbeat;

    private LogsApi() {
    }

    /**
     * 最近日志（JSON 数组）
     */
    public static void recent(HttpExchange ex) throws IOException {
        List<String> lines = LogBus.get().recent();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("lines", lines);
        HttpUtil.ok(ex, extra);
    }

    /**
     * SSE 实时日志流
     */
    public static void stream(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        // content-length 未知 → 0 表示分块发送
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();

        SseClient client = new SseClient(ex, out);
        try {
            // 1. 先回放历史快照，避免前端进页后到订阅前的空窗
            StringBuilder sb = new StringBuilder();
            for (String line : LogBus.get().recent()) {
                sb.append("data: ").append(escape(line)).append("\n\n");
            }
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            closeQuietly(client);
            return;
        }

        // 2. 订阅实时推送
        LogBus.get().subscribe(client);
        CLIENTS.add(client);

        ensureHeartbeat();
    }

    // ==========================================
    //  内部实现
    // ==========================================

    /**
     * 懒启动心跳：定期向所有连接写注释行，写失败即清理该连接
     */
    private static synchronized void ensureHeartbeat() {
        if (heartbeat != null) {
            return;
        }
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleWithFixedDelay(() -> {
            for (SseClient c : CLIENTS) {
                if (!c.ping()) {
                    remove(c);
                }
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 清理一个连接：退订日志总线、关闭响应流、移出列表
     */
    private static void remove(SseClient client) {
        LogBus.get().unsubscribe(client);
        CLIENTS.remove(client);
        client.close();
    }

    private static void closeQuietly(SseClient client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
        LogBus.get().unsubscribe(client);
        CLIENTS.remove(client);
    }

    /**
     * 单个 SSE 连接封装：既是 LogBus 订阅者，又持有输出流
     */
    private static class SseClient implements LogBus.Subscriber {

        private final HttpExchange exchange;
        private final OutputStream out;

        SseClient(HttpExchange exchange, OutputStream out) {
            this.exchange = exchange;
            this.out = out;
        }

        @Override
        public void onLine(String line) {
            // 接口不允许抛受检异常；写失败包成非受检异常，
            // 由 LogBus.publish 统一捕获并退订本连接
            try {
                write("data: " + escape(line) + "\n\n");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        /**
         * 发送心跳注释行；返回 false 表示连接已死
         */
        boolean ping() {
            try {
                write(": ping\n\n");
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private synchronized void write(String payload) throws IOException {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        void close() {
            try {
                out.close();
            } catch (Exception ignored) {
            }
            exchange.close();
        }
    }

    /**
     * JSON 字符串转义日志行，保证 SSE data 帧单行完整
     */
    private static String escape(String line) {
        return HttpUtil.GSON.toJson(line == null ? "" : line);
    }
}
