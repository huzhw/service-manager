package com.servicemanager.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.servicemanager.model.ServiceInfo;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务配置加载器
 * <p>
 * 优先从工作目录的 services.json 读取服务列表；文件不存在时从硬编码默认值生成并写入，
 * 之后用户可直接编辑 JSON 增删改服务，无需重新编译。
 */
public class ServiceConfigLoader {

    private static final String CONFIG_FILE = "services.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ServiceInfo>>() {}.getType();

    /**
     * 加载服务列表
     */
    public static List<ServiceInfo> load() {
        File file = new File(CONFIG_FILE);
        List<ServiceInfo> services;

        // 1. 尝试从 JSON 文件读取
        if (file.exists()) {
            services = loadFromFile(file);
            if (services != null && !services.isEmpty()) {
                return services;
            }
            System.err.println("services.json 解析失败，回退默认配置");
        }

        // 2. 回退硬编码默认值
        services = ServiceConfig.buildServices();

        // 3. 首次运行，写入 JSON 文件供用户编辑
        saveToFile(file, services);

        return services;
    }

    /**
     * 从 JSON 文件读取服务列表
     */
    private static List<ServiceInfo> loadFromFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, LIST_TYPE);
        } catch (Exception e) {
            System.err.println("读取 services.json 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 写入 JSON 文件
     */
    private static void saveToFile(File file, List<ServiceInfo> services) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(services, LIST_TYPE, writer);
            System.out.println("已生成 " + CONFIG_FILE + "，可编辑后重启生效");
        } catch (Exception e) {
            System.err.println("写入 services.json 失败: " + e.getMessage());
        }
    }
}
