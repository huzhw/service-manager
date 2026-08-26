/**
 * 全局运行日志抽屉 — 底部控制台风格，SSE 实时推流
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ClearOutlined } from '@ant-design/icons';
import { Button, Checkbox, Drawer, Tag } from 'antd';
import { api } from '../api/client';

interface Props {
    open: boolean;
    onClose: () => void;
}

const LogDrawer: React.FC<Props> = ({ open, onClose }) => {
    const [lines, setLines] = useState<string[]>([]);
    const [autoScroll, setAutoScroll] = useState(true);
    const scrollRef = useRef<HTMLDivElement>(null);
    const esRef = useRef<EventSource | null>(null);

    /** 追加一行并按需滚动到底部 */
    const appendLine = useCallback(
        (line: string) => {
            setLines((prev) => {
                const next = prev.length > 1500 ? prev.slice(prev.length - 1200) : prev;
                return [...next, line];
            });
        },
        [],
    );

    // 打开时：拉历史 + 订阅 SSE；关闭/卸载时断开
    useEffect(() => {
        if (!open) {
            return;
        }
        let cancelled = false;

        (async () => {
            try {
                const recent = await api.recentLogs();
                if (!cancelled) {
                    setLines(recent.lines || []);
                }
            } catch {
                /* 忽略：历史拉不到就直接订阅实时 */
            }
            const es = new EventSource(api.logStreamUrl());
            es.onmessage = (ev) => {
                try {
                    // 后端对每行做了 JSON 字符串转义，这里还原
                    appendLine(JSON.parse(ev.data) as string);
                } catch {
                    appendLine(ev.data);
                }
            };
            es.onerror = () => {
                /* 断线由浏览器 EventSource 自动重连 */
            };
            esRef.current = es;
        })();

        return () => {
            cancelled = true;
            esRef.current?.close();
            esRef.current = null;
        };
    }, [open, appendLine]);

    // 自动滚动到底
    useEffect(() => {
        if (autoScroll && scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [lines, autoScroll]);

    return (
        <Drawer
            title={
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
                    <span className="glow-dot ok" />
                    运行日志
                    <Tag bordered={false} style={{ fontSize: 11 }}>
                        实时推流
                    </Tag>
                </span>
            }
            placement="bottom"
            height={340}
            open={open}
            onClose={onClose}
            styles={{ body: { padding: 0 } }}
            extra={
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 14 }}>
                    <Checkbox
                        checked={autoScroll}
                        onChange={(e) => setAutoScroll(e.target.checked)}
                        style={{ fontSize: 12 }}
                    >
                        自动滚动
                    </Checkbox>
                    <Button size="small" icon={<ClearOutlined />} onClick={() => setLines([])}>
                        清屏
                    </Button>
                </span>
            }
        >
            <div
                ref={scrollRef}
                className="mono"
                style={{
                    height: '100%',
                    overflow: 'auto',
                    background: '#0a0f1c',
                    padding: '10px 16px',
                    fontSize: 12,
                    lineHeight: '19px',
                    color: '#9fb3cd',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                }}
            >
                {lines.map((l, i) => (
                    <div key={i}>{l || '\u00a0'}</div>
                ))}
                {!lines.length && (
                    <div style={{ color: '#5b6b84' }}>暂无日志 — 执行一次启停操作试试</div>
                )}
            </div>
        </Drawer>
    );
};

export default LogDrawer;
