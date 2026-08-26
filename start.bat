@echo off
rem ============================================================
rem  Service Manager launcher (self-elevate, ASCII-only, logged)
rem  Log: work\start.log   NOTE: keep this file ASCII-only!
rem ============================================================
net session >nul 2>&1
if %errorlevel% neq 0 goto ELEVATE
goto BODY

:ELEVATE
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs -WindowStyle Hidden"
exit /b

:BODY
cd /d "%~dp0"
set JAVA_HOME=D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk
if not exist work mkdir work
set LOGFILE=work\start.log
echo [%date% %time%] ==== launch begin ====>> "%LOGFILE%"

if exist "%JAVA_HOME%\bin\javaw.exe" goto CHK_JFX
echo [%date% %time%] ERROR: javaw.exe not found under JAVA_HOME >> "%LOGFILE%"
echo [ERROR] javaw.exe not found: %JAVA_HOME%\bin\javaw.exe
pause
exit /b 1

:CHK_JFX
dir /b "target\lib\javafx-controls*" >nul 2>&1
if errorlevel 1 goto ERR_JFX
if exist "target\service-manager-2.0.0.jar" goto CHK_OK
echo [%date% %time%] ERROR: main jar missing >> "%LOGFILE%"
echo [ERROR] target\service-manager-2.0.0.jar not found. Run: mvn package
pause
exit /b 1

:ERR_JFX
echo [%date% %time%] ERROR: javafx runtime lib missing in target\lib >> "%LOGFILE%"
echo [ERROR] JavaFX runtime jars missing under target\lib. Run: mvn package
pause
exit /b 1

:CHK_OK
if exist "work\jfx-cache" goto LAUNCH
mkdir "work\jfx-cache"

:LAUNCH
echo [%date% %time%] preflight ok, starting javaw >> "%LOGFILE%"
start "" "%JAVA_HOME%\bin\javaw" -Djavafx.cachedir="%~dp0work\jfx-cache" --module-path "target\lib" --add-modules javafx.controls,javafx.media,javafx.web,javafx.swing -jar "target\service-manager-2.0.0.jar"
ping -n 5 127.0.0.1 >nul
tasklist /FI "IMAGENAME eq javaw.exe" 2>nul | find /I "javaw.exe" >nul
if errorlevel 1 goto ERR_DEAD
echo [%date% %time%] process alive, launch OK >> "%LOGFILE%"
if exist "tools\dark-titlebar.ps1" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "tools\dark-titlebar.ps1"
exit /b 0

:ERR_DEAD
echo [%date% %time%] ERROR: javaw exited within 4s after start >> "%LOGFILE%"
echo [ERROR] process exited right after launch. See logs\service-manager.log
pause
exit /b 1
