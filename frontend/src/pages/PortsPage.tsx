/**
 * 端口工具页 — 单端口查询强杀 + 常用端口批量查杀
 */
import React, { useCallback, useEffect, useState } from 'react';
import { ClearOutlined, SearchOutlined } from '@ant-design/icons';
import { App as AntdApp, Alert, Button, Card, Input, Popconfirm, Space, Tag, Tooltip } from 'antd';
import { api } from '../api/client';
import type { PortUsage } from '../api/types';

const PortsPage: React.FC = () => {
    const { message } = AntdApp.useApp();
    const [portInput, setPortInput] = useState('');
    const [findResult, setFindResult] = useState<PortUsage | null>(null);
    const [finding, setFinding] = useState(false);
    const [common, setCommon] = useState<PortUsage[]>([]);
    const [scanning, setScanning] = useState(false);
    const [killingAll, setKillingAll] = useState(false);

    /** 扫描常用端口 */
    const scanCommon = useCallback(async () => {
        setScanning(true);
        try {
            const resp = await api.commonPorts();
            setCommon(resp.ports || []);
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setScanning(false);
        }
    }, [message]);

    useEffect(() => {
        scanCommon();
    }, [scanCommon]);

    /** 查询单个端口 */
    const doFind = async (portStr?: string) => {
        const raw = (portStr ?? portInput).trim();
        if (!/^\d{1,5}$/.test(raw)) {
            message.warning('请输入有效端口号');
            return;
        }
        setFinding(true);
        try {
            setFindResult(await api.findPort(Number(raw)));
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setFinding(false);
        }
    };

    /** 强杀单个 PID */
    const killByPid = async (pid: string) => {
        try {
            await api.killPid(pid);
            message.success(`已终止 PID ${pid}`);
        } catch (e) {
            message.error((e as Error).message);
        }
        // 杀完刷新两处视图
        if (findResult) {
            await doFind(String(findResult.port));
        }
        scanCommon();
    };

    /** 释放指定端口 */
    const killByPort = async (port: number) => {
        try {
            const resp = await api.killPort(port);
            message.success(resp.message || `端口 ${port} 已释放`);
        } catch (e) {
            message.error((e as Error).message);
        }
        if (findResult && findResult.port === port) {
            await doFind(String(port));
        }
        scanCommon();
    };

    /** 一键批量查杀常用端口 */
    const killAll = async () => {
        setKillingAll(true);
        try {
            const resp = await api.killCommonPorts();
            message.success(resp.message || '批量查杀完成');
            setCommon(resp.ports || []);
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setKillingAll(false);
        }
    };

    /** 端口占用状态着色 */
    const usageTag = (u: PortUsage) =>
        u.occupied ? (
            <Tag color="error" style={{ borderRadius: 999 }}>
                占用
            </Tag>
        ) : (
            <Tag bordered={false} style={{ borderRadius: 999 }}>
                空闲
            </Tag>
        );

    const occupiedCount = common.filter((p) => p.occupied).length;

    return (
        <div>
            <div style={{ marginBottom: 18 }}>
                <div style={{ fontSize: 20, fontWeight: 700 }}>端口工具</div>
                <div style={{ color: 'var(--muted)', fontSize: 12, marginTop: 2 }}>
                    查找端口占用的进程并强制终止
                </div>
            </div>

            {/* ===== 自定义端口查找 ===== */}
            <Card className="glass-card" bordered={false} title="🔍 查找端口" style={{ marginBottom: 14 }}>
                <Space.Compact style={{ width: 360 }}>
                    <Input
                        placeholder="输入端口号，如 8080"
                        value={portInput}
                        onChange={(e) => setPortInput(e.target.value)}
                        onPressEnter={() => doFind()}
                        allowClear
                    />
                    <Button type="primary" icon={<SearchOutlined />} loading={finding} onClick={() => doFind()}>
                        查找
                    </Button>
                </Space.Compact>

                {findResult && (
                    <Alert
                        style={{ marginTop: 12 }}
                        type={findResult.occupied ? 'warning' : 'success'}
                        showIcon
                        message={
                            findResult.occupied ? (
                                <span>
                                    端口 <span className="mono">{findResult.port}</span> 被 PID{' '}
                                    <span className="mono">{findResult.pid}</span> 占用
                                    {findResult.processName ? <>（{findResult.processName}）</> : null}
                                </span>
                            ) : (
                                <span>
                                    端口 <span className="mono">{findResult.port}</span> 未被占用
                                </span>
                            )
                        }
                        action={
                            findResult.occupied ? (
                                <Popconfirm title={`强制终止 PID ${findResult.pid}？`} onConfirm={() => killByPid(findResult.pid!)}>
                                    <Button size="small" danger>
                                        终止进程
                                    </Button>
                                </Popconfirm>
                            ) : undefined
                        }
                    />
                )}
            </Card>

            {/* ===== 常用端口快捷区 ===== */}
            <Card
                className="glass-card"
                bordered={false}
                title="⚡ 常用端口快捷查杀"
                extra={
                    <Space>
                        <Button size="small" icon={<ClearOutlined />} loading={scanning} onClick={scanCommon}>
                            重新扫描
                        </Button>
                        <Popconfirm
                            title={`一键终止 ${occupiedCount} 个被占用常用端口的进程？`}
                            disabled={occupiedCount === 0}
                            onConfirm={killAll}
                        >
                            <Button size="small" danger loading={killingAll} disabled={occupiedCount === 0}>
                                一键全部杀掉（{occupiedCount}）
                            </Button>
                        </Popconfirm>
                    </Space>
                }
            >
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    {common.map((u) => (
                        <Tooltip key={u.port} title={u.occupied ? `${u.processName || '进程'} · PID ${u.pid}，点击终止` : '空闲，点击查询'}>
                            <Popconfirm
                                title={`终止占用端口 ${u.port} 的进程？`}
                                onConfirm={() => killByPort(u.port)}
                                disabled={!u.occupied}
                            >
                                <button
                                    className="glass-card hoverable"
                                    style={{
                                        cursor: u.occupied ? 'pointer' : 'default',
                                        border: '1px solid',
                                        borderColor: u.occupied ? 'rgba(244,63,94,.45)' : 'var(--line)',
                                        background: u.occupied ? 'rgba(244,63,94,.08)' : undefined,
                                        borderRadius: 10,
                                        padding: '8px 16px',
                                        minWidth: 96,
                                        display: 'inline-flex',
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        gap: 3,
                                    }}
                                >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                                        <span className={`glow-dot ${u.occupied ? 'bad' : 'off'}`} />
                                        <span className="mono" style={{ fontSize: 15, fontWeight: 600 }}>
                                            {u.port}
                                        </span>
                                    </span>
                                    <span style={{ fontSize: 11, color: 'var(--muted)', maxWidth: 110, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {u.occupied ? `${u.processName || 'PID ' + u.pid}` : '空闲'}
                                    </span>
                                </button>
                            </Popconfirm>
                        </Tooltip>
                    ))}
                    {scanning && !common.length && (
                        <span style={{ color: 'var(--muted)' }}>扫描中…</span>
                    )}
                </div>
            </Card>
        </div>
    );
};

export default PortsPage;
