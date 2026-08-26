/**
 * 文件关联页 — 常用扩展名默认打开方式查看与修改
 */
import React, { useCallback, useEffect, useState } from 'react';
import { EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Card, Form, Input, Modal, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../api/client';
import type { AssocRow } from '../api/types';

const FileAssocPage: React.FC = () => {
    const { message } = AntdApp.useApp();
    const [rows, setRows] = useState<AssocRow[]>([]);
    const [loading, setLoading] = useState(false);
    const [editing, setEditing] = useState<AssocRow | null>(null);
    const [saving, setSaving] = useState(false);
    const [form] = Form.useForm<{ exePath: string }>();

    /** 加载关联列表（后端首次查询需数秒） */
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const resp = await api.fileAssoc();
            setRows(resp.rows || []);
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setLoading(false);
        }
    }, [message]);

    useEffect(() => {
        load();
    }, [load]);

    /** 打开修改弹窗 */
    const openEdit = (row: AssocRow) => {
        setEditing(row);
        form.setFieldsValue({ exePath: '' });
    };

    /** 提交修改 */
    const saveEdit = async () => {
        if (!editing) {
            return;
        }
        const { exePath } = await form.validateFields();
        setSaving(true);
        try {
            const resp = await api.setFileAssoc(editing.ext, exePath.trim());
            message.success(resp.message || `${editing.ext} 已更新`);
            setEditing(null);
            load();
        } catch (e) {
            message.error((e as Error).message);
        } finally {
            setSaving(false);
        }
    };

    const columns: ColumnsType<AssocRow> = [
        {
            title: '扩展名',
            dataIndex: 'ext',
            width: 110,
            render: (v: string) => <span className="mono">{v}</span>,
        },
        { title: '描述', dataIndex: 'desc', width: 190 },
        {
            title: '当前程序',
            dataIndex: 'program',
            ellipsis: true,
            render: (v: string) => <span className="mono">{v}</span>,
        },
        {
            title: '操作',
            key: 'act',
            width: 100,
            render: (_, row) => (
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>
                    更改
                </Button>
            ),
        },
    ];

    return (
        <div>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 18 }}>
                <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 20, fontWeight: 700 }}>文件关联</div>
                    <div style={{ color: 'var(--muted)', fontSize: 12, marginTop: 2 }}>
                        修改需管理员权限 · 首次查询较慢，请耐心等待
                    </div>
                </div>
                <Space>
                    <Button icon={<ReloadOutlined />} loading={loading} onClick={load}>
                        刷新
                    </Button>
                </Space>
            </div>

            <Card className="glass-card" bordered={false} styles={{ body: { padding: 6 } }}>
                <Table<AssocRow>
                    rowKey="ext"
                    size="middle"
                    columns={columns}
                    dataSource={rows}
                    loading={loading}
                    pagination={false}
                    scroll={{ y: 'calc(100vh - 260px)' }}
                />
            </Card>

            {/* 修改弹窗：Web 端无法弹出系统文件选择框，改为手输 exe 路径 */}
            <Modal
                title={`更改 ${editing?.ext || ''} 的默认打开方式`}
                open={!!editing}
                onOk={saveEdit}
                confirmLoading={saving}
                onCancel={() => setEditing(null)}
                okText="保存"
                cancelText="取消"
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="exePath"
                        label="程序完整路径（exe）"
                        rules={[
                            { required: true, message: '请输入 exe 完整路径' },
                            {
                                validator: (_, v: string) =>
                                    v && v.includes('"')
                                        ? Promise.reject(new Error('路径不能包含引号'))
                                        : Promise.resolve(),
                            },
                        ]}
                    >
                        <Input placeholder='例如 "C:\Program Files\Typora\Typora.exe"' />
                    </Form.Item>
                    <div style={{ color: 'var(--muted)', fontSize: 12 }}>
                        提示：在资源管理器中按住 Shift 右键程序 → 「复制文件地址」即可获得完整路径。
                    </div>
                </Form>
            </Modal>
        </div>
    );
};

export default FileAssocPage;
