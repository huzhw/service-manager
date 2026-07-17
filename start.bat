@echo off
cd /d "%~dp0"

:: Auto-elevate to admin if needed
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:: Check JAR
if not exist "target\service-manager-1.0.0.jar" (
    echo Building JAR...
    call mvn package -DskipTests -q
    if errorlevel 1 (
        echo Build failed! Check Maven and JDK.
        pause
        exit /b 1
    )
)

:: Launch in background
start "" javaw -jar "target\service-manager-1.0.0.jar"

echo Service Manager started. Check system tray icon.
echo If nothing appears, run start-debug.bat for error details.
