@echo off
rem ============================================================
rem  make-shortcut.bat - recreate desktop shortcut "??????"
rem  Target: launch.vbs (hidden elevate bridge -> start.bat)
rem  Keep this file ASCII-only. Chinese name built from codepoints:
rem  U+670D 52A1 7BA1 7406 9762 677F
rem ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$name = -join [char[]](0x670D,0x52A1,0x7BA1,0x7406,0x9762,0x677F);" ^
    "$ws = New-Object -ComObject WScript.Shell;" ^
    "$desktop = [Environment]::GetFolderPath('Desktop');" ^
    "$lnkPath = Join-Path $desktop ($name + '.lnk');" ^
    "$lnk = $ws.CreateShortcut($lnkPath);" ^
    "$lnk.TargetPath = Join-Path $env:SystemRoot 'System32\wscript.exe';" ^
    "$lnk.Arguments = 'F:\idea-workspase\service-manager\launch.vbs';" ^
    "$lnk.WorkingDirectory = 'F:\idea-workspase\service-manager';" ^
    "$lnk.IconLocation = (Join-Path $env:SystemRoot 'System32\shell32.dll') + ',13';" ^
    "$lnk.Description = 'Local service manager panel';" ^
    "$lnk.Save()"
if errorlevel 1 (
    echo [ERROR] shortcut create failed
    pause
    exit /b 1
)
echo [OK] desktop shortcut recreated.
pause
