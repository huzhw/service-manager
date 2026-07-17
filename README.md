# Service Manager 2.0

JavaFX 桌面应用，统一管理开发环境中的各类后台服务（数据库、缓存、搜索引擎等）、Node.js/Python 版本工具、端口查杀和文件关联。

## 环境要求

| 依赖 | 版本 | 路径 |
|------|------|------|
| JDK | 21 (ES 内置) | `D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk` |
| Maven | 3.x | 系统 PATH |

## 快速开始

### 编译

```bash
# 使用 ES 自带的 JDK 21
set JAVA_HOME=D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk
mvn clean package -DskipTests
```

### 启动

双击项目根目录的 `start.bat`，会自动提权并以 JavaFX 模式启动。

## 四大功能模块

| 模块 | 功能 | 面板 |
|------|------|------|
| 🖥 服务管理 | 批量启动/停止/监控各类后台服务，30 秒自动刷新 | JavaFX TableView |
| 📦 版本管理 | nvm (Node.js) / pyenv (Python) 版本查看、切换、安装 | JavaFX |
| 🔌 端口工具 | 查找端口占用进程 + 强制终止，常用端口一键查杀 | JavaFX |
| 📄 文件关联 | 查看和修改常用文件扩展名（含 Office）的默认打开方式 | JavaFX TableView |

## 开机自启

已通过 Windows 任务计划配置：

```
任务名称: Service-Manager-AutoStart
触发器:   系统启动时
延迟:     1 分钟
权限:     最高权限
执行:     F:\idea-workspase\service-manager\start.bat
```

管理方式：
- 查看：`Win+R` → `taskschd.msc` → 找到 `Service-Manager-AutoStart`
- 删除：`schtasks /delete /tn Service-Manager-AutoStart /f`

## 项目结构

```
src/main/java/com/servicemanager/
├── App.java                     # JavaFX 入口，单实例锁
├── MainWindow.java              # 主窗口：侧边栏 + 内容区 + 日志
├── ServiceTable.java            # 服务管理表格
├── model/
│   ├── ServiceInfo.java         # 服务实体
│   └── ServiceType.java         # 服务类型枚举
├── config/
│   ├── ServiceConfig.java       # 配置实体
│   └── ServiceConfigLoader.java # 配置加载（JSON/YAML）
├── service/
│   ├── ServiceController.java   # 服务控制接口
│   ├── ProcessController.java   # 进程模式控制
│   └── WindowsServiceController.java # Windows 服务控制
├── ui/
│   ├── VersionPanel.java        # 版本管理面板
│   ├── PortToolPanel.java       # 端口工具面板
│   ├── FileAssocPanel.java      # 文件关联面板
│   ├── TrayManager.java         # 系统托盘 + 开机自启注册表
│   └── AppIcon.java             # 托盘图标
└── util/
    ├── LogManager.java          # 日志管理（文件 + UI）
    └── PortChecker.java         # 端口检测
```

## 样式

全局主题：`src/main/resources/css/theme.css`（浅色护眼风格），所有四个面板统一走这个 CSS 文件。

## 常用端口快捷列表

`3000, 3001, 8051, 8021, 8087, 8095, 8091, 8080, 8000, 8686, 10535`

## 文件关联支持的扩展名

文本/代码：`.txt .md .java .json .xml .py .js .html .css`

Office：`.doc .docx .xls .xlsx .ppt .pptx .csv .vsd .vsdx`

其他：`.pdf .zip .png .jpg`
