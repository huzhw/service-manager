/**
 * 服务管理页 — 统计卡 + 实时服务表格
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    ApiOutlined,
    FolderOpenOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import { App as AntdApp, Button, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../api/client';
import type { ServiceRow, ServicesResp, SvcStatus } from '../api/types';

/** 状态 → 光点样式与中文文案 */
const STATUS_META: Record<SvcStatus, { dot: string; text: string; tagColor?: string }> = {
    RUNNING: { dot: 'ok', text: '运行中', tagColor: 'success' },
    STOPPED: { dot: 'off', text: '已停止', tagColor: 'default' },
    STARTING: { dot: 'warn', text: '启动中' },
    STOPPING: { dot: 'warn', text: '停止中' },
    PORT_UNREACHABLE: { dot: 'bad', text: '端口不通' },
    UNKNOWN: { dot: 'bad', text: '未知' },
};

/** 页面标题条 */
const PageHeader: React.FC<{
    onRefresh: () => void;
    onStartAll: () => void;
    onStopAll: () => void;
    refreshing: boolean;
}> = ({ onRefresh, onStartAll, onStopAll, refreshing }) => (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 18 }}>
        <div style={{ flex: 1 }}>
            <div style={{ fontSize: 20, fontWeight: 700 }}>服务管理</div>
            <div style={{ color: 'var(--muted)', fontSize: 12, marginTop: 2 }}>
                每 5 秒自动刷新 · 同组服务联动启停
            </div>
        </div>
        <Space>
            <Button icon={<ReloadOutlined />} loading={refreshing} onClick={onRefresh}>
                刷新
            </Button>
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={onStartAll}>
                全部启动
            </Button>
            <Button danger ghost icon={<PauseCircleOutlined />} onClick={onStopAll}>
                全部停止
            </Button>
        </Space>
    </div>
);

/** 统计卡片 */
const StatCard: React.FC<{ label: string; value: number; dot: string }> = ({ label, value, dot }) => (
    <div className="glass-card hoverable" style={{ flex: 1, padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14 }}>
        <span
            className={`glow-dot ${dot}`}
            style={
                dot === ''
                    ? { width: 10, height: 10, background: 'var(--accent)', boxShadow: '0 0 6px var(--accent)' }
                    : { width: 10, height: 10 }
              }
        />
        <div>
            <div style={{ fontSize: 24, fontWeight: 700, lineHeight: 1.1 }}>{value}</div>
            <div style={{ color: 'var(--muted)', fontSize: 12 }}>{label}</div>
        </div>
    </div>
);

/** 行内启停按钮组：loading 态自持在单元格内，点击不会触发整表重渲染 */
const ActionButtons: React.FC<{
    row: ServiceRow;
    onAction: (action: 'start' | 'stop', name: string) => Promise<void>;
    onOpenDir: (row: ServiceRow) => void;
}> = React.memo(({ row, onAction, onOpenDir }) => {
    const [pending, setPending] = useState(false);
    const running = row.status === 'RUNNING';
    return (
        <Space size={4}>
            {/* OliveTin 形态：点击立即执行，零弹层依赖（老 WebKit 对 portal 弹层兼容差）；
                提示用原生 title 属性，不用 antd Tooltip */}
            <Button
                size="small"
                type={running ? 'default' : 'primary'}
                danger={running}
                ghost={running}
                icon={running ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                loading={pending}
                title={(running ? '停止（同组联动）：' : '启动（同组联动）：') + row.name}
                onClick={async () => {
                    setPending(true);
                    try {
                        await onAction(running ? 'stop' : 'start', row.name);
                    } finally {
                        setPending(false);
                    }
                }}
            >
                {running ? '停止' : '启动'}
            </Button>
            <Button
                size="small"
                type="text"
                icon={<FolderOpenOutlined />}
                disabled={!row.workingDir}
                title="打开目录"
                onClick={() => onOpenDir(row)}
            />
        </Space>
    );
});

const ServicesPage: React.FC = () => {
    const { message } = AntdApp.useApp();
    const [data, setData] = useState<ServicesResp | null>(null);
    const [refreshing, setRefreshing] = useState(false);

    /** 拉取列表：内容没变化就跳过 setState，避免老 WebKit 每 5 秒无谓全表重绘卡顿 */
    const lastSnapRef = useRef<string>('');
    const load = useCallback(async (showError = false) => {
        try {
            const resp = await api.services();
            const snap = JSON.stringify(resp);
            if (snap !== lastSnapRef.current) {
                lastSnapRef.current = snap;
                setData(resp);
            }
        } catch (e) {
            if (showError) {
                message.error((e as Error).message);
            }
        }
    }, [message]);

    // 首次加载 + 5 秒轮询
    useEffect(() => {
        load(true);
        const timer = setInterval(() => load(), 5000);
        return () => clearInterval(timer);
    }, [load]);

    /** 手动刷新 */
    const refresh = useCallback(async () => {
        setRefreshing(true);
        await load(true);
        setRefreshing(false);
    }, [load]);

    /** 提交启停动作：提交后立刻刷新 + 短轮询直到目标脱离 STARTING/STOPPING 过渡态 */
    const doAction = useCallback(async (action: 'start' | 'stop' | 'start-all' | 'stop-all', name?: string) => {
        try {
            await api.serviceAction(action, name);
            message.success(`「${name || '批量'}」任务已提交，等待状态刷新`);
            await load();

            // 目标服务（或批量，则取任意仍处于过渡态的服务）不再处于 STARTING/STOPPING 即停
            const POLL_MS = 800;
            const MAX_POLLS = 10;
            for (let i = 0; i < MAX_POLLS; i++) {
                const resp = await api.services();
                if (name) {
                    const row = resp.services.find((r) => r.name === name);
                    if (!row || row.status !== 'STARTING' && row.status !== 'STOPPING') {
                        await load();
                        break;
                    }
                } else {
                    const busy = resp.services.some(
                        (r) => r.status === 'STARTING' || r.status === 'STOPPING'
                    );
                    if (!busy) {
                        await load();
                        break;
                    }
                }
                // 等 800ms 后继续轮询
                await new Promise((resolve) => setTimeout(resolve, POLL_MS));
            }
        } catch (e) {
            message.error((e as Error).message);
        }
    }, [load, message]);

    /** 打开工作目录 */
    const openDir = useCallback(async (row: ServiceRow) => {
        try {
            await api.openDir(row.name);
        } catch (e) {
            message.error((e as Error).message);
        }
    }, [message]);

    /** 表格列定义 */
    const columns: ColumnsType<ServiceRow> = [
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (s: SvcStatus) => {
                const meta = STATUS_META[s] || STATUS_META.UNKNOWN;
                return (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7 }}>
                        <span className={`glow-dot ${meta.dot}`} />
                        <span>{meta.text}</span>
                    </span>
                );
            },
        },
        {
            title: '服务名',
            dataIndex: 'name',
            render: (_, row) => (
                <div>
                    <div style={{ fontWeight: 600 }}>{row.name}</div>
                    {row.groupName && (
                        <div style={{ color: 'var(--muted)', fontSize: 11 }}>分组: {row.groupName}</div>
                    )}
                </div>
            ),
        },
        {
            title: '类型',
            dataIndex: 'typeLabel',
            width: 105,
            render: (t: string) => <Tag bordered={false}>{t}</Tag>,
        },
        {
            title: '端口',
            dataIndex: 'port',
            width: 80,
            render: (p: number) => (p > 0 ? <span className="mono">{p}</span> : '-'),
        },
        {
            title: '版本',
            dataIndex: 'version',
            width: 90,
            render: (v: string | null) => (v ? <span className="mono">{v}</span> : '-'),
        },
        {
            title: 'PID',
            dataIndex: 'pid',
            width: 75,
            render: (p: number) => (p > 0 ? <span className="mono">{p}</span> : '-'),
        },
        {
            title: '操作',
            key: 'actions',
            width: 150,
            render: (_, row) => (
                <ActionButtons row={row} onAction={doAction} onOpenDir={openDir} />
            ),
        },
    ];

    const rows = data?.services || [];

    return (
        <div style={{ height: 'calc(100vh - 52px)', display: 'flex', flexDirection: 'column' }}>
            <PageHeader
                refreshing={refreshing}
                onRefresh={refresh}
                onStartAll={() => doAction('start-all')}
                onStopAll={() => doAction('stop-all')}
            />

            {/* 统计卡片行 */}
            <div style={{ display: 'flex', gap: 14, marginBottom: 14 }}>
                <StatCard label="运行中" value={data?.running ?? 0} dot="ok" />
                <StatCard label="已停止" value={data?.stopped ?? 0} dot="off" />
                <StatCard label="异常 / 过渡" value={data?.abnormal ?? 0} dot="warn" />
                <StatCard label="服务总数" value={data?.total ?? 0} dot="" />
            </div>

            {/* 表格：紧凑模式，全部行直接铺开，不做内部滚动 */}
            <div className="glass-card" style={{ padding: 6, flex: 1, minHeight: 0, overflow: 'auto' }}>
                <Table<ServiceRow>
                    rowKey="name"
                    size="small"
                    columns={columns}
                    dataSource={rows}
                    pagination={false}
                    loading={!data}
                />
            </div>
        </div>
    );
};

export default ServicesPage;
