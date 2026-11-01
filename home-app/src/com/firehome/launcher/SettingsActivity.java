package com.firehome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * TV-style settings: page title on the left, a list of options on the right. Back goes up a level.
 * Toggles for ad-blocking and the Home button need WRITE_SECURE_SETTINGS, which the clean-up
 * script grants over ADB; without it those rows explain how to change them instead.
 */
public class SettingsActivity extends Activity {

    private static final String SERVICE = "com.firehome.launcher/com.firehome.launcher.HomeRedirectService";
    private static final String KEY_SERVICE = "com.firehome.launcher/com.firehome.launcher.HomeKeyService";
    private static final String ADGUARD = "dns.adguard-dns.com";

    private interface Page {
        String title();

        String subtitle();

        void build(List<Row> rows);
    }

    private static final class Row {
        String title;
        String value;
        boolean chevron;
        Runnable action;

        Row(String title, String value, boolean chevron, Runnable action) {
            this.title = title;
            this.value = value;
            this.chevron = chevron;
            this.action = action;
        }
    }

    private final List<Page> stack = new ArrayList<Page>();
    private final List<Integer> focusStack = new ArrayList<Integer>();
    private FrameLayout root;
    private TextView pageTitle, pageSubtitle;
    private LinearLayout list;
    private ScrollView listScroll;
    private LinearLayout split;
    private ScrollView mainScroll;
    private LinearLayout mainBox;
    private final List<View> mainTiles = new ArrayList<View>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackground(Look.background(this));
        split = new LinearLayout(this);
        root.addView(split, new FrameLayout.LayoutParams(-1, -1));

        // Main page: Fire TV's settings tiles, laid out like Amazon's, then this app's own options.
        mainScroll = new ScrollView(this);
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.setClipToPadding(false);
        mainScroll.setClipChildren(false);
        mainScroll.setPadding(dp(56), dp(40), dp(56), dp(48));
        mainBox = new LinearLayout(this);
        mainBox.setOrientation(LinearLayout.VERTICAL);
        mainBox.setClipChildren(false);
        mainScroll.addView(mainBox, new ViewGroup.LayoutParams(-1, -2));
        root.addView(mainScroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setPadding(dp(64), 0, dp(32), 0);
        Glyph gear = new Glyph(this, Glyph.GEAR, 0x99FFFFFF);
        left.addView(gear, new LinearLayout.LayoutParams(dp(72), dp(72)));
        pageTitle = Look.text(this, 38, Color.WHITE, "sans-serif-medium", Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(18);
        left.addView(pageTitle, tlp);
        pageSubtitle = Look.text(this, 15, 0xFF9AA3AF, "sans-serif", Typeface.NORMAL);
        pageSubtitle.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        left.addView(pageSubtitle, slp);
        split.addView(left, new LinearLayout.LayoutParams(0, -1, 0.4f));

        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setClipToPadding(false);
        listScroll.setClipChildren(false);
        listScroll.setPadding(dp(20), dp(56), dp(64), dp(56));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        listScroll.addView(list, new ViewGroup.LayoutParams(-1, -2));
        split.addView(listScroll, new LinearLayout.LayoutParams(0, -1, 0.6f));

        setContentView(root);
        push(main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        root.setBackground(Look.background(this));
        if (!stack.isEmpty()) show(currentFocusIndex());
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            int f = focusStack.remove(focusStack.size() - 1);
            show(f);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ page plumbing

    private void push(Page p) {
        if (!stack.isEmpty()) focusStack.add(currentFocusIndex());
        stack.add(p);
        show(0);
    }

    private int currentFocusIndex() {
        View f = getCurrentFocus();
        if (!stack.isEmpty() && stack.get(stack.size() - 1) == main) {
            int i = mainTiles.indexOf(f);
            return i < 0 ? 0 : i;
        }
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i) == f) return i;
        return 0;
    }

    private void refresh() {
        show(currentFocusIndex());
    }

    private void show(int focusIndex) {
        Page p = stack.get(stack.size() - 1);
        boolean isMain = p == main;
        split.setVisibility(isMain ? View.GONE : View.VISIBLE);
        mainScroll.setVisibility(isMain ? View.VISIBLE : View.GONE);
        if (isMain) {
            buildMain(focusIndex);
            return;
        }
        pageTitle.setText(p.title());
        pageSubtitle.setText(p.subtitle());
        List<Row> rows = new ArrayList<Row>();
        p.build(rows);
        list.removeAllViews();
        for (Row r : rows) list.addView(rowView(r), rowParams());
        if (list.getChildCount() > 0) {
            final View f = list.getChildAt(Math.min(focusIndex, list.getChildCount() - 1));
            f.post(new Runnable() {
                @Override
                public void run() {
                    f.requestFocus();
                }
            });
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.bottomMargin = dp(8);
        return lp;
    }

    // ------------------------------------------------------------------ main page (Amazon-style tiles)

    private void buildMain(int focusIndex) {
        mainBox.removeAllViews();
        mainTiles.clear();
        TextView title = Look.text(this, 34, Color.WHITE, "sans-serif-medium", Typeface.BOLD);
        title.setText("Settings");
        mainBox.addView(title);

        int cols = 4;
        int width = getResources().getDisplayMetrics().widthPixels - dp(112);
        int gap = dp(18);
        int tileW = (width - gap * (cols - 1)) / cols;
        int tileH = dp(132);

        List<View> fire = new ArrayList<View>();
        for (final FireTvSettings.Section sec : FireTvSettings.sections(this)) {
            fire.add(tile(sec.icon, sec.title, null, new Runnable() {
                public void run() {
                    FireTvSettings.open(SettingsActivity.this, sec);
                }
            }));
        }
        fire.add(tile(Glyph.GEAR, "All Fire TV Settings", null, new Runnable() {
            public void run() {
                openFireTvSettings(SettingsActivity.this, null, Settings.ACTION_SETTINGS);
            }
        }));
        addGrid(fire, cols, tileW, tileH, gap, dp(26));

        TextView label = Look.text(this, 14, 0xFF9AA3AF, "sans-serif-medium", Typeface.BOLD);
        label.setText("HOME APP");
        label.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dp(30);
        mainBox.addView(label, llp);

        List<View> mine = new ArrayList<View>();
        mine.add(tile(Glyph.HOME, "Home screen", null, open(homePage)));
        mine.add(tile(Glyph.SHIELD, "Ad-blocking", adBlockOn() ? "On" : "Off", new Runnable() {
            public void run() {
                toggleAdBlock();
            }
        }));
        mine.add(tile(Glyph.REMOTE, "Home button", homeButtonOn() ? "Opens this Home" : "Amazon Home", new Runnable() {
            public void run() {
                toggleHomeButton();
            }
        }));
        mine.add(tile(Glyph.INFO, "About", null, open(aboutPage)));
        addGrid(mine, cols, tileW, tileH, gap, dp(14));

        if (!mainTiles.isEmpty()) {
            final View f = mainTiles.get(Math.min(focusIndex, mainTiles.size() - 1));
            f.post(new Runnable() {
                public void run() {
                    f.requestFocus();
                }
            });
        }
    }

    private void addGrid(List<View> tiles, int cols, int w, int h, int gap, int top) {
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.topMargin = i == 0 ? top : gap;
                mainBox.addView(row, rlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i % cols != 0) lp.leftMargin = gap;
            row.addView(tiles.get(i), lp);
            mainTiles.add(tiles.get(i));
        }
    }

    /** A Fire TV-style settings tile: icon above its name (and an optional value). */
    private View tile(int icon, String name, String value, final Runnable action) {
        final LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        Look.noSystemFocusHighlight(t);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        final GradientDrawable bg = Look.rounded(this, 0x14FFFFFF, 14);
        t.setBackground(bg);
        final Glyph g = new Glyph(this, icon, Color.WHITE);
        t.addView(g, new LinearLayout.LayoutParams(dp(40), dp(40)));
        final TextView n = Look.text(this, 15, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(12);
        t.addView(n, nlp);
        final TextView v = Look.text(this, 13, 0xFFAEB6C2, "sans-serif", Typeface.NORMAL);
        if (value != null) {
            v.setText(value);
            v.setGravity(Gravity.CENTER);
            t.addView(v, new LinearLayout.LayoutParams(-1, -2));
        }
        t.setContentDescription(value == null ? name : name + ", " + value);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
                g.setColor(has ? 0xFF111111 : Color.WHITE);
                n.setTextColor(has ? 0xFF111111 : Color.WHITE);
                v.setTextColor(has ? 0xFF4A4F57 : 0xFFAEB6C2);
                view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start();
                if (has) {
                    View row = (View) view.getParent();
                    int y = mainScroll.getScrollY(), hh = mainScroll.getHeight();
                    if (row.getTop() < y) mainScroll.smoothScrollTo(0, row.getTop() - dp(16));
                    else if (row.getBottom() + dp(48) > y + hh) mainScroll.smoothScrollTo(0, row.getBottom() + dp(48) - hh);
                }
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                action.run();
            }
        });
        return t;
    }

    private View rowView(final Row r) {
        final LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(18), 0);
        row.setFocusable(r.action != null);
        row.setFocusableInTouchMode(r.action != null);
        Look.noSystemFocusHighlight(row);
        final GradientDrawable bg = Look.rounded(this, 0x14FFFFFF, 12);
        row.setBackground(bg);

        final TextView title = Look.text(this, 18, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);
        title.setText(r.title);
        title.setSingleLine(true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView value = Look.text(this, 16, 0xFFAEB6C2, "sans-serif", Typeface.NORMAL);
        value.setSingleLine(true);
        if (r.value != null) value.setText(r.value);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = dp(16);
        row.addView(value, vlp);
        final Glyph chev = new Glyph(this, Glyph.CHEVRON, 0x88FFFFFF);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(22), dp(22));
        clp.leftMargin = dp(6);
        row.addView(chev, clp);
        chev.setVisibility(r.chevron ? View.VISIBLE : View.INVISIBLE);

        if (r.action == null) { // info row
            row.setBackground(null);
            title.setTextColor(0xFFAEB6C2);
            value.setTextColor(Color.WHITE);
            return row;
        }
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
                title.setTextColor(has ? 0xFF111111 : Color.WHITE);
                value.setTextColor(has ? 0xFF4A4F57 : 0xFFAEB6C2);
                chev.setColor(has ? 0xFF4A4F57 : 0x88FFFFFF);
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(140).start();
                if (has) keepVisible(v);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.action.run();
            }
        });
        return row;
    }

    private void keepVisible(View v) {
        int y = listScroll.getScrollY(), h = listScroll.getHeight();
        int pad = dp(56);
        if (v.getTop() < y) listScroll.smoothScrollTo(0, v.getTop() - dp(8));
        else if (v.getBottom() + pad > y + h) listScroll.smoothScrollTo(0, v.getBottom() + pad - h + dp(8));
    }

    // ------------------------------------------------------------------ pages

    private final Page main = new Page() {
        public String title() {
            return "Settings";
        }

        public String subtitle() {
            return "Your Fire Stick, without the ads.";
        }

        public void build(List<Row> rows) {
            rows.add(new Row("Home screen", null, true, open(homePage)));
            rows.add(new Row("Network", localIp(), true, system(AMZ + "/.tv.network.NetworkActivity", Settings.ACTION_WIFI_SETTINGS)));
            rows.add(new Row("Display & Sounds", null, true,
                    system(AMZ + "/.tv.display_sounds.DisplayAndSoundsActivity", "android.settings.DISPLAY_SETTINGS")));
            rows.add(new Row("Applications", null, true,
                    system(AMZ + "/.tv.applications.ApplicationsActivity", Settings.ACTION_APPLICATION_SETTINGS)));
            rows.add(new Row("Remotes & Bluetooth", null, true, system(AMZ + "/.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity", "android.settings.CONTROLLERS_SETTINGS", Settings.ACTION_BLUETOOTH_SETTINGS)));
            rows.add(new Row("Ad-blocking", adBlockOn() ? "On" : "Off", false, new Runnable() {
                public void run() {
                    toggleAdBlock();
                }
            }));
            rows.add(new Row("Home button", homeButtonOn() ? "Opens this Home" : "Amazon Home", false, new Runnable() {
                public void run() {
                    toggleHomeButton();
                }
            }));
            rows.add(new Row("About", null, true, open(aboutPage)));
            rows.add(new Row("All Fire TV settings", null, true, system(null, Settings.ACTION_SETTINGS)));
        }
    };

    private final Page homePage = new Page() {
        public String title() {
            return "Home screen";
        }

        public String subtitle() {
            return "Choose which apps appear and how Home looks.";
        }

        public void build(List<Row> rows) {
            final android.content.SharedPreferences p = Apps.prefs(SettingsActivity.this);
            rows.add(new Row("My apps", null, true, open(myAppsPage)));
            final boolean fireTvGear = p.getBoolean("gear_firetv", false);
            rows.add(new Row("Settings button opens", fireTvGear ? "Fire TV Settings" : "This Settings page", false,
                    new Runnable() {
                        public void run() {
                            p.edit().putBoolean("gear_firetv", !fireTvGear).apply();
                            toast(!fireTvGear
                                    ? "The gear opens Fire TV's Settings. Press Menu on it for this page."
                                    : "The gear opens this page. Press Menu on it for Fire TV's Settings.");
                            refresh();
                        }
                    }));
            final int size = p.getInt("size", 1);
            rows.add(new Row("Tile size", Look.SIZE_NAMES[size], false, new Runnable() {
                public void run() {
                    p.edit().putInt("size", (size + 1) % Look.SIZE_NAMES.length).apply();
                    refresh();
                }
            }));
            final boolean names = p.getBoolean("names", false);
            rows.add(new Row("App names", names ? "Always" : "When selected", false, new Runnable() {
                public void run() {
                    p.edit().putBoolean("names", !names).apply();
                    refresh();
                }
            }));
            final int bgIndex = p.getInt("background", 0);
            rows.add(new Row("Background", Look.BACKGROUND_NAMES[bgIndex], false, new Runnable() {
                public void run() {
                    p.edit().putInt("background", (bgIndex + 1) % Look.BACKGROUND_NAMES.length).apply();
                    root.setBackground(Look.background(SettingsActivity.this));
                    refresh();
                }
            }));
            final boolean h24 = p.getBoolean("clock24",
                    android.text.format.DateFormat.is24HourFormat(SettingsActivity.this));
            rows.add(new Row("Clock", h24 ? "24-hour" : "12-hour", false, new Runnable() {
                public void run() {
                    p.edit().putBoolean("clock24", !h24).apply();
                    refresh();
                }
            }));
            rows.add(new Row("Reset Home screen", null, false, new Runnable() {
                public void run() {
                    confirm("Reset Home screen?", "Your app list and look go back to the defaults.", new Runnable() {
                        public void run() {
                            p.edit().clear().apply();
                            root.setBackground(Look.background(SettingsActivity.this));
                            refresh();
                            toast("Home screen reset.");
                        }
                    });
                }
            }));
        }
    };

    private final Page myAppsPage = new Page() {
        public String title() {
            return "My apps";
        }

        public String subtitle() {
            return "Select an app to move or remove it. Order here is the order on Home.";
        }

        public void build(List<Row> rows) {
            final List<Apps.App> all = Apps.installed(SettingsActivity.this);
            final List<String> pinned = Apps.pinned(SettingsActivity.this, all);
            rows.add(new Row("Add an app", null, true, new Runnable() {
                public void run() {
                    addApp(all, pinned);
                }
            }));
            for (int i = 0; i < pinned.size(); i++) {
                final int index = i;
                Apps.App a = Apps.find(all, pinned.get(i));
                if (a == null) continue;
                final String label = a.label;
                rows.add(new Row(label, String.valueOf(i + 1), false, new Runnable() {
                    public void run() {
                        editPinned(label, index, pinned);
                    }
                }));
            }
        }
    };

    private final Page aboutPage = new Page() {
        public String title() {
            return "About";
        }

        public String subtitle() {
            return "Details about this Fire Stick.";
        }

        public void build(List<Row> rows) {
            rows.add(new Row("Device", Build.MODEL, false, null));
            rows.add(new Row("Fire OS", fireOsVersion(), false, null));
            rows.add(new Row("IP address", localIp(), false, null));
            rows.add(new Row("Free storage", freeStorage(), false, null));
            rows.add(new Row("Ad-blocking DNS", adBlockOn() ? ADGUARD : "Off", false, null));
            String v = "1";
            try {
                v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            rows.add(new Row("Home version", v, false, null));
        }
    };

    // ------------------------------------------------------------------ actions

    private Runnable open(final Page p) {
        return new Runnable() {
            public void run() {
                push(p);
            }
        };
    }

    private static final String AMZ = "com.amazon.tv.settings.v2";

    /** Opens a Fire TV settings screen: an exact Amazon screen first, else the action, else Fire TV Settings. */
    private Runnable system(final String component, final String... actions) {
        return new Runnable() {
            public void run() {
                openFireTvSettings(SettingsActivity.this, component, actions);
            }
        };
    }

    static void openFireTvSettings(android.content.Context c, String component, String... actions) {
        HomeRedirectService.settingsSeenAt = android.os.SystemClock.uptimeMillis();
        PackageManager pm = c.getPackageManager();
        if (component != null) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .setComponent(android.content.ComponentName.unflattenFromString(component))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            } catch (Exception e) {
                android.util.Log.i(LocalShell.TAG, "couldn't open " + component + ": " + e);
            }
        }
        for (String a : actions) {
            Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(pm) != null) {
                try {
                    c.startActivity(i);
                    return;
                } catch (Exception e) {
                    android.util.Log.i(LocalShell.TAG, "couldn't open " + a + ": " + e);
                }
            }
        }
        try {
            c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.util.Log.i(LocalShell.TAG, "couldn't open Fire TV Settings: " + e);
        }
    }

    private void addApp(List<Apps.App> all, final List<String> pinned) {
        final List<Apps.App> choices = new ArrayList<Apps.App>();
        for (Apps.App a : all) if (!pinned.contains(a.pkg)) choices.add(a);
        if (choices.isEmpty()) {
            toast("All your apps are already on Home.");
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add an app")
                .setAdapter(new HomeActivity.AppListAdapter(this, choices), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        pinned.add(choices.get(which).pkg);
                        Apps.savePinned(SettingsActivity.this, pinned);
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editPinned(String label, final int index, final List<String> pinned) {
        final List<String> items = new ArrayList<String>();
        if (index > 0) items.add("Move up");
        if (index < pinned.size() - 1) items.add("Move down");
        items.add("Remove from Home");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        String c = items.get(which);
                        if (c.equals("Move up")) Collections.swap(pinned, index, index - 1);
                        else if (c.equals("Move down")) Collections.swap(pinned, index, index + 1);
                        else pinned.remove(index);
                        Apps.savePinned(SettingsActivity.this, pinned);
                        refresh();
                    }
                })
                .show();
    }

    private boolean canWriteSecure() {
        return checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    private boolean adBlockOn() {
        ContentResolver cr = getContentResolver();
        String mode = Settings.Global.getString(cr, "private_dns_mode");
        String host = Settings.Global.getString(cr, "private_dns_specifier");
        return "hostname".equals(mode) && host != null && host.contains("adguard");
    }

    private void toggleAdBlock() {
        if (!canWriteSecure()) {
            notAllowed();
            return;
        }
        ContentResolver cr = getContentResolver();
        try {
            if (adBlockOn()) {
                Settings.Global.putString(cr, "private_dns_mode", "off");
                toast("Ad-blocking off.");
            } else {
                Settings.Global.putString(cr, "private_dns_specifier", ADGUARD);
                Settings.Global.putString(cr, "private_dns_mode", "hostname");
                toast("Ad-blocking on.");
            }
        } catch (Exception e) {
            toast("Couldn't change ad-blocking.");
        }
        refresh();
    }

    private boolean homeButtonOn() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
        return s != null && s.contains(SERVICE);
    }

    private void toggleHomeButton() {
        if (!canWriteSecure()) {
            notAllowed();
            return;
        }
        ContentResolver cr = getContentResolver();
        String s = Settings.Secure.getString(cr, "enabled_accessibility_services");
        List<String> parts = new ArrayList<String>();
        if (s != null) {
            for (String x : s.split(":")) {
                if (x.length() > 0 && !x.equals(SERVICE) && !x.equals(KEY_SERVICE)) parts.add(x);
            }
        }
        boolean turnOn = !homeButtonOn();
        if (turnOn) {
            parts.add(KEY_SERVICE);
            parts.add(SERVICE);
        }
        StringBuilder sb = new StringBuilder();
        for (String x : parts) {
            if (sb.length() > 0) sb.append(':');
            sb.append(x);
        }
        try {
            Settings.Secure.putString(cr, "enabled_accessibility_services", sb.toString());
            Settings.Secure.putString(cr, "accessibility_enabled", parts.isEmpty() ? "0" : "1");
            toast(turnOn ? "Home button now opens this Home." : "Home button now opens Amazon's Home.");
        } catch (Exception e) {
            toast("Couldn't change the Home button.");
        }
        refresh();
    }

    private String instantStatus() {
        if (LocalShell.isReady()) return "On";
        if (LocalShell.isConnecting()) return "Connecting…";
        return LocalShell.isEnabled(this) ? "Reconnect" : "Set up";
    }

    /** Connects this app to the Fire Stick's ADB debugging so Home opens instantly and volume works. */
    private void setUpInstant() {
        if (LocalShell.isReady()) {
            toast("Instant Home & volume is on.");
            return;
        }
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Instant Home & volume")
                .setMessage("Makes the Home button on any remote open this Home straight away, and lets Fire Remote "
                        + "change the volume.\n\nYour TV will ask \"Allow USB debugging?\" for \"FireHome\". "
                        + "Tick \"Always allow from this computer\" and select OK.")
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        toast("Connecting…");
                        LocalShell.connect(SettingsActivity.this, new LocalShell.Callback() {
                            public void onPrompt() {
                            }

                            public void onConnected() {
                                ui.post(new Runnable() {
                                    public void run() {
                                        toast("Instant Home & volume is on.");
                                        refresh();
                                    }
                                });
                            }

                            public void onFailed(final String why) {
                                ui.post(new Runnable() {
                                    public void run() {
                                        toast("Couldn't connect: " + why);
                                        refresh();
                                    }
                                });
                            }
                        });
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void notAllowed() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Needs one more permission")
                .setMessage("Run the Fire Stick clean-up on your laptop once more and this switch will work.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirm(String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        yes.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ device info

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> a = ni.getInetAddresses();
                while (a.hasMoreElements()) {
                    InetAddress x = a.nextElement();
                    if (x instanceof Inet4Address && x.isSiteLocalAddress()) return x.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return "Not connected";
    }

    private static String fireOsVersion() {
        String v = getProp("ro.build.version.name");
        return v.length() > 0 ? v.replaceAll("\\s*\\(.*\\)", "") : "Android " + Build.VERSION.RELEASE;
    }

    private static String getProp(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String freeStorage() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            double free = s.getAvailableBytes() / 1e9, total = s.getTotalBytes() / 1e9;
            return String.format(java.util.Locale.UK, "%.1f GB of %.1f GB", free, total);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Look.dp(this, v);
    }
}
