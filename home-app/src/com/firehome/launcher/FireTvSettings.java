package com.firehome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Fire TV's own settings sections, in Amazon's order, each linked to the real Amazon screen.
 * The screens are found on the device at runtime (names differ between Fire OS versions), and a
 * section is only offered if this app is allowed to open it; otherwise its tile opens Fire TV's
 * main Settings, which is always reachable.
 */
final class FireTvSettings {

    static final class Section {
        final String title;
        final int icon;
        ComponentName screen; // null = open Fire TV's main Settings
        String action;
        boolean found;

        Section(String title, int icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";

    /**
     * title, icon, Amazon's screen, and the request that makes it show that section. Amazon's
     * screens pick their page from the request's action, so opening one without it shows the main page.
     */
    private static final Object[][] SECTIONS = {
            {"Network", Glyph.WIFI, ".tv.network.NetworkActivity", "android.settings.WIFI_SETTINGS"},
            {"Display & Sounds", Glyph.DISPLAY, ".tv.display_sounds.DisplayAndSoundsActivity",
                    "com.amazon.device.settings.action.DISPLAY_AND_SOUNDS"},
            {"Applications", Glyph.APPS, ".tv.applications.ApplicationsActivity",
                    "com.amazon.device.settings.action.APPLICATIONS"},
            {"Controllers & Bluetooth Devices", Glyph.CONTROLLER,
                    ".tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity",
                    "com.amazon.device.settings.action.CONTROLLERS"},
            {"Preferences", Glyph.SLIDERS, ".tv.preferences.PreferencesActivity",
                    "com.amazon.device.settings.action.PREFERENCES"},
            {"Accessibility", Glyph.ACCESS, ".tv.accessibility.AccessibilityActivity",
                    "com.amazon.device.settings.action.ACCESSIBILITY"},
            {"My Fire TV", Glyph.DEVICE, ".tv.device.DeviceActivity", "com.amazon.device.settings.action.DEVICE"},
            {"Account & Profile Settings", Glyph.PERSON, ".tv.my_account.MyAccountActivity",
                    "com.amazon.device.settings.action.MY_ACCOUNT"},
    };

    private static List<Section> cached;

    static synchronized List<Section> sections(Context c) {
        if (cached != null) return cached;
        PackageManager pm = c.getPackageManager();
        List<Section> out = new ArrayList<Section>();
        for (Object[] row : SECTIONS) {
            Section s = new Section((String) row[0], (Integer) row[1]);
            ComponentName cn = new ComponentName(SETTINGS_PKG, SETTINGS_PKG + row[2]);
            try {
                ActivityInfo ai = pm.getActivityInfo(cn, 0);
                s.found = true;
                if (canOpen(c, ai)) {
                    s.screen = cn;
                    s.action = (String) row[3];
                }
                android.util.Log.i(LocalShell.TAG, "settings " + row[0] + ": exported=" + ai.exported
                        + " permission=" + ai.permission + " -> " + (s.screen != null ? "direct" : "via Fire TV Settings"));
            } catch (Exception e) {
                android.util.Log.i(LocalShell.TAG, "settings " + row[0] + ": not on this Fire OS");
            }
            if (s.found) out.add(s);
        }
        cached = out;
        return out;
    }

    private static boolean canOpen(Context c, ActivityInfo ai) {
        if (!ai.exported || !ai.enabled) return false;
        return ai.permission == null
                || c.checkCallingOrSelfPermission(ai.permission) == PackageManager.PERMISSION_GRANTED;
    }

    static void open(Context c, Section s) {
        HomeRedirectService.settingsSeenAt = android.os.SystemClock.uptimeMillis();
        if (s.screen != null) {
            try {
                c.startActivity(new Intent(s.action).setComponent(s.screen)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception e) {
                android.util.Log.i(LocalShell.TAG, "couldn't open " + s.screen + ": " + e);
            }
        }
        SettingsActivity.openFireTvSettings(c, null, android.provider.Settings.ACTION_SETTINGS);
    }
}
