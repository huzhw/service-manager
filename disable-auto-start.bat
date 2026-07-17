@echo off
cd /d "%~dp0"
echo ========================================
echo   Disable All Auto-Start for Services
echo   Then use Service Manager to control
echo ========================================
echo.

:: Windows Services -> Manual (demand) start
echo [1/6] Setting Windows services to Manual...
sc config MySQL80 start= demand 2>&1 | findstr /v "SUCCESS"
sc config redis-x64-5.0.14.1 start= demand 2>&1 | findstr /v "SUCCESS"
sc config OracleServiceORCL start= demand 2>&1 | findstr /v "SUCCESS"
sc config OracleOraDb11g_home1TNSListener start= demand 2>&1 | findstr /v "SUCCESS"
sc config DmServiceDMSERVER start= demand 2>&1 | findstr /v "SUCCESS"
sc config postgresql-x64-16 start= demand 2>&1 | findstr /v "SUCCESS"
echo   Done.

:: Startup folder shortcuts
echo [2/6] Opening Startup folder — remove ES and MinIO shortcuts if present...
start "" "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
echo   Check the opened folder and delete ES/MinIO shortcuts manually.

echo.
echo All auto-start disabled. Reboot and use start.bat to launch Service Manager.
pause
