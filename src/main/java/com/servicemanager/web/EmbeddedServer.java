package com.servicemanager.web;

import com.servicemanager.util.LogManager;
import com.servicemanager.web.api.FileAssocApi;
import com.servicemanager.web.api.LogsApi;
import com.servicemanager.web.api.PortsApi;
import com.servicemanager.web.api.ServicesApi;
import com.servicemanager.web.api.VersionsApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 内嵌 HTTP 服务器 — 同 JVM 内为 React 前端提供静态页与 REST API
 * <p>
 * 安全约束：
 * ① 仅绑定 127.0.0.1 回环地址，局域网不可达
 * ② /api/* 全部要求 token（启动时随机生成，见控制台/日志），静态资源不校验
 * ③ 端口在 46815~46834 间顺延选取，全部占用则启动失败
 */
public class EmbeddedServer {

    /** 端口扫描起点（5 位数冷门段，避开 Windows 临时端口区间 49152+ 与常见软件端口） */
    private static final int PORT_START = 46815;
    private static final int PORT_END = 46834;

    private HttpServer server;
    private int port;
    private String token;
    private final StaticHandler staticHandler = new StaticHandler();

    /**
     * 启动服务器
     *
     * @throws IOException 全部候选端口被占或绑定失败
     */
    public void start() throws IOException {
        this.token = UUID.randomUUID().toString().replace("-", "");
        IOException lastError = null;
        for (int p = PORT_START; p <= PORT_END; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
                port = p;
                break;
            } catch (BindException e) {
                lastError = e;
            }
        }
        if (server == null) {
            throw new IOException("端口 " + PORT_START + "~" + PORT_END + " 全部被占用", lastError);
        }

        server.createContext("/", this::dispatch);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "web");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    /**
     * 停止服务器（应用退出时调用）
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    /**
     * 前端完整访问地址（带 token）
     */
    public String url() {
        return "http://127.0.0.1:" + port + "/?token=" + token;
    }

    // ==========================================
    //  路由分发
    // ==========================================

    /**
     * 总入口：/api/* 校验 token 后路由到各 API；其余走静态处理
     */
    private void dispatch(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                if (!tokenValid(ex)) {
                    // 401 留痕：静默拒绝会让"点击无效"无法定位（旧页面/旧实例残留的典型特征）
                    LogManager.log("⚠ API 401: " + ex.getRequestMethod() + " " + path
                            + "（token 不匹配）");
                    HttpUtil.error(ex, 401, "token 无效，请从托盘重新打开面板");
                    return;
                }
                Handler h = ROUTES.get(ex.getRequestMethod() + " " + path);
                if (h == null) {
                    HttpUtil.error(ex, 404, "接口不存在: " + path);
                    return;
                }
                h.handle(ex);
            } else {
                staticHandler.handle(ex);
            }
        } catch (Exception e) {
            // 兜底：任何处理器异常都转 500，避免连接悬挂
            HttpUtil.error(ex, 500, "服务内部错误: " + e.getMessage());
        }
    }

    /**
     * token 校验：请求头 X-Token 或查询参数 token 二选一匹配
     */
    private boolean tokenValid(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("X-Token");
        if (token.equals(header)) {
            return true;
        }
        return token.equals(HttpUtil.query(ex).get("token"));
    }

    /** 简化函数签名 */
    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    // ==========================================
    //  路由表：METHOD + PATH → 处理器
    // ==========================================

    private static final Map<String, Handler> ROUTES = new HashMap<>();

    static {
        ROUTES.put("GET /api/services", ServicesApi::list);
        ROUTES.put("POST /api/services/action", ex ->
                ServicesApi.action(ex, HttpUtil.body(ex)));
        ROUTES.put("POST /api/services/open-dir", ex ->
                ServicesApi.openDir(ex, HttpUtil.body(ex)));

        ROUTES.put("GET /api/versions/node", VersionsApi::node);
        ROUTES.put("GET /api/versions/python", VersionsApi::python);
        ROUTES.put("POST /api/versions/switch", ex ->
                VersionsApi.switchVersion(ex, HttpUtil.body(ex)));
        ROUTES.put("POST /api/versions/install", ex ->
                VersionsApi.installVersion(ex, HttpUtil.body(ex)));

        ROUTES.put("GET /api/ports/find", PortsApi::find);
        ROUTES.put("GET /api/ports/common", PortsApi::common);
        ROUTES.put("POST /api/ports/kill", ex ->
                PortsApi.killPid(ex, HttpUtil.body(ex)));
        ROUTES.put("POST /api/ports/kill-port", ex ->
                PortsApi.killPort(ex, HttpUtil.body(ex)));
        ROUTES.put("POST /api/ports/kill-common", PortsApi::killCommon);

        ROUTES.put("GET /api/fileassoc", FileAssocApi::list);
        ROUTES.put("POST /api/fileassoc/set", ex ->
                FileAssocApi.set(ex, HttpUtil.body(ex)));

        ROUTES.put("GET /api/logs/recent", LogsApi::recent);
        ROUTES.put("GET /api/logs/stream", LogsApi::stream);
    }
}
