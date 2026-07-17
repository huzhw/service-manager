package com.servicemanager.service;

import com.servicemanager.model.ServiceInfo;

/**
 * 服务控制器接口
 */
public interface ServiceController {

    /**
     * 获取服务状态
     *
     * @return "RUNNING" / "STOPPED" / "UNKNOWN"
     */
    String getStatus(ServiceInfo info);

    /**
     * 启动服务
     *
     * @return true 成功
     */
    boolean start(ServiceInfo info);

    /**
     * 停止服务
     *
     * @return true 成功
     */
    boolean stop(ServiceInfo info);
}
