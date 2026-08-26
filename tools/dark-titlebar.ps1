# ============================================================
#  dark-titlebar.ps1 - apply Windows dark titlebar to the panel
#  window. Pure ASCII: title chars built from codepoints.
#  Title literal: "服务管理面板" = U+670D 52A1 7BA1 7406 9762 677F
# ============================================================
param()
Add-Type @"
using System;
using System.Runtime.InteropServices;
using System.Text;
public class DarkTB {
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc p, IntPtr l);
    [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder sb, int m);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr h, int a, ref int v, int s);
    public static IntPtr Find(string part) {
        IntPtr f = IntPtr.Zero;
        EnumWindows((h, l) => { var sb = new StringBuilder(256); GetWindowText(h, sb, 256);
            if (IsWindowVisible(h) && sb.ToString().Contains(part)) { f = h; return false; } return true; }, IntPtr.Zero);
        return f;
    }
    public static int SetDark(IntPtr h) {
        int v = 1;
        int r = DwmSetWindowAttribute(h, 20, ref v, 4);   // Win10 1809+/11
        if (r != 0) { r = DwmSetWindowAttribute(h, 19, ref v, 4); } // older build fallback
        return r;
    }
}
"@
$title = -join ([char]0x670D, [char]0x52A1, [char]0x7BA1, [char]0x7406, [char]0x9762, [char]0x677F)
$hwnd = [IntPtr]::Zero
for ($i = 0; $i -lt 30; $i++) {
    $hwnd = [DarkTB]::Find($title)
    if ($hwnd -ne [IntPtr]::Zero) { break }
    Start-Sleep -Milliseconds 500
}
if ($hwnd -eq [IntPtr]::Zero) { exit 1 }
$null = [DarkTB]::SetDark($hwnd)
exit 0
