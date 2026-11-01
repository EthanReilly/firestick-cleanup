<#
  Fire Stick clean-up
  - Turns off Amazon bloat, ads, tracking and Alexa (keeps Prime Video and the Appstore)
  - Turns on ad-blocking DNS (AdGuard)
  - Installs the ad-free "Home" app and makes the Home button open it

  Everything changed is recorded in changes.txt next to this script.
  Undo everything:   powershell -ExecutionPolicy Bypass -File FireStickCleanup.ps1 -Restore
#>
param(
    [string]$Ip = "",
    [switch]$Restore
)

$ErrorActionPreference = "Continue"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $Here "platform-tools\adb.exe"
$Log = Join-Path $Here "changes.txt"
$HomeApk = Join-Path $Here "FireHome.apk"
$IpFile = Join-Path $Here "lastip.txt"
$HomePkg = "com.firehome.launcher"
$HomeService = "com.firehome.launcher/com.firehome.launcher.HomeRedirectService"
$KeyService = "com.firehome.launcher/com.firehome.launcher.HomeKeyService"
$Diag = Join-Path $Here "diagnostics.txt"

function Say($msg, $color = "Gray") { Write-Host $msg -ForegroundColor $color }
function Step($msg) { Write-Host ""; Write-Host "== $msg" -ForegroundColor Cyan }

# ---------------------------------------------------------------- what gets turned off
# Sources: dumbtv.org package audit + XDA/AFTVnews lists. Prime Video and Appstore are kept.
$Bloat = @(
    # Ads, promos, shopping
    "com.amazon.hybridadidservice", "com.amazon.privacypassservice", "com.amazon.shoptv.client",
    "com.amazon.shoptv.firetv.client", "com.amazon.sneakpeek", "com.amazon.media.recommendations",
    "com.amazon.systemnotices", "com.amazon.tv.website_launcher", "com.amazon.tahoe",
    # Watching what you watch
    "com.amazon.tv.acr", "com.amazon.hedwig",
    # Telemetry and logging
    "com.amazon.tv.fw.metrics", "com.amazon.wirelessmetrics.service", "com.amazon.perfc",
    "com.amazon.perfcollection", "com.amazon.dp.logger", "com.amazon.logan",
    "com.amazon.neo.minerva.logcat", "com.amazon.ceviche", "com.amazon.connectivitydiag",
    "com.amazon.tigris", "com.amazon.tv.developer.dataservice", "com.amazon.spiderpork",
    # Remote experiments
    "com.amazon.ale", "com.amazon.katoch", "com.amazon.device_remote_config_sync_service",
    # Update nag screens
    "com.amazon.tv.forcedotaupdater.v2", "com.amazon.tv.easyupgrade",
    # Demos and tutorials
    "com.amazon.tv.democontent.provider", "com.amazon.tmm.tutorial", "com.amazon.tv.releasenotes",
    # Alexa (voice button stops working)
    "com.amazon.aca", "com.amazon.alexa.datastore.app", "com.amazon.alexadirectivebrokerservice",
    "com.amazon.alexamediaplayer.runtime.ftv", "com.amazon.alexa.externalmediaplayer.fireos",
    "com.amazon.tv.alexaalerts", "com.amazon.tv.alexanotifications", "com.amazon.vizzini.ftvcds",
    "com.amazon.firetv.androidskillconnectioncapability", "com.amazon.audiohome",
    "com.amazon.rtcsessioncontroller", "com.amazon.smarthomemapviewapp",
    "com.amazon.spotify.mediabrowserservice", "com.amazon.wha.mediabrowserservice", "com.amazon.aria",
    "com.amazon.whad", "com.amazon.whasettings",
    # Amazon apps you chose not to keep
    "com.amazon.bueller.music", "com.amazon.bueller.photos", "com.amazon.cloud9",
    "com.amazon.imdb.tv.android.app", "com.imdb.livingroom.firetv", "com.amazon.gamehub",
    "com.amazon.minitv.android.app"
)

# Never touched: needed for the Fire Stick to boot, the remote, the Appstore, Prime Video,
# Settings, the phone remote app, HDMI/CEC and Wi-Fi.
$Protected = @(
    "com.amazon.tv.launcher", "com.amazon.firehomestarter", "com.amazon.tv.settings.v2",
    "com.amazon.tv.settings.core", "com.amazon.tv.inputpreference.service", "com.amazon.tv.routing",
    "com.amazon.tv.devicecontrol", "com.amazon.tv.devicecontrolsettings", "com.amazon.tv.keypolicymanager",
    "com.amazon.fireinputdevices", "com.amazon.autopairservice", "com.amazon.device.bluetoothpa",
    "com.amazon.device.blepa", "com.amazon.whisperjoin.middleware.np", "com.amazon.whisperplay.service.install",
    "com.amazon.tv.oobe", "com.amazon.tv.ooberesource", "com.amazon.webview.chromium", "com.amazon.venezia",
    "com.amazon.firebat", "com.amazon.avod", "com.amazon.amazonvideo.livingroom", "com.amazon.device.messaging",
    "com.amazon.vizzini", "com.amazon.storm.lightning.services", "com.amazon.storm.lightning",
    "com.android.systemui", "com.android.packageinstaller", "com.android.permissioncontroller",
    "com.amazon.device.software.ota", "com.amazon.tv.ime", "com.android.providers.settings"
)

# ---------------------------------------------------------------- adb
function Get-Adb {
    if (Test-Path $Adb) { return }
    Step "Downloading Google's Android platform tools (adb)"
    $zip = Join-Path $Here "platform-tools.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath $Here -Force
    Remove-Item $zip
    if (-not (Test-Path $Adb)) { throw "Couldn't set up adb." }
    Say "adb ready." Green
}

$script:Target = ""
function Sh($cmd) { ((& $Adb -s $script:Target shell $cmd 2>&1) | Out-String).Trim() }

function Get-HomeNetworks {
    # The real network is the one with a default gateway (internet). Skip VirtualBox, Hyper-V,
    # VMware, WSL and VPN adapters, which have their own private addresses.
    $virtual = 'VirtualBox|Hyper-V|vEthernet|VMware|WSL|Loopback|TAP|Tunnel|VPN|Bluetooth|Npcap|ZeroTier|Tailscale'
    $nets = @()
    foreach ($cfg in (Get-NetIPConfiguration -ErrorAction SilentlyContinue)) {
        if (-not $cfg.IPv4DefaultGateway) { continue }
        if ($cfg.NetAdapter.Status -ne 'Up') { continue }
        if (($cfg.InterfaceDescription + ' ' + $cfg.InterfaceAlias) -match $virtual) { continue }
        foreach ($a in $cfg.IPv4Address) {
            if ($a.IPAddress -match '^(10|172\.(1[6-9]|2\d|3[01])|192\.168)\.') {
                $nets += [pscustomobject]@{ Ip = $a.IPAddress; Name = $cfg.InterfaceAlias }
            }
        }
    }
    return $nets
}

function Scan-Subnet($prefix) {
    $tries = @()
    foreach ($i in 1..254) {
        $c = New-Object System.Net.Sockets.TcpClient
        $tries += [pscustomobject]@{ Ip = "$prefix.$i"; Client = $c; Task = $c.ConnectAsync("$prefix.$i", 5555) }
    }
    Start-Sleep -Milliseconds 2500
    $found = @()
    foreach ($t in $tries) {
        if ($t.Task.Status -eq 'RanToCompletion' -and $t.Client.Connected) { $found += $t.Ip }
        $t.Client.Dispose()
    }
    return $found
}

function Find-FireStick {
    Step "Looking for your Fire Stick on the Wi-Fi"
    $found = @()
    $nets = Get-HomeNetworks
    if (-not $nets) { Say "Couldn't work out which network is your Wi-Fi." DarkYellow }
    foreach ($n in $nets) {
        $prefix = ($n.Ip -split '\.')[0..2] -join '.'
        Say "Scanning $prefix.1 - $prefix.254 on '$($n.Name)' ..."
        $found += Scan-Subnet $prefix
    }
    if ($found.Count -eq 0) {
        Write-Host ""
        Say "Couldn't find it automatically." Yellow
        Say "Find the Fire Stick's IP address in Fire Remote on your phone (it's shown at the top once connected)," Yellow
        Say "or on the TV under Settings > My Fire TV > About > Network." Yellow
        $typed = (Read-Host "Type the Fire Stick's IP address and press Enter").Trim()
        if (-not $typed) { throw "No IP address entered." }
        $typed = $typed -replace ':5555$', ''
        $probe = New-Object System.Net.Sockets.TcpClient
        try {
            $ok = $probe.ConnectAsync($typed, 5555).Wait(3000) -and $probe.Connected
        } catch { $ok = $false }
        $probe.Dispose()
        if (-not $ok) {
            throw "$typed isn't accepting ADB connections. On the TV check Settings > My Fire TV > Developer options > ADB debugging is ON (turn it off and on again), and that this laptop is on the same Wi-Fi."
        }
        return $typed
    }
    if ($found.Count -gt 1) {
        Say ("Found more than one device: " + ($found -join ", ")) Yellow
        Say "Using $($found[0]). To pick another, run with -Ip <address>." Yellow
    }
    return $found[0]
}

function Test-Adb($addr) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $ok = $c.ConnectAsync($addr, 5555).Wait(2000) -and $c.Connected } catch { $ok = $false }
    $c.Dispose()
    return $ok
}

function Connect-FireStick {
    if (-not $Ip -and (Test-Path $IpFile)) {
        $saved = (Get-Content $IpFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($saved -and (Test-Adb $saved.Trim())) { $script:Ip = $saved.Trim() }
    }
    if (-not $Ip) { $script:Ip = Find-FireStick }
    Set-Content $IpFile $script:Ip -Encoding ascii
    $script:Target = "$($script:Ip):5555"
    Step "Connecting to $($script:Target)"
    & $Adb start-server *> $null
    & $Adb connect $script:Target | Out-Host
    $deadline = (Get-Date).AddSeconds(120)
    $told = $false
    while ((Get-Date) -lt $deadline) {
        $state = (& $Adb -s $script:Target get-state 2>&1 | Out-String).Trim()
        if ($state -eq "device") { Say "Connected." Green; return }
        if (-not $told) {
            Say ">>> On the TV: tick 'Always allow from this computer' and select OK (use Fire Remote's touchpad)." Yellow
            $told = $true
        }
        Start-Sleep -Seconds 2
        if ($state -match "offline|not found|no devices") { & $Adb connect $script:Target *> $null }
    }
    throw "Timed out waiting for you to allow the connection on the TV."
}

# ---------------------------------------------------------------- restore
if ($Restore) {
    Get-Adb
    Connect-FireStick
    Step "Undoing changes"
    if (Test-Path $Log) {
        foreach ($line in Get-Content $Log) {
            $p = $line -split ' '
            switch ($p[0]) {
                "disabled"    { Say "  re-enabling $($p[1])"; Sh "pm enable $($p[1])" | Out-Null }
                "uninstalled" { Say "  restoring $($p[1])"; Sh "cmd package install-existing $($p[1])" | Out-Null }
            }
        }
    }
    Sh "settings put global private_dns_mode off" | Out-Null
    $acc = Sh "settings get secure enabled_accessibility_services"
    $rest = ($acc -split ':' | Where-Object { $_ -and $_ -ne $HomeService -and $_ -ne $KeyService -and $_ -ne "null" }) -join ':'
    if ($rest) { Sh "settings put secure enabled_accessibility_services $rest" | Out-Null }
    else { Sh "settings delete secure enabled_accessibility_services" | Out-Null }
    & $Adb -s $script:Target uninstall $HomePkg 2>&1 | Out-Null
    Rename-Item $Log ("changes-undone-" + (Get-Date -Format yyyyMMdd-HHmm) + ".txt") -ErrorAction SilentlyContinue
    Say ""
    Say "Done - the Fire Stick is back to how it was. Restart it to finish." Green
    exit
}

# ---------------------------------------------------------------- clean-up
try {
    Get-Adb
    Connect-FireStick

    $sdk = [int](Sh "getprop ro.build.version.sdk")
    $model = Sh "getprop ro.product.model"
    $fos = Sh "getprop ro.build.version.name"
    Step "Fire Stick: $model  ($fos, Android API $sdk)"

    $installed = @{}
    foreach ($l in (Sh "pm list packages") -split "`n") {
        $n = $l.Trim() -replace '^package:', ''
        if ($n) { $installed[$n] = $true }
    }
    $already = @{}
    foreach ($l in (Sh "pm list packages -d") -split "`n") {
        $n = $l.Trim() -replace '^package:', ''
        if ($n) { $already[$n] = $true }
    }
    if (-not (Test-Path $Log)) { "" | Out-File $Log -Encoding ascii }
    $done = @{}
    foreach ($l in Get-Content $Log) { if ($l) { $done[($l -split ' ')[1]] = $true } }

    Step "Turning off bloat, ads, tracking and Alexa"
    $off = 0; $skipped = 0; $failed = @()
    foreach ($pkg in $Bloat) {
        if ($Protected -contains $pkg) { continue }
        if (-not $installed.ContainsKey($pkg)) { continue }
        if ($already.ContainsKey($pkg) -or $done.ContainsKey($pkg)) { $skipped++; continue }
        $out = Sh "pm disable-user --user 0 $pkg"
        if ($out -match "disabled-user") {
            Add-Content $Log "disabled $pkg"; $off++; Say "  off  $pkg" DarkGray
        } elseif ($sdk -ge 28) {
            $out2 = Sh "pm uninstall -k --user 0 $pkg"
            if ($out2 -match "Success") { Add-Content $Log "uninstalled $pkg"; $off++; Say "  off  $pkg (removed for this user)" DarkGray }
            else { $failed += $pkg }
        } else { $failed += $pkg }
    }
    Say "$off turned off" Green
    if ($skipped) { Say "$skipped were already off" }
    if ($failed.Count) { Say ("Fire OS wouldn't let these be turned off (that's OK): " + ($failed -join ", ")) DarkYellow }

    Step "Turning on ad-blocking DNS"
    if ($sdk -ge 28) {
        Sh "settings put global private_dns_mode hostname" | Out-Null
        Sh "settings put global private_dns_specifier dns.adguard-dns.com" | Out-Null
        Add-Content $Log "dns adguard"
        Say "Ad-blocking DNS on (AdGuard)." Green
    } else {
        Say "This Fire OS version is too old for private DNS; skipped." DarkYellow
    }

    Step "Installing the new Home screen"
    $res = (& $Adb -s $script:Target install -r "$HomeApk" 2>&1 | Out-String).Trim()
    if ($res -notmatch "Success") { throw "Installing Home failed: $res" }
    Say "Installed." Green

    Sh "pm grant $HomePkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null
    $perm = Sh "dumpsys package $HomePkg | grep WRITE_SECURE_SETTINGS"
    if ($perm -match "granted=true") { Say "Settings page can change ad-blocking and the Home button." Green }

    Step "Making the Home button open it"
    $acc = Sh "settings get secure enabled_accessibility_services"
    $others = ($acc -split ':' | Where-Object { $_ -and $_ -ne "null" -and $_ -ne $HomeService -and $_ -ne $KeyService }) -join ':'
    # Switch off and on again so Fire OS loads the updated services.
    if ($others) { Sh "settings put secure enabled_accessibility_services $others" | Out-Null }
    else { Sh "settings delete secure enabled_accessibility_services" | Out-Null }
    Start-Sleep -Milliseconds 1000
    $new = "$KeyService`:$HomeService"
    if ($others) { $new = "$others`:$new" }
    Sh "settings put secure enabled_accessibility_services $new" | Out-Null
    Sh "settings put secure accessibility_enabled 1" | Out-Null
    Add-Content $Log "accessibility $HomeService"
    Start-Sleep -Seconds 3

    $dump = Sh "dumpsys accessibility"
    $enabledNow = Sh "settings get secure enabled_accessibility_services"
    $keyOk = $enabledNow -match "HomeKeyService"
    $redirOk = $enabledNow -match "HomeRedirectService"
    $boundKey = $dump -match "label=Home button"
    $boundRedir = $dump -match "label=Home,"
    if ($redirOk) { Say "Home button redirect: on" Green } else { Say "Home button redirect: Fire OS switched it OFF" Red }
    if ($keyOk) { Say "Instant Home button (no flash): on" Green } else { Say "Instant Home button: Fire OS switched it OFF (you'll see a brief flash)" DarkYellow }

    # Can the Home app reach ADB on the Fire Stick itself? (needed for Instant Home & volume)
    $probeSrc = Join-Path $Here "adbprobe.sh"
    $probe = ""
    if (Test-Path $probeSrc) {
        & $Adb -s $script:Target push $probeSrc /data/local/tmp/adbprobe.sh 2>&1 | Out-Null
        $probe = Sh "sh /data/local/tmp/adbprobe.sh"
    }
    Start-Sleep -Seconds 4 # let the Home app try its instant link and log the result

    # Save details for troubleshooting.
    @(
        "Fire OS: $fos  Model: $model  API: $sdk",
        "enabled_accessibility_services = $enabledNow",
        "accessibility_enabled = " + (Sh "settings get secure accessibility_enabled"),
        "Home package: " + (Sh "dumpsys package $HomePkg | grep -E 'versionName|WRITE_SECURE'"),
        "---- ADB self-connection probe ----", $probe,
        "---- dumpsys accessibility ----", $dump,
        "---- recent log ----", (Sh "logcat -d -t 600 | grep -iE 'accessib|firehome|HomeKey|HomeRedirect'")
    ) | Out-File $Diag -Encoding utf8

    Sh "am start -n $HomePkg/.HomeActivity" | Out-Null
    Write-Host ""
    Say ">>> If the TV asks 'Allow USB debugging?' for FireHome, tick 'Always allow' and select OK." Yellow
    Say "    That turns on Instant Home & volume (Home opens straight away, Fire Remote volume works)." Yellow

    Write-Host ""
    Say "All done! Your TV should now show the new Home screen." Green
    Say "Press Home on Fire Remote to try it. On the Home screen, press Menu on any app to move or hide it."
    Say "To undo everything later: powershell -ExecutionPolicy Bypass -File `"$($MyInvocation.MyCommand.Path)`" -Restore"
}
catch {
    Write-Host ""
    Say "Stopped: $($_.Exception.Message)" Red
    exit 1
}
