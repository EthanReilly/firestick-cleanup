# Records what the Fire Stick does when Home and volume are pressed, into buttons-log.txt next to this script.
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $Here "platform-tools\adb.exe"
$Out = Join-Path $Here "buttons-log.txt"
$ip = (Get-Content (Join-Path $Here "lastip.txt") | Select-Object -First 1).Trim()
$t = "$($ip):5555"
& $Adb connect $t | Out-Host
function Sh($c) { ((& $Adb -s $t shell $c 2>&1) | Out-String) }

Write-Host ""
Write-Host "Getting ready..." -ForegroundColor Cyan
& $Adb -s $t logcat -c
Write-Host ""
Write-Host ">>> On the PHYSICAL remote: open an app (e.g. YouTube), press HOME, then press VOLUME UP and VOLUME DOWN." -ForegroundColor Yellow
Write-Host ">>> Then in Fire Remote on your phone: press HOME, then VOLUME UP." -ForegroundColor Yellow
Write-Host ">>> Recording for 45 seconds..." -ForegroundColor Yellow
for ($i = 45; $i -gt 0; $i--) { Write-Host -NoNewline "`r    $i seconds left  "; Start-Sleep -Seconds 1 }
Write-Host ""

@(
    "---- Home app ----",
    (Sh "dumpsys package com.firehome.launcher | grep versionName"),
    "---- accessibility ----",
    (Sh "settings get secure enabled_accessibility_services"),
    (Sh "dumpsys accessibility | head -20"),
    "---- HDMI-CEC ----",
    (Sh "settings list global | grep -iE 'hdmi|cec'"),
    "---- log ----",
    (& $Adb -s $t logcat -d -v time 2>&1 | Select-String -Pattern 'FireHome|ActivityManager: START|Displayed|start request|KeyPolicy|HOME_PRESSED|VOLUME|Volume|volume|devicecont|DCS|CEC|Cec|actix_web' | ForEach-Object { $_.Line })
) | Out-File $Out -Encoding utf8

Write-Host ""
Write-Host "Done - tell Claude it's finished." -ForegroundColor Green
