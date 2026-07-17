---
name: javafx-redesign
description: Service Manager 从 Swing 迁移到 JavaFX 的设计方案
metadata:
  type: project
---

## 背景

原 Service Manager 1.x 基于 Swing + FlatLaf，功能完整但 UI 现代感不足。迁移到 JavaFX 21 获得 CSS 主题、现代控件、数据绑定等能力。

## 架构

```
App.java (JavaFX Application)
├── MainWindow.java (主窗口)
│   ├── 侧边栏导航 (VBox + Button)
│   └── 内容区 (StackPane)
│       ├── ServiceTable.java (JavaFX TableView)
│       │   服务管理表格：序号/名称/类型/端口/目录/版本/状态/操作
│       │   纯 JavaFX 实现，CSS 可定制
│       ├── VersionPanel (SwingNode 包裹)
│       ├── PortToolPanel (SwingNode 包裹)
│       └── FileAssocPanel (SwingNode 包裹)
├── TrayManager.java (系统托盘)
│   适配 MainWindow，托盘菜单调用公开方法
└── 业务层不变
    ├── ProcessController (JDK 21 Process.pid() 替代反射)
    ├── WindowsServiceController
    ├── ServiceInfo, ServiceType
    ├── ServiceConfigLoader, ServiceConfig
    ├── PortChecker, LogManager
    └── PID 文件持久化
```

## 技术选型

| 项目 | 选择 | 原因 |
|------|------|------|
| JDK | 21 (ES 自带) | JavaFX 需要 JDK 11+, JDK 8 不包含 |
| JavaFX | 21.0.5 (OpenJFX) | LTS 稳定版 |
| 构建 | Maven shade + dependency-plugin | app JAR 瘦包 + JavaFX lib 目录 |
| 主题 | 自定义 CSS dark theme | 完全可控，比 FlatLaf 更强 |
| 旧 Swing 页 | SwingNode 包裹 | 渐进迁移，不需要一次重写完 |

## 关键决策

1. **JDK 升级**：从 Java 8 → 21，ProcessController 用 `Process.pid()` 替代反射
2. **JavaFX 分离部署**：Maven shade 排除 JavaFX，dependency-plugin 复制到 `target/lib/`，start.bat 通过 `--module-path` 加载
3. **渐进迁移**：服务管理表格纯 JavaFX，其余页面用 SwingNode 包裹暂不重写
4. **PID 字段**：原来反射不可靠（无 pid 字段），JDK 21 直接 `process.pid()` 稳定获取

## CSS 主题设计

`src/main/resources/css/theme.css` — Dark premium theme
- 颜色体系：深蓝基底 (#1a1a2e)、卡片 (#16213e)、强调蓝 (#5b8def)
- 状态色：绿(运行)、红(停止)、橙(过渡)
- 侧边栏 200px 宽，选中态左侧蓝条
- 表格斑马纹，hover 高亮

## 运行时依赖

- JAR: `target/service-manager-2.0.0.jar`
- JavaFX SDK: `target/lib/javafx-*.jar`
- JDK: `D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk`
- 启动: `start.bat` (管理员自提权 + --module-path)

## 与旧版差异

| | 1.x (Swing) | 2.0 (JavaFX) |
|---|---|---|
| UI 框架 | Swing + FlatLaf | JavaFX 21 + CSS |
| JDK | Java 8 | JDK 21 |
| PID 获取 | 反射 (不稳定) | Process.pid() |
| 主题 | FlatLaf + 系统 L&F | 自定义 CSS dark theme |
| 表格 | JTable | TableView |
| 部署 | 单一 fat JAR | JAR + lib 目录 |
