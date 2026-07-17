package com.servicemanager.model;

/**
 * 服务类型枚举
 */
public enum ServiceType {

    /** Windows 系统服务，通过 sc 命令管理 */
    WINDOWS_SERVICE("Windows 服务"),

    /** 独立进程（exe / bat / ps1），通过 ProcessBuilder + PID 管理 */
    PROCESS("进程");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
