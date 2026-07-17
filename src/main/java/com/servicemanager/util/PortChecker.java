package com.servicemanager.util;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 端口连通性检测工具
 */
public class PortChecker {

    private static final int DEFAULT_TIMEOUT_MS = 500;

    /**
     * 检测指定端口是否可达（500ms 超时）
     *
     * @param port 端口号
     * @return true 端口可通
     */
    public static boolean isPortOpen(int port) {
        return isPortOpen("127.0.0.1", port, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 检测指定主机端口是否可达
     *
     * @param host      主机地址
     * @param port      端口号
     * @param timeoutMs 超时毫秒
     * @return true 端口可通
     */
    public static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
