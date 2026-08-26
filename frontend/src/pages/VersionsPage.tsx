/**
 * 版本管理页 — nvm (Node.js) / pyenv (Python) 查看、切换、安装
 */
import React, { useCallback, useEffect, useState } from 'react';
import { CheckCircleFilled, ReloadOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Card, Input, Popconfirm, Space, Tag } from 'antd';
import { api } from '../api/client';
import type { VersionSnapshotResp } from '../api/types';

/** 单个工具卡片（node / python 共用） */
const ToolCard: React.FC<{
    title: string;
    subtitle: string;
    color: string;
    tool: 'node' | 'python';
    snap: VersionSnapshotResp | null;
    loading: boolean;
    onReload: () => void;
}> = ({ title, subtitle, color, tool, snap, loading, onReload }) => {
    const { message } = AntdApp.useApp();
    const [installValue, setInstallValue] = useState('');
    const [switching, setSwitching] = useState<string | null>(null);
    const [installing, setInstalling] = useState(false);

    /** 切换版本 */
    const doSwitch = async (v: string) => {
        setSwitching(v);
        try {
            const resp = await api.switchVersion(tool, v);
            message.success(resp.message || `已切换至 ${v}`);
            onReload();
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setSwitching(null);
        }
    };

    /** 安装版本 */
    const doInstall = async () => {
        const v = installValue.trim();
        if (!/^\d+(\.\d+){0,3}$/.test(v)) {
            message.warning('请输入合法版本号，如 18.17.0');
            return;
        }
        setInstalling(true);
        try {
            const resp = await api.installVersion(tool, v);
            message.info(resp.message || '安装已开始，进度见运行日志');
            setInstallValue('');
            // 安装耗时较长，30 秒后自动刷一次列表
            setTimeout(onReload, 30000);
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setInstalling(false);
        }
    };

    return (
        <Card
            className="glass-card"
            bordered={false}
            title={
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <span
                        className="glow-dot ok"
                        style={{ background: color, boxShadow: `0 0 6px ${color}` }}
                    />
                    {title}
                </span>
            }
            extra={
                <Button size="small" type="text" icon={<ReloadOutlined />} onClick={onReload} />
            }
        >
            {/* 当前版本 */}
            <div style={{ marginBottom: 14 }}>
                <span style={{ color: 'var(--muted)', fontSize: 12 }}>当前版本</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 2 }}>
                    <span className="mono" style={{ fontSize: 26, fontWeight: 700 }}>
                        {loading ? '…' : snap?.current || '未检测到'}
                    </span>
                    {snap?.current && (
                        <Tag icon={<CheckCircleFilled />} bordered={false} color="success">
                            生效中
                        </Tag>
                    )}
                </div>
                <div style={{ color: 'var(--muted)', fontSize: 11, marginTop: 2 }}>{subtitle}</div>
            </div>

            {/* 已装版本 chips */}
            <div style={{ marginBottom: 16 }}>
                <div style={{ color: 'var(--muted)', fontSize: 12, marginBottom: 8 }}>
                    已安装版本（点击切换）
                </div>
                <Space size={[6, 6]} wrap>
                    {(snap?.versions || []).map((v) => {
                        const active = v === snap?.current;
                        return (
                            <Popconfirm
                                key={v}
                                title={`切换到 ${v}？`}
                                onConfirm={() => doSwitch(v)}
                                disabled={active}
                            >
                                <Tag.CheckableTag
                                    checked={active}
                                    style={{
                                        border: '1px solid',
                                        borderColor: active ? 'transparent' : 'rgba(148,163,184,.3)',
                                        padding: '3px 12px',
                                        borderRadius: 999,
                                        cursor: active ? 'default' : 'pointer',
                                    }}
                                >
                                    {active ? '● ' : ''}
                                    {v}
                                </Tag.CheckableTag>
                            </Popconfirm>
                        );
                    })}
                    {!loading && (snap?.versions || []).length === 0 && (
                        <span style={{ color: 'var(--muted)' }}>未检测到已安装版本</span>
                    )}
                    {switching && (
                        <Tag bordered={false} color="processing">
                            切换到 {switching} …
                        </Tag>
                    )}
                </Space>
            </div>

            {/* 安装新版本 */}
            <Input.Search
                placeholder="输入版本号安装，如 18.17.0"
                enterButton={installing ? '安装中…' : '安装'}
                value={installValue}
                onChange={(e) => setInstallValue(e.target.value)}
                onSearch={doInstall}
                loading={installing}
                size="small"
                style={{ maxWidth: 300 }}
            />
        </Card>
    );
};

const VersionsPage: React.FC = () => {
    const [nodeSnap, setNodeSnap] = useState<VersionSnapshotResp | null>(null);
    const [pySnap, setPySnap] = useState<VersionSnapshotResp | null>(null);

    /** 拉取快照（失败保留上次数据） */
    const loadNode = useCallback(async () => {
        try {
            setNodeSnap(await api.nodeVersions());
        } catch {
            /* 忽略：保持旧快照 */
        }
    }, []);

    const loadPython = useCallback(async () => {
        try {
            setPySnap(await api.pythonVersions());
        } catch {
            /* 忽略：保持旧快照 */
        }
    }, []);

    useEffect(() => {
        loadNode();
        loadPython();
    }, [loadNode, loadPython]);

    return (
        <div>
            <div style={{ marginBottom: 18 }}>
                <div style={{ fontSize: 20, fontWeight: 700 }}>版本管理</div>
                <div style={{ color: 'var(--muted)', fontSize: 12, marginTop: 2 }}>
                    nvm / pyenv 全局版本切换与安装
                </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <ToolCard
                    title="Node.js · nvm"
                    subtitle="nvm use 切换约需 2 秒生效"
                    color="#34d399"
                    tool="node"
                    snap={nodeSnap}
                    loading={!nodeSnap}
                    onReload={loadNode}
                />
                <ToolCard
                    title="Python · pyenv"
                    subtitle="pyenv global + rehash 即时生效"
                    color="#f59e0b"
                    tool="python"
                    snap={pySnap}
                    loading={!pySnap}
                    onReload={loadPython}
                />
            </div>
        </div>
    );
};

export default VersionsPage;
