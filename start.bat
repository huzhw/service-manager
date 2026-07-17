@echo off
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)
cd /d "%~dp0"
set JAVA_HOME=D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk
start "" "%JAVA_HOME%\bin\javaw" --module-path "target\lib" --add-modules javafx.controls,javafx.media,javafx.web,javafx.swing -jar "target\service-manager-2.0.0.jar"
