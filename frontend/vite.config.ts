import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import legacy from '@vitejs/plugin-legacy';

/**
 * Vite 配置 — 目标环境是 JavaFX WebView（内核偏旧）
 * ① 构建目标降到 es2017
 * ② legacy 插件产出 SystemJS nomodule 兜底包，WebView 不支持 ES Module 也能跑
 */
export default defineConfig({
    base: './',
    plugins: [
        react(),
        legacy({
            targets: ['Chrome >= 84', 'Safari >= 13'],
            additionalLegacyPolyfills: ['regenerator-runtime/runtime'],
        }),
    ],
    build: {
        target: 'es2017',
        outDir: 'dist',
        chunkSizeWarningLimit: 4000,
    },
    server: {
        proxy: {
            // 本地开发：后端需以 -Dsm.dev.token=sm-dev 启动
            '/api': {
                target: 'http://127.0.0.1:38080',
                changeOrigin: true,
                headers: { 'X-Token': process.env.SM_DEV_TOKEN || 'sm-dev' },
            },
        },
    },
});
