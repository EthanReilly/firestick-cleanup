package com.firehome.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Simple ad-free home screen: a clock, Settings, and the user's own apps with a "+" tile to add more. */
public class HomeActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private TextView clock, date, hint;
    private View settingsButton;

    private List<Apps.App> all = new ArrayList<Apps.App>();
    private List<String> pinned = new ArrayList<String>();
    private String focusPkg;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            load();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            boolean h24 = Apps.prefs(HomeActivity.this).getBoolean("clock24",
                    android.text.format.DateFormat.is24HourFormat(HomeActivity.this));
            clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
            ui.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeRedirectService r = HomeRedirectService.instance;
        if (r != null) {
            final HomeRedirectService rr = r;
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    rr.hideCover();
                }
            }, 150);
        }
        root.setBackgroundColor(Look.baseColor(this)); // settings may have changed
        ui.removeCallbacks(tick);
        tick.run();
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        takeSnapshot();
    }

    /** Keeps a full-size picture of this screen for the instant cover shown after a Home press. */
    private void takeSnapshot() {
        try {
            int w = root.getWidth(), h = root.getHeight();
            if (w == 0 || h == 0) return;
            android.graphics.Bitmap old = HomeRedirectService.snapshot;
            android.graphics.Bitmap b = old != null && !old.isRecycled() && old.getWidth() == w && old.getHeight() == h
                    ? old : android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            root.draw(c);
            HomeRedirectService.snapshot = b;
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packagesChanged); } catch (Exception ignored) { }
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back does nothing.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        rowScroll.smoothScrollTo(0, 0);
        View first = tileAt(0);
        if (first != null) first.requestFocus();
    }

    // ------------------------------------------------------------------ layout (Spotlight)

    private FrameLayout hero;
    private View heroColor;
    private TextView heroTitle, heroHint;
    private HorizontalScrollView rowScroll;
    private LinearLayout row;

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Look.baseColor(this));
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int heroH = (int) (screenH * 0.58f);

        // Spotlight: the selected app fills the top of the screen.
        hero = new FrameLayout(this);
        heroColor = new View(this);
        hero.addView(heroColor, new FrameLayout.LayoutParams(-1, -1));
        root.addView(hero, new FrameLayout.LayoutParams(-1, heroH));

        // Clock, date and Settings (top right).
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);
        times.setGravity(Gravity.END);
        clock = Look.text(this, 30, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);

        date = Look.text(this, 14, 0xFFAEB6C2, "sans-serif", Typeface.NORMAL);

        times.addView(clock);
        times.addView(date);
        top.addView(times);
        settingsButton = settingsButton();
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(58), dp(58));
        glp.leftMargin = dp(16);
        top.addView(settingsButton, glp);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.topMargin = dp(22);
        tp.rightMargin = dp(48);
        root.addView(top, tp);

        // Selected app's name.
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        heroTitle = Look.text(this, 52, Color.WHITE, "sans-serif-medium", Typeface.BOLD);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        heroHint = Look.text(this, 15, 0xFFAEB6C2, "sans-serif", Typeface.NORMAL);
        titleBox.addView(heroTitle);
        titleBox.addView(heroHint);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = dp(56);
        hp.rightMargin = dp(56);
        hp.topMargin = heroH - dp(122);
        root.addView(titleBox, hp);

        // My apps: one row along the bottom.
        TextView appsLabel = Look.text(this, 14, 0xFF9AA3AF, "sans-serif-medium", Typeface.BOLD);
        appsLabel.setText("MY APPS");
        appsLabel.setLetterSpacing(0.14f);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        alp.leftMargin = dp(56);
        alp.topMargin = heroH + dp(4);
        root.addView(appsLabel, alp);

        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setClipChildren(false);
        rowScroll.setPadding(dp(56), dp(22), dp(56), dp(22));
        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setGravity(Gravity.TOP);
        rowScroll.addView(row, new ViewGroup.LayoutParams(-2, -2));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        rlp.topMargin = heroH + dp(14);
        root.addView(rowScroll, rlp);

        hint = Look.text(this, 13, 0xFF8C95A3, "sans-serif", Typeface.NORMAL);
        hint.setText("Press Menu on an app to move or remove it");
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hlp.bottomMargin = dp(14);
        root.addView(hint, hlp);
        return root;
    }

    /** Shows the selected app in the Spotlight area: its name over a faint wash of its colour. */
    private void spotlight(Apps.App app) {
        int tint = app == null ? 0xFF3A4150 : app.tint;
        heroColor.animate().cancel();
        heroColor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(tint & 0x00FFFFFF) | 0x47000000, (tint & 0x00FFFFFF) | 0x14000000, 0x00000000}));
        heroColor.setAlpha(0.4f);
        heroColor.animate().alpha(1f).setDuration(220).start();
        if (app == null) {
            heroTitle.setText("Add an app");
            heroHint.setText("Press OK to choose from your apps");
        } else {
            heroTitle.setText(app.label);
            heroHint.setText("OK to open  ·  Menu to move or remove");
        }
    }

    private View settingsButton() {
        final FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setContentDescription("Settings. Press Menu for the other settings.");
        Look.noSystemFocusHighlight(b);
        // The highlight grows inside the button's own area (no zoom), so nothing can be cut off.
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x22FFFFFF);
        final int inset = dp(5);
        b.setBackground(new android.graphics.drawable.InsetDrawable(bg, inset));
        final Glyph g = new Glyph(this, Glyph.GEAR, Color.WHITE);
        b.addView(g, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? Color.WHITE : 0x22FFFFFF);
                g.setColor(has ? 0xFF111111 : Color.WHITE);
                v.setBackground(new android.graphics.drawable.InsetDrawable(bg, has ? 0 : inset));
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings(!Apps.prefs(HomeActivity.this).getBoolean("gear_firetv", false));
            }
        });
        b.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                    openSettings(Apps.prefs(HomeActivity.this).getBoolean("gear_firetv", false));
                    return true;
                }
                return false;
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openSettings(Apps.prefs(HomeActivity.this).getBoolean("gear_firetv", false));
                return true;
            }
        });
        return b;
    }

    /** ours = this Home app's Settings; otherwise Fire TV's own Settings. */
    private void openSettings(boolean ours) {
        if (ours) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        SettingsActivity.openFireTvSettings(this, null, android.provider.Settings.ACTION_SETTINGS);
    }

    // ------------------------------------------------------------------ data

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Apps.App> found = Apps.installed(HomeActivity.this);
                final List<String> pins = Apps.pinned(HomeActivity.this, found);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        all = found;
                        pinned = pins;
                        render();
                    }
                });
            }
        }, "load-apps").start();
    }

    private List<Apps.App> pinnedApps() {
        List<Apps.App> out = new ArrayList<Apps.App>();
        for (String p : pinned) {
            Apps.App a = Apps.find(all, p);
            if (a != null) out.add(a);
        }
        return out;
    }

    // ------------------------------------------------------------------ row

    private void render() {
        boolean settingsFocused = getCurrentFocus() == settingsButton;
        root.setBackgroundColor(Look.baseColor(this));
        row.removeAllViews();
        int tileW = dp(Look.spotlightTileWidth(this));
        int tileH = tileW * 9 / 16;
        boolean names = Apps.prefs(this).getBoolean("names", false);

        List<Apps.App> apps = pinnedApps();
        View toFocus = null;
        for (int i = 0; i < apps.size(); i++) {
            View t = appTile(apps.get(i), tileW, tileH, names);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = dp(14);
            row.addView(t, lp);
            if (apps.get(i).pkg.equals(focusPkg)) toFocus = t;
        }
        View add = addTile(tileH, tileH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (!apps.isEmpty()) lp.leftMargin = dp(14);
        row.addView(add, lp);
        if ("__add".equals(focusPkg) || toFocus == null && apps.isEmpty()) toFocus = add;
        if (toFocus == null) toFocus = row.getChildAt(0);
        hint.setVisibility(apps.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (!settingsFocused) {
            final View f = toFocus;
            f.post(new Runnable() {
                @Override
                public void run() {
                    f.requestFocus();
                }
            });
        }
        if (apps.isEmpty()) spotlight(null);
    }

    private View tileAt(int index) {
        return row.getChildCount() > index ? row.getChildAt(index) : null;
    }

    private View appTile(final Apps.App app, int w, int h, boolean showName) {
        FrameLayout card = card(w, h, app.tint);
        ImageView img = new ImageView(this);
        if (app.banner != null) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(app.banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(app.icon);
            card.addView(img, new FrameLayout.LayoutParams(h * 3 / 5, h * 3 / 5, Gravity.CENTER));
        }
        final View tile = wrap(card, app.label, w, h, showName);
        tile.setContentDescription(app.label);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                animateFocus(v, has);
                if (has) {
                    focusPkg = app.pkg;
                    spotlight(app);
                }
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.launch(HomeActivity.this, app);
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                    appOptions(app);
                    return true;
                }
                return false;
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                appOptions(app);
                return true;
            }
        });
        return tile;
    }

    private View addTile(int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = Look.rounded(this, 0x14FFFFFF, 14);
        bg.setStroke(dp(2), 0x59FFFFFF, dp(8), dp(6));
        card.setBackground(bg);
        Glyph plus = new Glyph(this, Glyph.PLUS, 0xFFE6E9EE);
        card.addView(plus, new FrameLayout.LayoutParams(h / 3, h / 3, Gravity.CENTER));
        final View tile = wrap(card, "Add app", w, h, false);
        tile.setContentDescription("Add an app");
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                animateFocus(v, has);
                bg.setColor(has ? 0x33FFFFFF : 0x14FFFFFF);
                if (has) {
                    focusPkg = "__add";
                    spotlight(null);
                }
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickApp();
            }
        });
        return tile;
    }

    private FrameLayout card(int w, int h, int tint) {
        FrameLayout card = new FrameLayout(this);
        final float radius = dp(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Look.lighten(tint, 0.18f), Look.darken(tint, 0.35f)}));
        return card;
    }

    /**
     * Tile = a ring (white outline when selected) around the card, plus an optional name below.
     * Tag holds {ring, label}.
     */
    private View wrap(View card, String name, int w, int h, boolean showName) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setClipChildren(false);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        Look.noSystemFocusHighlight(tile);
        FrameLayout ring = new FrameLayout(this);
        int pad = dp(5);
        ring.setPadding(pad, pad, pad, pad);
        ring.addView(card, new FrameLayout.LayoutParams(w, h));
        tile.addView(ring, new LinearLayout.LayoutParams(w + pad * 2, h + pad * 2));
        TextView label = Look.text(this, 14, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);
        label.setText(name);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setVisibility(showName ? View.VISIBLE : View.GONE);
        label.setAlpha(0.8f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w + pad * 2, -2);
        llp.topMargin = dp(6);
        tile.addView(label, llp);
        tile.setTag(new View[]{ring, label});
        return tile;
    }

    private void animateFocus(View tile, boolean has) {
        View[] parts = (View[]) tile.getTag();
        View ring = parts[0];
        if (has) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setStroke(dp(3), Color.WHITE);
            ring.setBackground(g);
        } else {
            ring.setBackground(null);
        }
        float s = has ? 1.07f : 1f;
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(ring, "scaleX", s),
                ObjectAnimator.ofFloat(ring, "scaleY", s),
                ObjectAnimator.ofFloat(ring, "translationZ", has ? dp(14) : 0),
                ObjectAnimator.ofFloat(parts[1], "alpha", has ? 1f : 0.8f));
        set.setDuration(150);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    // ------------------------------------------------------------------ editing

    private void appOptions(final Apps.App app) {
        final int index = pinned.indexOf(app.pkg);
        final List<String> items = new ArrayList<String>();
        if (index > 0) items.add("Move left");
        if (index >= 0 && index < pinned.size() - 1) items.add("Move right");
        if (index > 0) items.add("Move to start");
        items.add("Remove from Home");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(app.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String c = items.get(which);
                        if (c.equals("Move left")) {
                            java.util.Collections.swap(pinned, index, index - 1);
                        } else if (c.equals("Move right")) {
                            java.util.Collections.swap(pinned, index, index + 1);
                        } else if (c.equals("Move to start")) {
                            pinned.remove(index);
                            pinned.add(0, app.pkg);
                        } else {
                            pinned.remove(app.pkg);
                            focusPkg = index > 0 ? pinned.get(index - 1) : (pinned.isEmpty() ? "__add" : pinned.get(0));
                        }
                        Apps.savePinned(HomeActivity.this, pinned);
                        render();
                    }
                })
                .show();
    }

    private void pickApp() {
        final List<Apps.App> choices = new ArrayList<Apps.App>();
        for (Apps.App a : all) if (!pinned.contains(a.pkg)) choices.add(a);
        if (choices.isEmpty()) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("All your apps are already on Home")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        AppListAdapter adapter = new AppListAdapter(this, choices);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add an app")
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Apps.App a = choices.get(which);
                        pinned.add(a.pkg);
                        Apps.savePinned(HomeActivity.this, pinned);
                        focusPkg = a.pkg;
                        render();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** List rows with the app's icon and name, for the "Add an app" picker. */
    static final class AppListAdapter extends ArrayAdapter<Apps.App> {
        AppListAdapter(Context c, List<Apps.App> apps) {
            super(c, 0, apps);
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Context c = getContext();
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = Look.dp(c, 20);
            row.setPadding(p, Look.dp(c, 10), p, Look.dp(c, 10));
            Apps.App a = getItem(pos);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(a.icon);
            row.addView(icon, new LinearLayout.LayoutParams(Look.dp(c, 36), Look.dp(c, 36)));
            TextView t = Look.text(c, 17, Color.WHITE, "sans-serif", Typeface.NORMAL);
            t.setText(a.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Look.dp(c, 16);
            row.addView(t, lp);
            return row;
        }
    }

    private int dp(float v) {
        return Look.dp(this, v);
    }
}
