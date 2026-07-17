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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                p.waitFor();
                String output = sb.toString();
                // sc start 成功后会包含服务名
                return output.contains(info.getIdentifier()) || p.exitValue() == 0;
            }
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                p.waitFor();
                // sc stop 异步操作，返回成功不一定立刻停止
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
