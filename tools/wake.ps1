# ============================================================
#  wake.ps1 - probe & wake | start
#  Panel already running (TCP probe of single-instance lock port
#  19953): send SHOW so the live instance reopens its browser.
#  Not running: start start.bat (inherits configured admin rights,
#  no forced runas / no UAC prompt).
#  Intended to run hidden from launch.vbs.
# ============================================================
$port = 19953
$awake = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect("127.0.0.1", $port)
    $stream = $client.GetStream()
    $payload = [System.Text.Encoding]::UTF8.GetBytes("SHOW")
    $stream.Write($payload, 0, $payload.Length)
    $stream.Flush()
    $client.Close()
    $awake = $true
} catch {
    $awake = $false
}

if (-not $awake) {
    $dir = Split-Path (Split-Path $script:MyInvocation.MyCommand.Path -Parent) -Parent
    Start-Process -FilePath (Join-Path $dir "start.bat") -WorkingDirectory $dir -WindowStyle Hidden
}