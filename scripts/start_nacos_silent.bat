@echo off
setlocal

set "target_dir=D:\tools\nacos\data"
set "startup_script=D:\tools\nacos\bin\startup.cmd"
set "JAVA_HOME=D:\tools\elasticsearch-8.11.3\elasticsearch-8.11.3\jdk"

REM 1. clean data dir
if exist "%target_dir%" (
    rmdir /s /q "%target_dir%" 2>nul
)

REM 2. start Nacos (no popup window, standalone mode)
call "%startup_script%" -m standalone < nul

endlocal
