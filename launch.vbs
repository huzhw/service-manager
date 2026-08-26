' ============================================================
'  launch.vbs - Service Manager quick launcher (no PowerShell)
'  Probes single-instance lock port 19953 via WinHTTP:
'    connected (panel running) -> silent exit; the running
'    instance reopens its browser (any-connection wake).
'    refused (panel down) -> start start.bat hidden, inheriting
'    the shortcut's admin rights.
'  Keep this file ASCII-only.
' ============================================================
Set http = CreateObject("MSXML2.ServerXMLHTTP.6.0")
On Error Resume Next
http.open "GET", "http://127.0.0.1:19953/", False
http.send
If Err.Number = 0 Then
    WScript.Quit 0
End If
On Error GoTo 0

Set sh = CreateObject("Shell.Application")
Set fso = CreateObject("Scripting.FileSystemObject")
d = fso.GetParentFolderName(WScript.ScriptFullName)
sh.ShellExecute """" & d & "\start.bat""", "", d, "", 0