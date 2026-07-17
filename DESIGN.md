# 服务管理面板 设计文档

## 1. 概述

### 1.1 背景

9953 台式机作为本地开发机，运行 9 个数据库/中间件（10 个进程），启动方式分散在 Windows 服务、启动文件夹脚本、Docker 容器三种机制中。日常开发时需要统一查看状态、按需启停，缺少一个集中管理工具。

### 1.2 目标

做一个 Windows 桌面程序，用一个面板接管所有服务的启停控制和状态监控。

### 1.3 技术栈

| 维度 | 选择 | 版本 |
|------|------|------|
| 语言 | Java | 1.8 |
| 构建 | Maven | 3.x |
| GUI | Swing + FlatLaf | 2.6 |
| 系统托盘 | java.awt.SystemTray | JDK 内置 |
| 服务控制 | ProcessBuilder + sc/tasklist/taskkill | Windows 内置 |
| 打包 | maven-shade-plugin | Fat JAR |

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                       App.java                           │
│              (入口：单实例锁 + FlatLaf 初始化)              │
└──────────┬──────────────────────────────┬───────────────┘
           │                              │
     ┌─────▼──────┐               ┌──────▼──────┐
     │ TrayManager │◄──── 调用 ───│  MainFrame   │
     │  (系统托盘)   │              │   (主面板)    │
     └─────────────┘              └──────┬───────┘
           │                              │
           │                     ┌────────▼────────┐
           │                     │  ServiceConfig   │
           │                     │  (10个服务定义)    │
           │                     └────────┬────────┘
           │                              │
           │              ┌───────────────┼───────────────┐
           │              │               │               │
           │     ┌────────▼────────┐ ┌───▼────────┐      │
           │     │ ServiceController│ │ ServiceInfo │      │
           │     │    (接口)        │ │  (实体)      │      │
           │     └────────┬────────┘ └────────────┘      │
           │              │                               │
           │     ┌────────┼────────┐                      │
           │     │        │        │                      │
     ┌─────▼─────▼─┐ ┌───▼────────▼────┐                 │
     │ Windows     │ │   Process        │                 │
     │ Service     │ │   Controller     │                 │
     │ Controller  │ │                  │                 │
     │ (sc命令)     │ │ (PID跟踪)        │                 │
     └─────────────┘ └──────────────────┘                 │
           │               │                              │
           ▼               ▼                              │
    ┌──────────┐    ┌──────────────┐                      │
    │ Windows  │    │ pid.properties│                     │
    │ Services │    │ (%TEMP%)      │                     │
    └──────────┘    └──────────────┘                      │
```

---

## 3. 模块说明

### 3.1 模型层（model）

| 类 | 职责 |
|----|------|
| `ServiceType` | 枚举：`WINDOWS_SERVICE`、`PROCESS` |
| `ServiceInfo` | 服务实体，包含名称、类型、标识、端口、启停顺序、状态、PID 等字段 |

**启停顺序设计**：`startOrder` 越小越先启动，`stopOrder` 越小越先停止。Oracle 双服务利用此机制，启动时监听(1)→主库(2)，停止时主库(1)→监听(2)。

### 3.2 配置层（config）

`ServiceConfig.buildServices()` 定义了全部 10 个服务：

| # | 服务 | 类型 | 标识 |
|---|------|------|------|
| 1 | MySQL | Windows 服务 | `MySQL80` |
| 2 | Redis | Windows 服务 | `redis-x64-3.0.504` |
| 3 | Oracle 主库 | Windows 服务 | `OracleServiceORCL` |
| 4 | Oracle 监听 | Windows 服务 | `OracleOraDb11g_home1TNSListener` |
| 5 | 达梦 DM8 | Windows 服务 | `DmServiceDMSERVER` |
| 6 | PostgreSQL | Windows 服务 | `postgresql-x64-16` |
| 7 | Elasticsearch | 进程 | PowerShell 启动 `start_es.ps1` |
| 8 | MinIO | 进程 | `minio.exe server` |
| 9 | Nginx | 进程 | `nginx.exe` |
| 10 | Nacos | 进程 | `restart_nacos.bat` |

增删改服务只需修改此文件，无需改其他代码。

### 3.3 服务控制层（service）

**接口 `ServiceController`**：
```java
String getStatus(ServiceInfo info)  // 返回 RUNNING / STOPPED / UNKNOWN
boolean start(ServiceInfo info)     // 启动服务
boolean stop(ServiceInfo info)      // 停止服务
```

**`WindowsServiceController`**：
- 状态查询：`sc query <服务名>` → 解析输出中的 `RUNNING`/`STOPPED`
- 启动：`sc start <服务名>`
- 停止：`sc stop <服务名>`（异步，不等待完全停止）

**`ProcessController`**：
- 启动：`ProcessBuilder` 执行命令，反射获取 PID，持久化到 `%TEMP%/service-manager/pids.properties`
- 状态：先查内存 PID → 查持久化文件 → `tasklist /FI "PID eq xxx"` 验证存活
- 停止：`taskkill /PID xxx /T /F`（`/T` 同时杀子进程树）
- PID 获取：Java 8 通过反射访问 `ProcessImpl.pid` 字段

**PID 持久化流程**：
```
启动 → 获取PID → 写 pids.properties ──→ 程序重启 → 读 pids.properties → tasklist验证 → 恢复状态
                      │                                                        │
                      └────────────────────────────────────────────────────────┘
                                           PID 已失效 → 丢弃 → 状态=STOPPED
```

### 3.4 UI 层（ui）

**`MainFrame`**：
- 顶部工具栏：启动全部 / 停止全部 / 刷新
- 中部 JTable：序号 | 服务名 | 类型 | 端口 | 状态灯 | 操作按钮
- 状态灯渲染：●绿色=运行中，●红色=已停止，●橙色=启动中/停止中，●灰色=未知
- 操作列：JButton 渲染 + 编辑，点击触发启停（后台线程执行，避免 UI 卡死）
- 底部状态栏：共 X 个服务，Y 个运行中
- 30 秒 Timer 自动刷新
- 关闭窗口 → `setDefaultCloseOperation(HIDE_ON_CLOSE)` 隐藏到托盘

**`TrayManager`**：
- 16×16 蓝色圆点作为托盘图标
- 右键菜单：显示面板 / 启动全部 / 停止全部 / 开机自启（带状态标记） / 退出
- 双击图标 → 显示面板
- `StartupManager` 内部类：通过注册表 `HKCU\...\Run` 管理开机自启

---

## 4. 关键设计决策

### 4.1 为什么不用 java.awt.Desktop 或外部库做托盘

`java.awt.SystemTray` 是 JDK 1.6 内置 API，零额外依赖。`pystray`（Python）和第三方 Java 托盘库都不如直接用标准库简单可靠。

### 4.2 为什么 PID 要持久化到文件

进程类服务（ES/Nacos/MinIO/Nginx）不是 Windows 服务，启动后程序只拿到一个 `Process` 对象。程序重启或异常退出后，这个对象丢失，无法再通过 `taskkill` 停止已运行的进程。持久化 PID 可以跨会话恢复控制。

### 4.3 为什么用 ServerSocket 做单实例锁

经典 Java 方案，比文件锁更可靠——进程崩溃时端口自动释放，不会出现残留锁文件的问题。

### 4.4 为什么启停操作在后台线程

`sc start`/`sc stop` 和 `taskkill` 都是阻塞 I/O，在主线程执行会冻住 UI。所有启停操作都提交到新线程执行，通过 `SwingUtilities.invokeLater` 更新界面。

---

## 5. 数据流

### 5.1 刷新状态

```
用户点击"刷新" / Timer触发
        │
        ▼
  refreshAllStatus()
        │
        ▼ mainFrame新线程
  ┌─────────────────────────┐
  │ for each ServiceInfo:   │
  │   ctrl.getStatus(svc)   │──── WindowsServiceController ──► sc query
  │   svc.setStatus(result) │──── ProcessController ──► PID检查 + tasklist
  └────────┬────────────────┘
           │
           ▼ SwingUtilities.invokeLater
  tableModel.fireTableDataChanged()   // 刷新表格
  statusLabel.setText(...)            // 更新状态栏
```

### 5.2 用户启停单个服务

```
点击表格操作列按钮
        │
        ▼ ButtonEditor.stopCellEditing()
  根据当前状态判断：运行中→停止，已停止→启动
        │
        ▼ 新线程
  ctrl.start(svc) 或 ctrl.stop(svc)
        │
        ▼ 等待800ms
  refreshAllStatus()   // 立即刷新看到最新状态
```

### 5.3 一键启动全部

```
遍历 services（按startOrder升序）
  ├── 跳过已运行的服务
  ├── ctrl.start(svc)
  ├── 间隔1500ms（避免资源争抢）
  └── 最后统一 refreshAllStatus()
```

---

## 6. 异常处理

| 场景 | 处理方式 |
|------|----------|
| `sc query` 失败（权限不足/服务不存在） | catch 异常，返回 "UNKNOWN" |
| `sc start` 权限不足 | 返回 false，用户需以管理员运行 |
| `taskkill` 失败（进程已退出） | 清除 PID 记录，状态置为 STOPPED |
| FlatLaf 主题加载失败 | 降级到系统默认 LAF |
| 系统托盘不支持（Windows Server 无桌面） | 打印日志，程序仍可运行 |
| 反射获取 PID 失败（非 Windows / JDK 变化） | 返回 0，降级为不可控 |
| PID 文件读写失败 | 静默忽略，下次启动无法恢复状态 |

---

## 7. 待优化项

1. **管理员权限提示**：`sc` 命令需要管理员权限，目前静默失败，可加 UAC 提权提示
2. **启动依赖**：目前只做顺序启动，未做"等 MySQL 完全就绪再启动依赖服务"的等待逻辑
3. **端口检测**：可额外通过 `netstat -ano` 检测端口是否监听，双重验证服务状态
4. **日志**：进程类服务的 stdout/stderr 目前丢弃，可重定向到日志文件方便排查
5. **PyInstaller 打包**：如果用户要求，可用 `launch4j` 打包成 exe，无需依赖 JDK

---

## 8. 项目路径

```
F:\idea-workspase\service-manager\
├── pom.xml
├── DESIGN.md                       ← 本文档
├── src/main/java/com/servicemanager/
│   ├── App.java
│   ├── config/ServiceConfig.java
│   ├── model/ServiceInfo.java
│   ├── model/ServiceType.java
│   ├── service/ServiceController.java
│   ├── service/WindowsServiceController.java
│   ├── service/ProcessController.java
│   ├── ui/MainFrame.java
│   └── ui/TrayManager.java
└── target/service-manager-1.0.0.jar
```

---

*最后更新：2026-07-17*
