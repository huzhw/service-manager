package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.model.ServiceType;
import com.servicemanager.util.LogManager;
import com.servicemanager.util.PortChecker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务编排器 — 服务状态刷新、单个/分组启停、批量启停
 * <p>
 * 原 MainWindow 中的业务逻辑整体迁移到这里，成为无 UI 依赖的后端核心；
 * REST API 与系统托盘共用本类。
 */
public class ServiceOrchestrator {

    private static final ServiceOrchestrator INSTANCE = new ServiceOrchestrator();

    /** 刷新周期（秒），与原 30s 自动刷新一致 */
    private static final long REFRESH_INTERVAL_SECONDS = 30;

    private final List<ServiceInfo> services = new ArrayList<>();
    private final WindowsServiceController winCtrl = new WindowsServiceController();
    private final ProcessController procCtrl = new ProcessController();

    /** 批量/分组操作互斥标记：同一时刻只允许一个长任务 */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    private ServiceOrchestrator() {
    }

    public static ServiceOrchestrator get() {
        return INSTANCE;
    }

    /**
     * 初始化（应用启动时调用一次）：装载服务、立即刷新、起定时刷新
     */
    public synchronized void init(List<ServiceInfo> loaded) {
        if (scheduler != null) {
            return; // 已初始化
        }
        services.clear();
        services.addAll(loaded);
        refreshAllStatus();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "svc-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(ServiceOrchestrator.this::refreshAllStatus,
                REFRESH_INTERVAL_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public List<ServiceInfo> getServices() {
        return services;
    }

    /**
     * 是否有批量/分组长任务进行中
     */
    public boolean isBusy() {
        return busy.get();
    }

    /**
     * 尝试占用执行权（CAS），配合 endJob 使用
     */
    public boolean tryBeginJob() {
        return busy.compareAndSet(false, true);
    }

    /**
     * 释放执行权
     */
    public void endJob() {
        busy.set(false);
    }

    /**
     * 刷新全部服务状态（后台线程），含端口宽限期判断
     */
    public void refreshAllStatus() {
        Thread t = new Thread(() -> {
            for (ServiceInfo svc : services) {
                ServiceController ctrl = controllerOf(svc);
                String status = ctrl.getStatus(svc);
                if ("RUNNING".equals(status) && svc.getPort() > 0) {
                    if (!PortChecker.isPortOpen(svc.getPort())) {
                        // 刚启动 60 秒内给宽限期，数据库等需要初始化时间
                        long elapsed = System.currentTimeMillis() - svc.getStartTime();
                        if (svc.getStartTime() > 0 && elapsed < 60_000) {
                            status = "STARTING";
                        } else {
                            status = "PORT_UNREACHABLE";
                        }
                    }
                }
                svc.setStatus(status);
            }
        }, "svc-status-refresh");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 全部启动（按 startOrder 升序，跳过已运行）；忙时直接返回 false
     *
     * @return true = 任务已提交
     */
    public boolean startAll() {
        if (!tryBeginJob()) {
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                LogManager.log("→ 批量启动...");
                List<ServiceInfo> sorted = new ArrayList<>(services);
                sorted.sort(Comparator.comparingInt(ServiceInfo::getStartOrder));
                int ok = 0;
                int fail = 0;
                for (ServiceInfo svc : sorted) {
                    if ("RUNNING".equals(svc.getStatus())) {
                        continue;
                    }
                    LogManager.log("→ 启动 " + svc.getName() + " ...");
                    try {
                        boolean result = controllerOf(svc).start(svc);
                        if (result) {
                            svc.setStatus("RUNNING");
                            svc.setStartTime(System.currentTimeMillis());
                            LogManager.log("  ✓ " + svc.getName() + " 启动成功");
                            ok++;
                        } else {
                            svc.setStatus("STOPPED");
                            LogManager.log("  ✗ " + svc.getName() + " 失败");
                            fail++;
                        }
                    } catch (Exception e) {
                        // 单服务异常不打断批量：记录并继续下一个，finally 仍会释放互斥
                        svc.setStatus("STOPPED");
                        LogManager.log("  ✗ " + svc.getName() + " 启动异常: " + e.getMessage());
                        fail++;
                    }
                    sleepQuietly(1500);
                }
                LogManager.log("批量完成: 成功 " + ok + ", 失败 " + fail);
                refreshAllStatus();
            } finally {
                // 无论是否异常都必须释放互斥标记，否则后续所有启停被静默拒绝
                endJob();
            }
        }, "svc-start-all");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /**
     * 全部停止（按 stopOrder 降序，跳过已停止）；忙时直接返回 false
     */
    public boolean stopAll() {
        if (!tryBeginJob()) {
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                LogManager.log("← 批量停止...");
                List<ServiceInfo> sorted = new ArrayList<>(services);
                sorted.sort(Comparator.comparingInt(ServiceInfo::getStopOrder).reversed());
                int ok = 0;
                int fail = 0;
                for (ServiceInfo svc : sorted) {
                    if ("STOPPED".equals(svc.getStatus())) {
                        continue;
                    }
                    LogManager.log("← 停止 " + svc.getName() + " ...");
                    try {
                        boolean result = controllerOf(svc).stop(svc);
                        if (result) {
                            svc.setStatus("STOPPED");
                            svc.setPid(0);
                            svc.setStartTime(0);
                            LogManager.log("  ✓ " + svc.getName() + " 已停止");
                            ok++;
                        } else {
                            LogManager.log("  ✗ " + svc.getName() + " 失败");
                            fail++;
                        }
                    } catch (Exception e) {
                        // 单服务异常不打断批量：记录并继续下一个，finally 仍会释放互斥
                        svc.setStatus("STOPPED");
                        LogManager.log("  ✗ " + svc.getName() + " 停止异常: " + e.getMessage());
                        fail++;
                    }
                    sleepQuietly(1000);
                }
                LogManager.log("批量完成: 成功 " + ok + ", 失败 " + fail);
                refreshAllStatus();
            } finally {
                endJob();
            }
        }, "svc-stop-all");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /**
     * 单个服务的启/停动作 — 同组服务联动启停
     *
     * @param stopping true=停止 false=启动
     * @return true = 任务已提交；false = 忙碌中被拒绝
     */
    public boolean triggerGroupAction(ServiceInfo svc, boolean stopping) {
        List<ServiceInfo> group = getGroupMembers(svc);
        if (!tryBeginJob()) {
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                group.sort(stopping
                        ? Comparator.comparingInt(ServiceInfo::getStopOrder)
                        : Comparator.comparingInt(ServiceInfo::getStartOrder));
                for (ServiceInfo member : group) {
                    ServiceController ctrl = controllerOf(member);
                    if (stopping) {
                        if ("STOPPED".equals(member.getStatus())) {
                            continue;
                        }
                        member.setStatus("STOPPING");
                        LogManager.log("← 停止 " + member.getName() + " ...");
                        boolean ok = ctrl.stop(member);
                        if (ok) {
                            member.setStartTime(0);
                        }
                        LogManager.log(ok ? "  ✓ " + member.getName() + " 已停止"
                                : "  ✗ " + member.getName() + " 失败");
                    } else {
                        if ("RUNNING".equals(member.getStatus())) {
                            continue;
                        }
                        member.setStatus("STARTING");
                        LogManager.log("→ 启动 " + member.getName() + " ...");
                        boolean ok = ctrl.start(member);
                        if (ok) {
                            member.setStartTime(System.currentTimeMillis());
                        }
                        LogManager.log(ok ? "  ✓ " + member.getName() + " 启动成功"
                                : "  ✗ " + member.getName() + " 失败");
                    }
                    sleepQuietly(800);
                }
                refreshAllStatus();
            } finally {
                endJob();
            }
        }, "svc-group-action");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /**
     * 应用退出时停止定时器
     */
    public synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    // ==========================================
    //  内部工具
    // ==========================================

    /**
     * 按服务类型取对应控制器
     */
    private ServiceController controllerOf(ServiceInfo svc) {
        return svc.getType() == ServiceType.WINDOWS_SERVICE ? winCtrl : procCtrl;
    }

    /**
     * 取同组成员（含自身）；无组名时返回单元素列表
     */
    private List<ServiceInfo> getGroupMembers(ServiceInfo svc) {
        String group = svc.getGroupName();
        if (group == null || group.isEmpty()) {
            List<ServiceInfo> self = new ArrayList<>();
            self.add(svc);
            return self;
        }
        List<ServiceInfo> members = new ArrayList<>();
        for (ServiceInfo s : services) {
            if (group.equals(s.getGroupName())) {
                members.add(s);
            }
        }
        return members;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
