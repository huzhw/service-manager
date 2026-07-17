@echo off
cd /d "%~dp0"

echo ========================================
echo   Service Manager — Debug Mode
echo ========================================
echo Current dir: %CD%
echo.

:: Check JAR
if not exist "target\service-manager-1.0.0.jar" (
    echo [1/2] Building JAR...
    call mvn package -DskipTests
    if errorlevel 1 (
        echo Build failed!
        pause
        exit /b 1
    )
) else (
    echo [1/2] JAR exists, skip build
)

:: Run in foreground with console output
echo [2/2] Starting (foreground, Ctrl+C to stop)...
echo.
java -jar "target\service-manager-1.0.0.jar" 2>&1

echo.
echo App exited, code: %ERRORLEVEL%
pause
