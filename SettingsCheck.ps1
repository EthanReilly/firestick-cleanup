# Lists every way into Fire TV's settings screens and whether other apps may use it, into settings-log.txt.
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $Here "platform-tools\adb.exe"
$Out = Join-Path $Here "settings-log.txt"
$ip = (Get-Content (Join-Path $Here "lastip.txt") | Select-Object -First 1).Trim()
$t = "$($ip):5555"
& $Adb connect $t | Out-Host
function Sh($c) { ((& $Adb -s $t shell $c 2>&1) | Out-String).Trim() }
$log = New-Object System.Collections.ArrayList
function Add($s) { [void]$log.Add($s) }
Write-Host "Checking Fire TV settings screens... (about 30 seconds)" -ForegroundColor Cyan

# Every intent action the settings apps answer to.
$actions = @()
foreach ($pkg in @("com.amazon.tv.settings.v2", "com.amazon.tv.launcher", "com.amazon.tv.devicecontrolsettings")) {
    $raw = Sh "dumpsys package $pkg"
    foreach ($m in [regex]::Matches($raw, 'Action: "([^"]+)"')) {
        $a = $m.Groups[1].Value
        if ($a -match 'settings|SETTINGS') { $actions += $a }
    }
}
$actions = $actions | Sort-Object -Unique
Add "==== $($actions.Count) settings actions"

foreach ($a in $actions) {
    $q = Sh "cmd package query-activities -a $a"
    $names = ([regex]::Matches($q, 'name=([\w\.\$]+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    $pkgs = ([regex]::Matches($q, 'packageName=([\w\.]+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    $exp = ([regex]::Matches($q, 'exported=(\w+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    $perm = ([regex]::Matches($q, 'permission=([\w\.]+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    Add ("{0}`n    -> {1} | {2} | exported={3} | permission={4}" -f $a, ($pkgs -join ","), ($names -join ","), ($exp -join ","), ($perm -join ","))
}

# Details of the main Amazon settings screens.
foreach ($c in @(".tv.network.NetworkActivity", ".tv.display_sounds.DisplayAndSoundsActivity",
                 ".tv.applications.ApplicationsActivity", ".tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity",
                 ".tv.accessibility.AccessibilityActivity", ".tv.device.DeviceActivity")) {
    $q = Sh "cmd package query-activities -n com.amazon.tv.settings.v2/$c"
    $exp = ([regex]::Matches($q, 'exported=(\w+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    $perm = ([regex]::Matches($q, 'permission=([\w\.]+)') | ForEach-Object { $_.Groups[1].Value }) | Select-Object -Unique
    Add "==== component $c -> exported=$($exp -join ',') permission=$($perm -join ',')"
}
Add "==== raw sample (Display & Sounds):"
Add (Sh "cmd package query-activities -n com.amazon.tv.settings.v2/.tv.display_sounds.DisplayAndSoundsActivity")

$log | Out-File $Out -Encoding utf8
Write-Host ""
Write-Host "Done - tell Claude it's finished." -ForegroundColor Green
