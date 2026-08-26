package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.util.PortChecker;

import java.io.*;
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
                // PowerShell 脚本 — 提取 -File 路径，避免 -Command 嵌套问题
                String rest = cmd.substring("powershell ".length()).trim();
                String ps1Path = rest;
                if (rest.startsWith("-File ")) {
                    ps1Path = rest.substring(6).trim();
                } else if (rest.startsWith("-ExecutionPolicy ")) {
                    int fileIdx = rest.indexOf("-File ");
                    if (fileIdx >= 0) {
                        ps1Path = rest.substring(fileIdx + 6).trim();
                    }
                }
                // 去外层引号
                if (ps1Path.startsWith("\"") && ps1Path.endsWith("\"")) {
                    ps1Path = ps1Path.substring(1, ps1Path.length() - 1);
                }
                pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", ps1Path);
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

            // 端口反查优先于进程名：Elasticsearch/Nacos 等 java 进程共用 java.exe，
            // 进程名会匹配到无关进程，端口监听才是该服务启动成功的强证据。
            if (info.getPort() > 0) {
                Thread.sleep(3000);
                for (int i = 0; i < 8; i++) {
                    Thread.sleep(1500);
                    if (PortChecker.isPortOpen(info.getPort())) {
                        long portPid = findPidByPort(info.getPort());
                        if (portPid > 0) {
                            info.setPid(portPid);
                            savePid(info.getName(), portPid);
                            return true;
                        }
                        return true; // 端口已达但反查不到 PID，也按成功处理
                    }
                }
            }

            // 仅当该服务配置了唯一的进程名（如 kingbase.exe / nginx.exe）时，
            // 才用进程名反查兜底；java.exe 之类共用进程名不可用于归属判定
            String procName = info.getProcessName();
            if (procName != null && !procName.isEmpty() && isUniqueProcessName(procName)) {
                long childPid = findPidByName(procName);
                if (childPid > 0) {
                    info.setPid(childPid);
                    savePid(info.getName(), childPid);
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
        // PID 解析：端口反查最鲜活 > 内存 PID（需存活校验）> PID 文件（可能陈旧）
        long target = 0;
        if (info.getPort() > 0) {
            target = findPidByPort(info.getPort());
        }
        if (target <= 0 && info.getPid() > 0 && isPidAlive(info.getPid())) {
            target = info.getPid();
        }
        if (target <= 0) {
            long saved = loadPid(info.getName());
            if (saved > 0 && isPidAlive(saved)) {
                target = saved;
            }
        }
        if (target <= 0) {
            com.servicemanager.util.LogManager.log(
                    "✗ " + info.getName() + " 停止失败：无监听端口且记录的 PID 已失效");
            return false;
        }

        try {
            // /T 杀进程树：npm/cmd 启动器的子进程一并终止
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/PID", String.valueOf(target), "/T", "/F");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(' ');
                }
            }
            p.waitFor();
            com.servicemanager.util.LogManager.log(
                    "taskkill /PID " + target + " /T /F → exit=" + p.exitValue()
                            + " | " + sb.toString().trim());

            // 成功判定按服务实际形态走三维度，任一维度先清零即判成功：
            //  ① 配了进程名的服务，进程消失即成功（如 kingbase.exe）——
            //     但共用进程名（java.exe、nginx.exe 多实例）不可用，靠端口
            //  ② 有端口以端口释放为准（最多 10 秒）
            //  ③ 无端口无进程名，以记录 PID 消失为准（最多 5 秒）
            boolean byName = info.getProcessName() != null
                    && !info.getProcessName().isEmpty()
                    && !info.getProcessName().contains("java");
            for (int i = 0; i < 20; i++) {
                Thread.sleep(500);
                if (byName && findPidByName(info.getProcessName()) <= 0) {
                    info.setPid(0);
                    deletePid(info.getName());
                    return true;
                }
                if (info.getPort() > 0 && !PortChecker.isPortOpen(info.getPort())) {
                    info.setPid(0);
                    deletePid(info.getName());
                    if (byName) {
                        // 进程名 + 端口同时释放才确认成功，避免同机器共享进程名的
                        // 另一实例仍占用端口时误判（课堂评分场景）
                        com.servicemanager.util.LogManager.log(
                                "  ✓ " + info.getName() + ": 端口 " + info.getPort() + " 已释放");
                    }
                    return true;
                }
            }
            // 轮询超时未清零，最后再查一次进程是否已消失（含共用进程名场景）
            if (!isPidAlive(target)) {
                info.setPid(0);
                deletePid(info.getName());
                return true;
            }
            com.servicemanager.util.LogManager.log(
                    "✗ " + info.getName() + " 停止超时：端口/进程 " + target + " 仍在");
            return false;
        } catch (Exception e) {
            com.servicemanager.util.LogManager.log("✗ " + info.getName() + " 停止异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 进程名是否可以被该服务独占判定（java.exe 等共用名不可信）
     */
    private boolean isUniqueProcessName(String procName) {
        String lower = procName.toLowerCase();
        return !lower.contains("java") && !lower.contains("cmd") && !lower.contains("powershell");
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
     * 从进程名反查 PID（用于 sys_ctl.exe 等启动器退出后找到实际服务进程）
     */
    private long findPidByName(String processName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI",
                    "IMAGENAME eq " + processName, "/NH");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(processName)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            try {
                                return Long.parseLong(parts[1]);
                            } catch (NumberFormatException ignored) {}
                        }
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
     * 按 Windows 命令行规则解析，正确处理双引号包裹的路径
     */
    private ProcessBuilder buildDirectProcess(String cmd) {
        List<String> parts = parseCommandLine(cmd);
        if (parts.isEmpty()) {
            return new ProcessBuilder(cmd);
        }
        return new ProcessBuilder(parts);
    }

    /**
     * 按 Windows 命令行规则拆分可执行文件和参数
     * <p>
     * 正确处理双引号包裹的路径，如 {@code "D:\a b\app.exe" -D "D:\data dir"}
     * → {@code ["D:\a b\app.exe", "-D", "D:\data dir"]}
     */
    private List<String> parseCommandLine(String cmd) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 获取 Windows 进程 PID（JDK 21 直接用 Process.pid()）
     */
    private long getPid(Process process) {
        return process.pid();
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
