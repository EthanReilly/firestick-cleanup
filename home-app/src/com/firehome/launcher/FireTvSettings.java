package com.firehome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
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

        Section(String title, int icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";

    /** title, icon, then the class-name fragments that identify Amazon's screen for it. */
    private static final Object[][] SECTIONS = {
            {"Network", Glyph.WIFI, new String[]{".tv.network.NetworkActivity", "network.Network"}},
            {"Display & Sounds", Glyph.DISPLAY, new String[]{".tv.display_sounds.DisplayAndSoundsActivity", "DisplayAndSounds"}},
            {"Applications", Glyph.APPS, new String[]{".tv.applications.ApplicationsActivity", "ApplicationsActivity"}},
            {"Live TV", Glyph.TV, new String[]{"livetv.", "LiveTv"}},
            {"Controllers & Bluetooth Devices", Glyph.CONTROLLER, new String[]{"ControllersAndBluetoothActivity"}},
            {"Notifications", Glyph.BELL, new String[]{"notifications.Notification", "NotificationsActivity"}},
            {"Equipment Control", Glyph.REMOTE, new String[]{"equipment", "Equipment", "devicecontrol"}},
            {"Preferences", Glyph.SLIDERS, new String[]{"preferences.Preferences", "PreferencesActivity"}},
            {"Accessibility", Glyph.ACCESS, new String[]{".tv.accessibility.AccessibilityActivity", "AccessibilityActivity"}},
            {"My Fire TV", Glyph.DEVICE, new String[]{".tv.device.DeviceActivity", "device.DeviceActivity"}},
            {"Account & Profile Settings", Glyph.PERSON, new String[]{"account.Account", "AccountActivity", "profiles.Profile"}},
    };

    private static List<Section> cached;

    static synchronized List<Section> sections(Context c) {
        if (cached != null) return cached;
        PackageManager pm = c.getPackageManager();
        List<ActivityInfo> activities = new ArrayList<ActivityInfo>();
        for (String pkg : new String[]{SETTINGS_PKG, "com.amazon.tv.devicecontrolsettings"}) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
                if (pi.activities != null) {
                    for (ActivityInfo ai : pi.activities) activities.add(ai);
                }
            } catch (Exception ignored) {
            }
        }
        List<Section> out = new ArrayList<Section>();
        for (Object[] row : SECTIONS) {
            Section s = new Section((String) row[0], (Integer) row[1]);
            ActivityInfo match = find(activities, (String[]) row[2]);
            if (match != null && canOpen(c, match)) s.screen = new ComponentName(match.packageName, match.name);
            // Equipment Control and Live TV only exist on some models; keep them only if found.
            boolean optional = row[0].equals("Equipment Control") || row[0].equals("Live TV")
                    || row[0].equals("Account & Profile Settings");
            if (match == null && optional) continue;
            out.add(s);
        }
        cached = out;
        return out;
    }

    private static ActivityInfo find(List<ActivityInfo> activities, String[] fragments) {
        for (String f : fragments) {
            for (ActivityInfo ai : activities) {
                String n = ai.name;
                if (n.endsWith("Receiver") || n.endsWith("Provider") || n.contains("Dummy")) continue;
                if (n.endsWith(f) || n.contains(f)) return ai;
            }
        }
        return null;
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
                c.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(s.screen)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception e) {
                android.util.Log.i(LocalShell.TAG, "couldn't open " + s.screen + ": " + e);
            }
        }
        SettingsActivity.openFireTvSettings(c, null, android.provider.Settings.ACTION_SETTINGS);
    }
}
