/**
 * 应用骨架：左侧玻璃拟态导航 + 右侧内容区
 */
import React, { useState } from 'react';
import {
    ClusterOutlined,
    CodeOutlined,
    FileTextOutlined,
    SnippetsOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';
import { Button, Menu, Tag, Tooltip } from 'antd';
import ServicesPage from './pages/ServicesPage';
import VersionsPage from './pages/VersionsPage';
import PortsPage from './pages/PortsPage';
import FileAssocPage from './pages/FileAssocPage';
import LogDrawer from './components/LogDrawer';

/** 导航项定义 */
type PageKey = 'services' | 'versions' | 'ports' | 'fileassoc';

const NAV_ITEMS: { key: PageKey; icon: React.ReactNode; label: string }[] = [
    { key: 'services', icon: <ClusterOutlined />, label: '服务管理' },
    { key: 'versions', icon: <CodeOutlined />, label: '版本管理' },
    { key: 'ports', icon: <ThunderboltOutlined />, label: '端口工具' },
    { key: 'fileassoc', icon: <FileTextOutlined />, label: '文件关联' },
];

const App: React.FC = () => {
    const [page, setPage] = useState<PageKey>('services');
    const [logOpen, setLogOpen] = useState(false);

    /** 按导航键渲染页面（带淡入动画） */
    const renderPage = () => {
        switch (page) {
            case 'services':
                return <ServicesPage />;
            case 'versions':
                return <VersionsPage />;
            case 'ports':
                return <PortsPage />;
            case 'fileassoc':
                return <FileAssocPage />;
        }
    };

    return (
        <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
            {/* ===== 左侧导航 ===== */}
            <aside
                className="glass-card"
                style={{
                    width: 216,
                    margin: 14,
                    marginRight: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    padding: '20px 12px 16px',
                    flexShrink: 0,
                }}
            >
                {/* 品牌区 */}
                <div style={{ padding: '2px 10px 18px' }}>
                    <div
                        className="brand-text"
                        style={{
                            fontSize: 19,
                            fontWeight: 700,
                            letterSpacing: 1,
                            whiteSpace: 'nowrap',
                        }}
                    >
                        ⚙ 服务管理面板
                    </div>
                    <div style={{ color: 'var(--muted)', fontSize: 11, marginTop: 4 }}>
                        Service Manager · Web Edition
                    </div>
                </div>

                {/* 导航菜单 */}
                <Menu
                    mode="inline"
                    selectedKeys={[page]}
                    onClick={(e) => setPage(e.key as PageKey)}
                    items={NAV_ITEMS}
                    style={{ background: 'transparent', borderInlineEnd: 'none', flex: 1 }}
                />

                {/* 底部工具区 */}
                <div style={{ padding: '8px 10px 4px', borderTop: '1px solid var(--line)' }}>
                    <Tooltip title="运行日志实时推流" placement="right">
                        <Button
                            block
                            type="text"
                            icon={<SnippetsOutlined />}
                            onClick={() => setLogOpen(true)}
                            style={{ justifyContent: 'flex-start', color: 'var(--muted)' }}
                        >
                            运行日志
                        </Button>
                    </Tooltip>
                    <div style={{ textAlign: 'center', marginTop: 8 }}>
                        <Tag bordered={false} color="blue" style={{ fontSize: 10 }}>
                            v2.0 · React
                        </Tag>
                    </div>
                </div>
            </aside>

            {/* ===== 右侧内容区 ===== */}
            <main
                key={page}
                className="page-fade"
                style={{ flex: 1, minWidth: 0, overflow: 'auto', padding: '24px 26px 28px' }}
            >
                {renderPage()}
            </main>

            {/* 全局日志抽屉 */}
            <LogDrawer open={logOpen} onClose={() => setLogOpen(false)} />
        </div>
    );
};

export default App;
