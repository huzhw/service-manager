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
import { App as AntdApp, Button, Popconfirm, Space, Table, Tag, Tooltip } from 'antd';
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
            <Popconfirm title="确定停止全部服务？" onConfirm={onStopAll}>
                <Button danger ghost icon={<PauseCircleOutlined />}>
                    全部停止
                </Button>
            </Popconfirm>
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

const ServicesPage: React.FC = () => {
    const { message, modal } = AntdApp.useApp();
    const [data, setData] = useState<ServicesResp | null>(null);
    const [refreshing, setRefreshing] = useState(false);
    const [acting, setActing] = useState<string | null>(null);

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

    /** 提交启停动作 */
    const doAction = useCallback(async (action: 'start' | 'stop' | 'start-all' | 'stop-all', name?: string) => {
        const key = name || action;
        setActing(key);
        try {
            await api.serviceAction(action, name);
            message.success(`「${name || '批量'}」任务已提交，等待状态刷新`);
            setTimeout(() => load(), 1200);
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setActing(null);
        }
    }, [load, message]);

    /** 全部停止前二次确认 */
    const confirmStopAll = useCallback(() => {
        modal.confirm({
            title: '停止全部服务',
            content: '将按停止顺序依次停止所有运行中的服务，确定继续？',
            okText: '停止',
            okButtonProps: { danger: true },
            onOk: () => doAction('stop-all'),
        });
    }, [doAction, modal]);

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
            render: (_, row) => {
                const running = row.status === 'RUNNING';
                return (
                    <Space size={4}>
                        <Tooltip title={running ? '停止（同组联动）' : '启动（同组联动）'}>
                            <Popconfirm
                                title={(running ? '停止 ' : '启动 ') + row.name + '？'}
                                onConfirm={() => doAction(running ? 'stop' : 'start', row.name)}
                            >
                                <Button
                                    size="small"
                                    type={running ? 'default' : 'primary'}
                                    danger={running}
                                    ghost={running}
                                    icon={running ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                                    loading={acting === row.name}
                                >
                                    {running ? '停止' : '启动'}
                                </Button>
                            </Popconfirm>
                        </Tooltip>
                        <Tooltip title="打开目录">
                            <Button
                                size="small"
                                type="text"
                                icon={<FolderOpenOutlined />}
                                disabled={!row.workingDir}
                                onClick={() => openDir(row)}
                            />
                        </Tooltip>
                    </Space>
                );
            },
        },
    ];

    const rows = data?.services || [];

    return (
        <div style={{ height: 'calc(100vh - 52px)', display: 'flex', flexDirection: 'column' }}>
            <PageHeader
                refreshing={refreshing}
                onRefresh={refresh}
                onStartAll={() => doAction('start-all')}
                onStopAll={confirmStopAll}
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
