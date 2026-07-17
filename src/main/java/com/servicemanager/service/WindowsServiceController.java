package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Windows 服务控制器，通过 sc 命令管理
 */
public class WindowsServiceController implements ServiceController {

    @Override
    public String getStatus(ServiceInfo info) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "query", info.getIdentifier());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                p.waitFor();
                String output = sb.toString();
                if (output.contains("RUNNING")) {
                    return "RUNNING";
                } else if (output.contains("STOPPED")) {
                    return "STOPPED";
                } else if (output.contains("STOP_PENDING")) {
                    return "STOPPING";
                } else if (output.contains("START_PENDING")) {
                    return "STARTING";
                }
            }
        } catch (Exception e) {
            // 查询失败返回 UNKNOWN
        }
        return "UNKNOWN";
    }

    @Override
    public boolean start(ServiceInfo info) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "start", info.getIdentifier());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            p.waitFor();
            String output = sb.toString();

            // sc start 失败检测
            if (output.contains("FAILED") || output.contains("1056") || output.contains("拒绝访问")) {
                return false;
            }

            // 轮询等待服务真正启动（最多 15 秒）
            for (int i = 0; i < 30; i++) {
                Thread.sleep(500);
                String status = getStatus(info);
                if ("RUNNING".equals(status)) {
                    return true;
                }
            }
            return false; // 超时未启动
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean stop(ServiceInfo info) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "stop", info.getIdentifier());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            p.waitFor();
            String output = sb.toString();

            // 检查是否有明确的错误
            if (output.contains("FAILED") || output.contains("1062") || output.contains("拒绝访问")) {
                return false;
            }

            // 轮询等待服务真正停止（最多 10 秒）
            for (int i = 0; i < 20; i++) {
                Thread.sleep(500);
                String status = getStatus(info);
                if ("STOPPED".equals(status)) {
                    return true;
                }
            }
            return false; // 超时未停止
        } catch (Exception e) {
            return false;
        }
    }
}
