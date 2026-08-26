package com.servicemanager.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志总线 — 内存环形缓冲 + 实时订阅
 * <p>
 * LogManager 双写改造：UI 回调换成 LogBus 订阅，SSE 推流给前端日志抽屉；
 * 环形缓冲保留最近 N 行供前端初次进入时回放。
 */
public class LogBus {

    /** 环形缓冲容量（行） */
    private static final int CAPACITY = 800;

    private static final LogBus INSTANCE = new LogBus();

    private final ArrayDeque<String> buffer = new ArrayDeque<>(CAPACITY);
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /** 单个订阅者（SSE 连接），异常时自动退订 */
    public interface Subscriber {
        void onLine(String line);
    }

    private LogBus() {
    }

    public static LogBus get() {
        return INSTANCE;
    }

    /**
     * 发布一行日志：入环形缓冲 + 推送给全部订阅者
     */
    public void publish(String line) {
        synchronized (buffer) {
            if (buffer.size() >= CAPACITY) {
                buffer.pollFirst();
            }
            buffer.addLast(line);
        }
        for (Subscriber s : subscribers) {
            try {
                s.onLine(line);
            } catch (Exception e) {
                // 写入失败的连接由 SSE 端自行清理，这里跳过即可
                unsubscribe(s);
            }
        }
    }

    /**
     * 取最近的历史日志快照
     */
    public List<String> recent() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /**
     * 订阅实时日志
     */
    public void subscribe(Subscriber s) {
        subscribers.add(s);
    }

    /**
     * 退订
     */
    public void unsubscribe(Subscriber s) {
        subscribers.remove(s);
    }
}
