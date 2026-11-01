# Collects facts about the Home app, settings shortcuts and the Fire Remote link into diagnose-log.txt.
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $Here "platform-tools\adb.exe"
$Out = Join-Path $Here "diagnose-log.txt"
$ip = (Get-Content (Join-Path $Here "lastip.txt") | Select-Object -First 1).Trim()
$t = "$($ip):5555"
& $Adb connect $t | Out-Host
function Sh($c) { ((& $Adb -s $t shell $c 2>&1) | Out-String).Trim() }
$log = New-Object System.Collections.ArrayList
function Add($s) { [void]$log.Add($s) }

Write-Host "Checking... (about 20 seconds)" -ForegroundColor Cyan

Add "==== Fire OS: $(Sh 'getprop ro.build.version.name')"
Add "==== Home app: $(Sh 'dumpsys package com.firehome.launcher | grep versionName')"
Add "==== accessibility: $(Sh 'settings get secure enabled_accessibility_services')"

# Is the Home app listening for Fire Remote (port 47100 = hex B83C)?
Add "==== listening sockets with B83C (47100):"
Add (Sh "cat /proc/net/tcp /proc/net/tcp6 | grep -i ':B83C'")
Add "==== Home app process: $(Sh 'pidof com.firehome.launcher')"

# Talk to the Home app from this laptop, like Fire Remote does.
try {
    $c = New-Object System.Net.Sockets.TcpClient
    if ($c.ConnectAsync($ip, 47100).Wait(2000) -and $c.Connected) {
        $s = $c.GetStream(); $w = New-Object System.IO.StreamWriter($s); $w.AutoFlush = $true
        $r = New-Object System.IO.StreamReader($s); $s.ReadTimeout = 2000
        $w.WriteLine("PING"); Add "==== PING over Wi-Fi -> $($r.ReadLine())"
    } else { Add "==== PING over Wi-Fi -> could not connect to port 47100" }
    $c.Dispose()
} catch { Add "==== PING over Wi-Fi -> error: $($_.Exception.Message)" }

# Where do the settings shortcuts go on this Fire OS?
foreach ($a in @("android.settings.SETTINGS", "android.settings.WIFI_SETTINGS", "android.settings.DISPLAY_SETTINGS",
                 "android.settings.SOUND_SETTINGS", "android.settings.APPLICATION_SETTINGS",
                 "android.settings.MANAGE_APPLICATIONS_SETTINGS", "android.settings.BLUETOOTH_SETTINGS")) {
    Add "==== resolve $a -> $(Sh "cmd package resolve-activity --brief -a $a | tail -1")"
}
Add "==== Amazon settings activities:"
Add (Sh "dumpsys package com.amazon.tv.settings.v2 | grep -E 'com.amazon.tv.settings.v2/[^ ]+' -o | sort -u | head -60")
Add "==== Amazon settings intent actions:"
Add (Sh "dumpsys package com.amazon.tv.settings.v2 | grep -E 'Action: ' | sort -u | head -80")

Add "==== Home app log (since boot, FireHome tag):"
Add (Sh "logcat -d -b main -v time -s FireHome | tail -60")

# Try opening Settings the way the Home app does, and see what happens.
Sh "logcat -c" | Out-Null
Sh "am start -a android.settings.SETTINGS" | Out-Null
Start-Sleep -Seconds 3
Sh "am start -n com.firehome.launcher/.HomeActivity" | Out-Null
Start-Sleep -Seconds 1
Add "==== log while opening Settings:"
Add (Sh "logcat -d -v time | grep -E 'ActivityManager: START|Displayed|FireHome|start request' | head -40")


$log | Out-File $Out -Encoding utf8
Write-Host ""
Write-Host "Done - tell Claude it's finished." -ForegroundColor Green
