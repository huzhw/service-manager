package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.util.PortChecker;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
        // 1. 从内存 PID 检查
        if (info.getPid() > 0) {
            if (isPidAlive(info.getPid())) {
                return "RUNNING";
            }
        }
        // 2. 从文件恢复 PID
        long savedPid = loadPid(info.getName());
        if (savedPid > 0 && isPidAlive(savedPid)) {
            info.setPid(savedPid);
            return "RUNNING";
        }
        // 3. PID 丢失（外部启动），用端口兜底
        if (info.getPort() > 0 && PortChecker.isPortOpen(info.getPort())) {
            // 从端口反查 PID 并记录
            long portPid = findPidByPort(info.getPort());
            if (portPid > 0) {
                info.setPid(portPid);
                savePid(info.getName(), portPid);
            }
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
                // 直接可执行文件，避免 cmd /c 包裹导致进程保护问题
                pb = buildDirectProcess(cmd);
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
            if (pid > 0 && isPidAlive(pid)) {
                return true;
            }
            // 父进程（powershell/cmd脚本）可能已退出，但子进程还在启动中
            // 有端口则用端口兜底验证（Nacos 3.x 需 ~15s）
            if (info.getPort() > 0) {
                Thread.sleep(13000);
                if (PortChecker.isPortOpen(info.getPort())) {
                    long portPid = findPidByPort(info.getPort());
                    if (portPid > 0) {
                        info.setPid(portPid);
                        savePid(info.getName(), portPid);
                    }
                    return true;
                }
            }
            return false;

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
            // 优先用 processName 全杀（比 /T 更可靠，能清理 nginx 这类多进程守护）
            String processName = info.getProcessName();
            boolean killed = false;
            if (processName != null && !processName.isEmpty()) {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/IM", processName, "/F");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                killed = (p.exitValue() == 0);
            }
            // 兜底用 PID
            if (!killed) {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/F");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                if (p.exitValue() != 0) {
                    return false;
                }
            }

            // 验证进程已停止（最多等 5 秒）
            for (int i = 0; i < 10; i++) {
                Thread.sleep(500);
                if (!isPidAlive(pid)) {
                    info.setPid(0);
                    deletePid(info.getName());
                    return true;
                }
            }
            return false; // 超时未停止
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从端口反查 PID（用于恢复外部启动的进程 PID）
     */
    private long findPidByPort(int port) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                    "netstat -ano | findstr \":" + port + " \" | findstr \"LISTENING\"");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        try {
                            return Long.parseLong(parts[parts.length - 1]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
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
     * 构建直接进程（不用 cmd /c 包裹，避免子进程权限问题）
     * <p>
     * 自动分离可执行文件路径与参数
     */
    private ProcessBuilder buildDirectProcess(String cmd) {
        // 找到可执行文件路径：查找 .exe/.bat/.cmd 结尾
        String lower = cmd.toLowerCase();
        int exeEnd = -1;
        for (String ext : new String[]{".exe", ".bat", ".cmd"}) {
            int idx = lower.indexOf(ext);
            if (idx >= 0) {
                exeEnd = idx + ext.length();
                break;
            }
        }

        List<String> cmdList = new ArrayList<>();
        if (exeEnd > 0 && exeEnd < cmd.length()) {
            // 有参数的情况
            cmdList.add(cmd.substring(0, exeEnd));
            String rest = cmd.substring(exeEnd).trim();
            if (!rest.isEmpty()) {
                for (String arg : rest.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        cmdList.add(arg);
                    }
                }
            }
        } else {
            // 纯路径，无参数（如 Nginx）
            cmdList.add(cmd);
        }
        return new ProcessBuilder(cmdList);
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
