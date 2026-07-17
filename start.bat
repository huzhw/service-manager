@echo off
chcp 65001 >nul
cd /d "%~dp0"

:: 检查 JAR
if not exist "target\service-manager-1.0.0.jar" (
    echo 正在编译打包...
    call mvn package -DskipTests -q
    if errorlevel 1 (
        echo 编译失败，请检查 Maven 和 JDK 配置
        pause
        exit /b 1
    )
)

:: 后台启动（javaw 无控制台窗口）
start "" javaw -jar "target\service-manager-1.0.0.jar"

echo 服务管理面板已启动，查看任务栏右下角托盘图标。
echo 如无反应，请运行 start-debug.bat 查看错误信息。
