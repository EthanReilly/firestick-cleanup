# Fire Stick Clean-up

A one-click Windows tool that removes Amazon's ads, tracking and bloat from a Fire TV Stick and replaces the home screen with a simple, ad-free one.

Everything it changes is recorded, and one double-click undoes it all.

## What it does

- **Turns off about 55 Amazon packages:** home-screen ads and sponsored rows, viewing tracking, telemetry, remote experiments, update nag screens, Alexa, Amazon Music, Photos, Silk, Freevee and Games.
- **Keeps what matters:** Prime Video, the Appstore, Settings, the remote, HDMI/CEC and Wi-Fi. The script refuses to touch anything the Fire Stick needs to boot.
- **Blocks ads on the network** by setting the Fire Stick's private DNS to [AdGuard DNS](https://adguard-dns.io). This works in most apps, but not for YouTube's own video ads.
- **Installs an ad-free Home screen** (see below) and makes the Home button open it.

Packages are turned off with `pm disable-user`, not deleted, so nothing is lost and a factory reset also restores everything.

## The Home screen

- **Spotlight layout:** the selected app's name sits large across the top over a faint wash of its colour, and **My apps** (just the apps you choose) run in one row along the bottom. The last tile is a **+** for adding more. The clock and Settings button sit top right.
- Press **Menu** on an app to move it or remove it.
- **Settings** (the gear): laid out like Fire TV's own, with tiles for Network, Display & Sounds, Applications, Controllers & Bluetooth Devices, Notifications, Preferences, Accessibility, My Fire TV and more. Each opens the real Fire TV screen behind it. Below the tiles are this app's own options:
  - **Home screen:** tile size, app names, background, 12- or 24-hour clock.
  - **Switches:** ad-blocking on/off, whether the Home button opens this screen or Amazon's, and whether the gear opens this Settings page or Fire TV's own. Press Menu on the gear for the other one.
  - **About:** device, Fire OS version, IP address and free storage.

## How to use

You need a Windows PC on the same Wi-Fi as the Fire Stick.

1. On the Fire Stick, open **Settings → My Fire TV → Developer options** and turn on **ADB debugging**. If Developer options is missing, go to **My Fire TV → About** and press OK on the device name 7 times.
2. Download this repository (**Code → Download ZIP**) and unzip it.
3. Double-click **Run Fire Stick Cleanup.bat**. If Windows shows "Windows protected your PC", choose **More info → Run anyway**.
4. The script downloads Google's official `adb` tool (about 7 MB from dl.google.com) and finds the Fire Stick on your Wi-Fi.
5. When the TV asks **"Allow USB debugging?"**, tick **Always allow** and select **OK**.

It takes about a minute. Running it again is safe: it skips anything already done.

**To undo everything,** double-click **Undo Fire Stick Cleanup.bat**.

### Home button notes

- A real Fire TV remote's Home key goes through Fire OS, which blocks other apps from opening screens for about 5 seconds after Home is pressed.
- Fire OS 7.7 refuses ADB connections from the Fire Stick itself, so apps on the TV can't get round this wait. Instead, the Home app covers Amazon's home with a picture of your Home screen until the real one opens.
- [Fire Remote](https://github.com/EthanReilly/fire-remote), the companion phone app, asks the Home app to open directly (over your Wi-Fi, port 47100), which avoids that delay. If the Home app isn't installed, or its Home button setting is off, Fire Remote uses Amazon's Home instead.
- **Test Home Button.bat** records what the Fire Stick does when Home is pressed, which helps with troubleshooting.

## Files

| File | Purpose |
|---|---|
| `Run Fire Stick Cleanup.bat` | Runs the clean-up |
| `Undo Fire Stick Cleanup.bat` | Reverses every change |
| `Test Home Button.bat` | Records a Home button log for troubleshooting |
| `Test Buttons.bat` | Records Home and volume button logs for troubleshooting |
| `Diagnose.bat` | Checks the Home app, settings shortcuts and Fire Remote link |
| `FireStickCleanup.ps1` | The clean-up script |
| `FireHome.apk` | The ad-free Home screen app |
| `home-app/` | Source code for the Home screen app |

These files are created on your PC as the script runs and stay private: `changes.txt` (what was changed, used by Undo), `lastip.txt`, `diagnostics.txt` and `platform-tools/`.

## Building the Home app

```bash
sudo apt-get install aapt android-sdk-platform-23 apksigner zipalign dalvik-exchange openjdk-17-jdk python3-pil
cd home-app && ./build.sh
```

`build.sh` creates a signing key the first time it runs. Updates must be signed with the same key to install over an existing copy.

Tested on a Fire TV Stick (AFTSS) running Fire OS 7.7.1.5.

## Credits

Package research draws on the [dumbtv package audit](https://dumbtv.org/blocked) and community debloat lists. This project isn't affiliated with Amazon. Use it at your own risk.
