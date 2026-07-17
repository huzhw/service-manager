package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * 进程控制器，通过 ProcessBuilder 启动 + PID 文件跟踪
 * <p>
 * PID 持久化到临时目录，程序重启后可恢复状态
 */
public class ProcessController implements ServiceController {

    /** PID 持久化文件路径 */
    private static final File PID_FILE = new File(
            System.getProperty("java.io.tmpdir"), "service-manager/pids.properties");

    @Override
    public String getStatus(ServiceInfo info) {
        // 先从内存取
        if (info.getPid() > 0) {
            if (isPidAlive(info.getPid())) {
                return "RUNNING";
            }
        }
        // 再从文件恢复
        long savedPid = loadPid(info.getName());
        if (savedPid > 0 && isPidAlive(savedPid)) {
            info.setPid(savedPid);
            return "RUNNING";
        }
        return "STOPPED";
    }

    @Override
    public boolean start(ServiceInfo info) {
        // 已经在运行
        if ("RUNNING".equals(getStatus(info))) {
            return true;
        }

        try {
            ProcessBuilder pb;
            String cmd = info.getIdentifier();

            // 根据命令类型构建 ProcessBuilder
            if (cmd.startsWith("powershell ")) {
                // PowerShell 脚本
                pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass",
                        "-Command", cmd.substring("powershell ".length()));
            } else if (cmd.startsWith("cmd /c ")) {
                // bat 脚本
                pb = new ProcessBuilder("cmd", "/c", cmd.substring("cmd /c ".length()));
            } else {
                // 直接可执行文件
                pb = new ProcessBuilder(cmd.split(" "));
            }

            // 设置工作目录
            if (info.getWorkingDir() != null && !info.getWorkingDir().isEmpty()) {
                pb.directory(new File(info.getWorkingDir()));
            }

            // 重定向输出，避免进程阻塞
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 通过反射获取 Windows PID
            long pid = getPid(process);
            info.setPid(pid);

            // 持久化 PID
            savePid(info.getName(), pid);

            // 等待一小段时间确认启动
            Thread.sleep(2000);
            return isPidAlive(pid);

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean stop(ServiceInfo info) {
        long pid = info.getPid();
        if (pid <= 0) {
            pid = loadPid(info.getName());
        }
        if (pid <= 0) {
            return false;
        }

        try {
            // taskkill /T 同时终止子进程
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();

            // 清除 PID 记录
            info.setPid(0);
            deletePid(info.getName());

            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 PID 是否存活
     */
    private boolean isPidAlive(long pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/NH");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(String.valueOf(pid))) {
                        return true;
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * 反射获取 Windows 进程 PID（兼容 Java 8）
     */
    private long getPid(Process process) {
        try {
            // Java 8: ProcessImpl 有私有字段 pid
            if (process.getClass().getName().equals("java.lang.ProcessImpl")) {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                return field.getLong(process);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    // ========== PID 文件读写 ==========

    private synchronized void savePid(String serviceName, long pid) {
        if (!PID_FILE.getParentFile().exists()) {
            PID_FILE.getParentFile().mkdirs();
        }
        Properties props = loadAllPids();
        props.setProperty(serviceName, String.valueOf(pid));
        try (FileOutputStream fos = new FileOutputStream(PID_FILE)) {
            props.store(fos, "Service Manager PID records");
        } catch (Exception e) {
            // ignore
        }
    }

    private long loadPid(String serviceName) {
        Properties props = loadAllPids();
        String val = props.getProperty(serviceName);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }

    private void deletePid(String serviceName) {
        Properties props = loadAllPids();
        props.remove(serviceName);
        try (FileOutputStream fos = new FileOutputStream(PID_FILE)) {
            props.store(fos, "Service Manager PID records");
        } catch (Exception e) {
            // ignore
        }
    }

    private Properties loadAllPids() {
        Properties props = new Properties();
        if (PID_FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(PID_FILE)) {
                props.load(fis);
            } catch (Exception e) {
                // ignore
            }
        }
        return props;
    }
}
