package com.servicemanager.config;

import com.servicemanager.model.ServiceInfo;
import com.servicemanager.model.ServiceType;

import java.util.ArrayList;
import java.util.List;

/**
 * 9 个本地服务的定义配置
 * <p>
 * 增删改服务只需修改这里的 buildServices() 方法
 */
public class ServiceConfig {

    /**
     * 构建全部服务列表
     */
    public static List<ServiceInfo> buildServices() {
        List<ServiceInfo> list = new ArrayList<>();

        // ========== Windows 服务 ==========

        ServiceInfo mysql = new ServiceInfo("MySQL", ServiceType.WINDOWS_SERVICE, "MySQL80", 3306, "数据库");
        mysql.setVersion("8.0");
        mysql.setWorkingDir("C:\\Program Files\\MySQL\\MySQL Server 8.0");
        list.add(mysql);

        ServiceInfo redis = new ServiceInfo("Redis", ServiceType.WINDOWS_SERVICE, "redis-x64-5.0.14.1", 6379, "缓存");
        redis.setVersion("5.0.14");
        redis.setWorkingDir("F:\\Program Files\\Redis-x64-5.0.14.1");
        list.add(redis);

        // Oracle：启动先监听→后主库，停止先主库→后监听
        ServiceInfo oracleDb = new ServiceInfo("Oracle 主库", ServiceType.WINDOWS_SERVICE, "OracleServiceORCL", 1521, "数据库");
        oracleDb.setVersion("11g");
        oracleDb.setWorkingDir("D:\\app\\Administrator\\product\\11.2.0\\dbhome_1");
        oracleDb.setStartOrder(2);
        oracleDb.setStopOrder(1);
        oracleDb.setGroupName("Oracle");
        list.add(oracleDb);

        ServiceInfo oracleListener = new ServiceInfo("Oracle 监听", ServiceType.WINDOWS_SERVICE, "OracleOraDb11g_home1TNSListener", 1521, "数据库");
        oracleListener.setVersion("11g");
        oracleListener.setWorkingDir("D:\\app\\Administrator\\product\\11.2.0\\dbhome_1");
        oracleListener.setStartOrder(1);
        oracleListener.setStopOrder(2);
        oracleListener.setGroupName("Oracle");
        list.add(oracleListener);

        ServiceInfo dm = new ServiceInfo("达梦 DM8", ServiceType.WINDOWS_SERVICE, "DmServiceDMSERVER", 5236, "数据库");
        dm.setVersion("DM8");
        dm.setWorkingDir("D:\\dmdbms");
        list.add(dm);

        ServiceInfo pg = new ServiceInfo("PostgreSQL", ServiceType.WINDOWS_SERVICE, "postgresql-x64-16", 5432, "数据库");
        pg.setVersion("16");
        pg.setWorkingDir("D:\\PostgreSQL\\16");
        list.add(pg);

        // ========== 进程类 ==========

        ServiceInfo es = new ServiceInfo("Elasticsearch", ServiceType.PROCESS,
                "powershell -ExecutionPolicy Bypass -File \"D:\\tools\\elasticsearch-8.11.3\\start_es.ps1\"",
                1200, "搜索引擎");
        es.setVersion("8.11.3");
        es.setWorkingDir("D:\\tools\\elasticsearch-8.11.3");
        es.setProcessName("java.exe");
        list.add(es);

        ServiceInfo minio = new ServiceInfo("MinIO", ServiceType.PROCESS,
                "D:\\tools\\minio\\minio.exe server D:\\minio-data --console-address :9001",
                9000, "对象存储");
        minio.setVersion("latest");
        minio.setWorkingDir("D:\\tools\\minio");
        minio.setProcessName("minio.exe");
        list.add(minio);

        ServiceInfo nginx = new ServiceInfo("Nginx", ServiceType.PROCESS,
                "D:\\tools\\nginx-1.30.3\\nginx.exe",
                6666, "Web服务器");
        nginx.setVersion("1.30.3");
        nginx.setWorkingDir("D:\\tools\\nginx-1.30.3");
        nginx.setProcessName("nginx.exe");
        list.add(nginx);

        ServiceInfo nacos = new ServiceInfo("Nacos", ServiceType.PROCESS,
                "cmd /c \"F:\\idea-workspase\\service-manager\\scripts\\start_nacos_silent.bat\"",
                8848, "注册中心");
        nacos.setVersion("3.2.2");
        nacos.setWorkingDir("D:\\tools\\nacos");
        nacos.setProcessName("java.exe");
        list.add(nacos);

        return list;
    }
}
