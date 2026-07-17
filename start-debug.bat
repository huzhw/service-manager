@echo off
cd /d "%~dp0"

:: Auto-elevate to admin
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo ========================================
echo   Service Manager — Debug Mode (Admin)
echo ========================================
echo Current dir: %CD%
echo.

:: Force rebuild to get latest fixes
echo [1/2] Building JAR...
call mvn package -DskipTests
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

:: Run in foreground with console output
echo [2/2] Starting (foreground, Ctrl+C to stop)...
echo.
java -jar "target\service-manager-1.0.0.jar" 2>&1

echo.
echo App exited, code: %ERRORLEVEL%
pause
