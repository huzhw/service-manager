@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   服务管理面板 — Service Manager
echo ========================================
echo.

:: 检查 JAR 是否存在
if not exist "target\service-manager-1.0.0.jar" (
    echo [1/2] 未找到 JAR，正在编译打包...
    call mvn package -DskipTests -q
    if errorlevel 1 (
        echo 编译失败，请检查 Maven 和 JDK 配置
        pause
        exit /b 1
    )
    echo 编译完成
) else (
    echo [1/2] JAR 已存在，跳过编译
)

echo [2/2] 启动应用...
echo.
start javaw -jar "target\service-manager-1.0.0.jar"
echo 服务管理面板已在系统托盘启动，查看任务栏右下角图标。
echo.
