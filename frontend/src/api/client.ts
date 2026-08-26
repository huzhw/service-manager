/**
 * API 客户端 — 统一携带 token，统一错误处理
 */
import type {
    ApiBase,
    AssocRow,
    CommonPortsResp,
    FileAssocResp,
    PortUsage,
    RecentLogsResp,
    ServicesResp,
    VersionSnapshotResp,
} from './types';

const TOKEN_KEY = 'sm_token';

/** 从 URL ?token= 中捕获并记忆（WebView 每次启动都会带新 token） */
export function captureToken(): void {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    if (token) {
        sessionStorage.setItem(TOKEN_KEY, token);
        // 清掉地址栏里的 token，避免截图/投屏泄露
        params.delete('token');
        const rest = params.toString();
        const newUrl = window.location.pathname + (rest ? '?' + rest : '') + window.location.hash;
        window.history.replaceState(null, '', newUrl);
    }
}

function getToken(): string {
    return sessionStorage.getItem(TOKEN_KEY) || '';
}

/** 请求头（带 token） */
function baseHeaders(): HeadersInit {
    return { 'Content-Type': 'application/json', 'X-Token': getToken() };
}

/** 通用请求：非 2xx 或 ok=false 时抛错（message 为后端 error 字段） */
async function request<T extends ApiBase>(path: string, init?: RequestInit): Promise<T> {
    let resp: Response;
    try {
        resp = await fetch(path, {
            ...init,
            headers: { ...baseHeaders(), ...(init?.headers || {}) },
        });
    } catch (e) {
        throw new Error('无法连接后端服务，请确认程序正在运行');
    }
    const data = (await resp.json()) as T;
    // 会话失效（应用重启后 token 已换新，旧页面还在点）必须给明确文案，不能无声
    if (resp.status === 401) {
        throw new Error('面板会话已失效，请从托盘重新打开面板');
    }
    if (!resp.ok || data.ok === false) {
        throw new Error(data.error || `请求失败 (${resp.status})`);
    }
    return data;
}

function get<T extends ApiBase>(path: string): Promise<T> {
    return request<T>(path);
}

function post<T extends ApiBase>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, { method: 'POST', body: JSON.stringify(body || {}) });
}

/** 简单 message 响应 */
interface MsgResp extends ApiBase {
    message?: string;
}

export const api = {
    // ---- 服务管理 ----
    services: () => get<ServicesResp>('/api/services'),
    serviceAction: (action: 'start' | 'stop' | 'start-all' | 'stop-all', name?: string) =>
        post<MsgResp>('/api/services/action', { action, name }),
    openDir: (name: string) => post<MsgResp>('/api/services/open-dir', { name }),

    // ---- 版本管理 ----
    nodeVersions: () => get<VersionSnapshotResp>('/api/versions/node'),
    pythonVersions: () => get<VersionSnapshotResp>('/api/versions/python'),
    switchVersion: (tool: 'node' | 'python', version: string) =>
        post<MsgResp>('/api/versions/switch', { tool, version }),
    installVersion: (tool: 'node' | 'python', version: string) =>
        post<MsgResp>('/api/versions/install', { tool, version }),

    // ---- 端口工具 ----
    findPort: (port: number) => get<{ port: number } & ApiBase & PortUsage>(
        `/api/ports/find?port=${port}`),
    commonPorts: () => get<CommonPortsResp>('/api/ports/common'),
    killPid: (pid: string) => post<MsgResp>('/api/ports/kill', { pid }),
    killPort: (port: number) => post<MsgResp>('/api/ports/kill-port', { port }),
    killCommonPorts: () => post<CommonPortsResp & MsgResp>('/api/ports/kill-common'),

    // ---- 文件关联 ----
    fileAssoc: () => get<FileAssocResp>('/api/fileassoc'),
    setFileAssoc: (ext: string, exePath: string) =>
        post<MsgResp>('/api/fileassoc/set', { ext, exePath }),

    // ---- 日志 ----
    recentLogs: () => get<RecentLogsResp>('/api/logs/recent'),
    logStreamUrl: () => `/api/logs/stream?token=${encodeURIComponent(getToken())}`,
};

/** 打开目录的响应里带 rows 的场景备用导出（避免 TS 未使用告警） */
export type { AssocRow };
