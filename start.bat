@echo off
cd /d "%~dp0"

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

:: Launch in background (no console window)
start "" javaw -jar "target\service-manager-1.0.0.jar"

echo Service Manager started. Check system tray icon (bottom-right).
echo If nothing appears, run start-debug.bat for error details.
