/**
 * 后端 API 类型定义
 */

/** 服务状态常量 */
export type SvcStatus =
    | 'RUNNING'
    | 'STOPPED'
    | 'STARTING'
    | 'STOPPING'
    | 'PORT_UNREACHABLE'
    | 'UNKNOWN';

/** 单个服务行 */
export interface ServiceRow {
    name: string;
    type: 'WINDOWS_SERVICE' | 'PROCESS';
    typeLabel: string;
    category: string;
    port: number;
    status: SvcStatus;
    pid: number;
    version: string | null;
    workingDir: string | null;
    groupName: string | null;
    startTime: number;
}

/** 服务列表响应 */
export interface ServicesResp extends ApiBase {
    services: ServiceRow[];
    total: number;
    running: number;
    stopped: number;
    abnormal: number;
}

/** 版本快照（node / python 共用） */
export interface VersionSnapshotResp extends ApiBase {
    tool: 'node' | 'python';
    current: string | null;
    versions: string[];
}

/** 单个端口占用 */
export interface PortUsage {
    port: number;
    occupied: boolean;
    pid: string | null;
    processName: string | null;
}

/** 常用端口扫描响应 */
export interface CommonPortsResp extends ApiBase {
    ports: PortUsage[];
}

/** 文件关联行 */
export interface AssocRow {
    ext: string;
    desc: string;
    program: string;
}

/** 文件关联列表响应 */
export interface FileAssocResp extends ApiBase {
    rows: AssocRow[];
}

/** 最近日志响应 */
export interface RecentLogsResp extends ApiBase {
    lines: string[];
}

/** API 通用字段 */
export interface ApiBase {
    ok: boolean;
    error?: string;
    message?: string;
}
