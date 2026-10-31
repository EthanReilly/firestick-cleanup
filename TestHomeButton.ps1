# Records what the Fire Stick does when Home is pressed, into homebutton-log.txt next to this script.
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $Here "platform-tools\adb.exe"
$Out = Join-Path $Here "homebutton-log.txt"
$ip = (Get-Content (Join-Path $Here "lastip.txt") | Select-Object -First 1).Trim()
$t = "$($ip):5555"
& $Adb connect $t | Out-Host
function Sh($c) { ((& $Adb -s $t shell $c 2>&1) | Out-String) }

Write-Host ""
Write-Host "Getting ready..." -ForegroundColor Cyan
Sh "am start -n com.firehome.launcher/.HomeActivity" | Out-Null
Start-Sleep -Seconds 2
& $Adb -s $t logcat -c
Write-Host ""
Write-Host ">>> Open any app on the TV (for example YouTube), then press HOME on Fire Remote." -ForegroundColor Yellow
Write-Host ">>> Recording for 25 seconds..." -ForegroundColor Yellow
for ($i = 25; $i -gt 0; $i--) { Write-Host -NoNewline "`r    $i seconds left  "; Start-Sleep -Seconds 1 }
Write-Host ""

@(
    "---- focused activity after test ----",
    (Sh "dumpsys activity activities | grep -E 'mResumedActivity|mFocusedActivity|realActivity' | head -20"),
    "---- accessibility ----",
    (Sh "dumpsys accessibility | head -30"),
    "---- log ----",
    (& $Adb -s $t logcat -d -v time 2>&1 | Select-String -Pattern 'ActivityManager|ActivityTaskManager|firehome|tv.launcher|firehomestarter|Accessib|KeyEvent|lightning|Lightning|WindowManager.*Home|HOME' | ForEach-Object { $_.Line })
) | Out-File $Out -Encoding utf8

Write-Host ""
Write-Host "Done - tell Claude it's finished." -ForegroundColor Green
