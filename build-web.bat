@echo off
rem ============================================================
rem  build-web.bat ? ??????????????
rem  ????????????????? mvn package ??
rem ============================================================
chcp 65001 >nul
setlocal

cd /d "%~dp0frontend"

echo [1/4] ????????????...
call npm install
if errorlevel 1 (
    echo ? npm install ?????? node ??
    pause
    exit /b 1
)

echo [2/4] ????...
call npm run build
if errorlevel 1 (
    echo ? ??????
    pause
    exit /b 1
)

echo [3/4] ? script ???? charset??? WebView ?????...
powershell -NoProfile -Command "$p='frontend\dist\index.html';$h=Get-Content $p -Raw -Encoding UTF8;$h=$h -replace '<script (?![^>]*charset)','<script charset=\"utf-8\" ';Set-Content $p -Value $h -Encoding UTF8 -NoNewline"
if errorlevel 1 (
    echo ? ?? charset ??
    pause
    exit /b 1
)

echo [4/4] ????? src\main\resources\web ...
robocopy dist "..\src\main\resources\web" /MIR /NFL /NDL /NJH | findstr /C:"??" /C:"Files" >nul
echo ????????:
echo   mvn clean package -DskipTests
pause
