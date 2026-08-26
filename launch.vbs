' ============================================================
'  launch.vbs - Service Manager hidden launcher
'  Silently elevates start.bat (runas) with a hidden window,
'  so no console windows flash on double-click.
'  Keep this file ASCII-only.
' ============================================================
Set sh = CreateObject("Shell.Application")
Set fso = CreateObject("Scripting.FileSystemObject")
d = fso.GetParentFolderName(WScript.ScriptFullName)
sh.ShellExecute """" & d & "\start.bat""", "", d, "runas", 0
