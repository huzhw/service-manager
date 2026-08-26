import React from 'react';
import { createRoot } from 'react-dom/client';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import { captureToken } from './api/client';
import './styles/global.css';

// 从地址栏捕获 ?token= 并记忆（后端每次启动都会换新 token）
captureToken();

const root = createRoot(document.getElementById('root')!);

root.render(
    <React.StrictMode>
        <ConfigProvider
            locale={zhCN}
            theme={{
                algorithm: antdTheme.darkAlgorithm,
                token: {
                    colorPrimary: '#4f8cff',
                    colorInfo: '#4f8cff',
                    colorSuccess: '#34d399',
                    colorWarning: '#f59e0b',
                    colorError: '#f43f5e',
                    colorBgContainer: '#101828',
                    colorBgElevated: '#151e31',
                    colorBgLayout: 'transparent',
                    colorBorder: 'rgba(148, 163, 184, 0.22)',
                    colorBorderSecondary: 'rgba(148, 163, 184, 0.12)',
                    borderRadius: 10,
                    fontFamily: "'Microsoft YaHei', 'Segoe UI', sans-serif",
                    fontSize: 13,
                },
            }}
        >
            <AntdApp>
                <App />
            </AntdApp>
        </ConfigProvider>
    </React.StrictMode>,
);
