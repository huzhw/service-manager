package com.servicemanager.model;

/**
 * 单个服务定义
 */
public class ServiceInfo {

    /** 显示名称 */
    private String name;

    /** 服务类型 */
    private ServiceType type;

    /** Windows 服务名 / 进程启动命令 */
    private String identifier;

    /** 工作目录（进程类型） */
    private String workingDir;

    /** 进程名（进程类型，用于查找 PID，如 nginx.exe） */
    private String processName;

    /** 端口号 */
    private int port;

    /** 分类：数据库 / 缓存 / 搜索引擎 / 对象存储 / Web服务器 / 注册中心 */
    private String category;

    /** 分组名，同组服务联动启停（如 Oracle 主库+监听） */
    private String groupName;

    /** 启动顺序，越小越先启动 */
    private int startOrder;

    /** 停止顺序，越小越先停止 */
    private int stopOrder;

    /** 当前状态：RUNNING / STOPPED / UNKNOWN */
    private String status = "UNKNOWN";

    /** 进程 PID（仅进程类型） */
    private long pid;

    /** 启动时间戳（毫秒），0 = 未启动 */
    private long startTime;

    public ServiceInfo() {
    }

    public ServiceInfo(String name, ServiceType type, String identifier, int port, String category) {
        this.name = name;
        this.type = type;
        this.identifier = identifier;
        this.port = port;
        this.category = category;
    }

    // ========== Getters & Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ServiceType getType() {
        return type;
    }

    public void setType(ServiceType type) {
        this.type = type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getStartOrder() {
        return startOrder;
    }

    public void setStartOrder(int startOrder) {
        this.startOrder = startOrder;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public void setStopOrder(int stopOrder) {
        this.stopOrder = stopOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
}
