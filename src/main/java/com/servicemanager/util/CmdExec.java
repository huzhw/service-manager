package com.servicemanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Windows 命令执行工具
 * <p>
 * 统一通过 cmd /c 执行命令，输出按 GBK 解码（Windows 控制台默认编码）。
 * 原三个 Panel 各自持有的 exec 工具方法收敛到这里复用。
 */
public final class CmdExec {

    private CmdExec() {
    }

    /**
     * 执行命令并返回完整输出（stdout+stderr 合并）
     *
     * @param cmd 命令行（不含 cmd /c 前缀）
     * @return 输出文本；执行异常返回 null
     */
    public static String exec(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 执行命令并仅返回第一行输出（用于取版本号等单行结果）
     *
     * @param cmd 命令行（不含 cmd /c 前缀）
     * @return 第一行文本；无输出或异常返回 null
     */
    public static String execLine(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line = reader.readLine();
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
