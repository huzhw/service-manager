@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   服务管理面板 — 调试模式
echo ========================================
echo 当前目录: %CD%
echo.

:: 检查 JAR
if not exist "target\service-manager-1.0.0.jar" (
    echo [1/2] 正在打包...
    call mvn package -DskipTests
    if errorlevel 1 (
        echo 编译失败!
        pause
        exit /b 1
    )
) else (
    echo [1/2] JAR 已存在，跳过编译
)

:: 直接在前台运行，用 java 不用 javaw（能看到控制台输出）
echo [2/2] 启动应用（前台运行，Ctrl+C 退出）...
echo.
java -jar "target\service-manager-1.0.0.jar" 2>&1

echo.
echo 应用已退出，返回码: %ERRORLEVEL%
pause
