package com.firehome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed apps and the user's pinned "My apps" list. */
final class Apps {

    static final class App {
        String label, pkg;
        ComponentName component;
        Drawable banner, icon;
        int tint;
    }

    /** First-run picks, in order; the first package found for each entry is used. */
    private static final String[][] DEFAULTS = {
            {"com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.amazon.firetv.youtube.tv"},
            {"com.stremio.one"},
            {"com.amazon.firebat", "com.amazon.avod"},
            {"com.netflix.ninja"},
            {"com.disney.disneyplus"},
            {"uk.co.bbc.iplayer", "bbc.iplayer.android"},
            {"ie.rte.player", "com.rte.player"},
            {"com.spotify.tv.android"},
            {"com.plexapp.android"},
            {"org.xbmc.kodi"},
    };

    private static final List<String> NEVER_SHOW = Arrays.asList(
            "com.firehome.launcher", "com.amazon.tv.launcher", "com.amazon.firehomestarter");

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** Every launchable app on the device, sorted by name. */
    static List<App> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> byPkg = new HashMap<String, App>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (byPkg.containsKey(ai.packageName) || NEVER_SHOW.contains(ai.packageName)) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.component = new ComponentName(ai.packageName, ai.name);
                a.label = String.valueOf(ri.loadLabel(pm));
                try {
                    a.banner = ai.loadBanner(pm);
                    if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
                } catch (Exception ignored) {
                }
                try {
                    a.icon = ri.loadIcon(pm);
                } catch (Exception ignored) {
                }
                a.tint = averageColor(a.banner != null ? a.banner : a.icon);
                byPkg.put(a.pkg, a);
            }
        }
        List<App> list = new ArrayList<App>(byPkg.values());
        Collections.sort(list, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return list;
    }

    static List<String> pinned(Context c, List<App> all) {
        SharedPreferences p = prefs(c);
        String raw = p.getString("pinned", null);
        if (raw != null) {
            List<String> out = new ArrayList<String>();
            for (String s : raw.split(",")) if (s.length() > 0) out.add(s);
            return out;
        }
        // First run: pick the usual streaming apps that are installed.
        List<String> out = new ArrayList<String>();
        for (String[] choices : DEFAULTS) {
            for (String pkg : choices) {
                if (find(all, pkg) != null) {
                    out.add(pkg);
                    break;
                }
            }
        }
        if (out.isEmpty()) {
            for (int i = 0; i < Math.min(6, all.size()); i++) out.add(all.get(i).pkg);
        }
        savePinned(c, out);
        return out;
    }

    static void savePinned(Context c, List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String s : pkgs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs(c).edit().putString("pinned", sb.toString()).apply();
    }

    static App find(List<App> all, String pkg) {
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static void launch(Context c, App app) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(app.component);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
        } catch (Exception e) {
            PackageManager pm = c.getPackageManager();
            Intent li = pm.getLeanbackLaunchIntentForPackage(app.pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(app.pkg);
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(li);
            }
        }
    }

    static int averageColor(Drawable d) {
        if (d == null) return 0xFF2A3140;
        try {
            Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 8, 8);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                    n++;
                }
            }
            b.recycle();
            if (n == 0) return 0xFF2A3140;
            return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        } catch (Exception e) {
            return 0xFF2A3140;
        }
    }
}
