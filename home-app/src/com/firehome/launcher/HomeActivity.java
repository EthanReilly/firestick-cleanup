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
import android.widget.ScrollView;
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
    private LinearLayout grid;
    private ScrollView gridScroll;
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
        root.setBackground(Look.background(this)); // settings may have changed
        ui.removeCallbacks(tick);
        tick.run();
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
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
        gridScroll.smoothScrollTo(0, 0);
        View first = tileAt(0);
        if (first != null) first.requestFocus();
    }

    // ------------------------------------------------------------------ layout

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Look.background(this));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(26), 0, 0);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Header: clock on the left, Settings on the right.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);
        clock = Look.text(this, 44, Color.WHITE, "sans-serif-light", Typeface.NORMAL);
        date = Look.text(this, 15, 0xFFAEB6C2, "sans-serif", Typeface.NORMAL);
        times.addView(clock);
        times.addView(date);
        header.addView(times, new LinearLayout.LayoutParams(0, -2, 1f));
        settingsButton = settingsButton();
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        header.setPadding(dp(56), dp(8), dp(56), dp(8));
        header.setClipChildren(false);
        col.addView(header);

        TextView title = Look.text(this, 15, 0xFF8C95A3, "sans-serif-medium", Typeface.BOLD);
        title.setText("MY APPS");
        title.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(22);
        tlp.leftMargin = dp(56);
        col.addView(title, tlp);

        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setClipToPadding(false);
        gridScroll.setClipChildren(false);
        gridScroll.setPadding(dp(56), dp(22), dp(56), dp(40));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        gridScroll.addView(grid, new ViewGroup.LayoutParams(-1, -2));
        col.addView(gridScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        hint = Look.text(this, 13, 0xFF6F7886, "sans-serif", Typeface.NORMAL);
        hint.setText("Press Menu on an app to move or remove it");
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hlp.bottomMargin = dp(16);
        root.addView(hint, hlp);
        return root;
    }

    private View settingsButton() {
        final FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setContentDescription("Settings");
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x22FFFFFF);
        b.setBackground(bg);
        final Glyph g = new Glyph(this, Glyph.GEAR, Color.WHITE);
        b.addView(g, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? Color.WHITE : 0x22FFFFFF);
                g.setColor(has ? 0xFF111111 : Color.WHITE);
                v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(150).start();
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        return b;
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

    // ------------------------------------------------------------------ grid

    private void render() {
        View current = getCurrentFocus();
        boolean settingsFocused = current == settingsButton;
        grid.removeAllViews();
        int cols = Look.columns(this);
        int width = getResources().getDisplayMetrics().widthPixels - dp(112);
        int gap = dp(cols >= 6 ? 18 : 24);
        int tileW = (width - gap * (cols - 1)) / cols;
        int tileH = tileW * 9 / 16;
        boolean alwaysNames = Apps.prefs(this).getBoolean("names", false);

        List<Apps.App> apps = pinnedApps();
        List<View> tiles = new ArrayList<View>();
        for (Apps.App a : apps) tiles.add(appTile(a, tileW, tileH, alwaysNames));
        tiles.add(addTile(tileW, tileH));

        LinearLayout row = null;
        View toFocus = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = dp(alwaysNames ? 22 : 34);
                grid.addView(row, rlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            if (i % cols != 0) lp.leftMargin = gap;
            row.addView(tiles.get(i), lp);
            if (i < apps.size() && apps.get(i).pkg.equals(focusPkg)) toFocus = tiles.get(i);
        }
        if ("__add".equals(focusPkg)) toFocus = tiles.get(tiles.size() - 1);
        if (toFocus == null) toFocus = tiles.get(0);
        if (!settingsFocused) {
            final View f = toFocus;
            f.post(new Runnable() {
                @Override
                public void run() {
                    f.requestFocus();
                }
            });
        }
        hint.setVisibility(apps.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private View tileAt(int index) {
        int cols = Look.columns(this);
        int r = index / cols, c = index % cols;
        if (grid.getChildCount() <= r) return null;
        ViewGroup row = (ViewGroup) grid.getChildAt(r);
        return row.getChildCount() > c ? row.getChildAt(c) : null;
    }

    private View appTile(final Apps.App app, int w, int h, boolean alwaysName) {
        FrameLayout card = card(w, h, app.tint);
        if (app.banner != null) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(app.banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(app.icon);
            card.addView(img, new FrameLayout.LayoutParams(h * 3 / 5, h * 3 / 5, Gravity.CENTER));
        }
        addSheen(card, h);
        final View tile = wrap(card, app.label, w, h, alwaysName);
        tile.setContentDescription(app.label);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                animateFocus(v, has, alwaysName);
                if (has) {
                    focusPkg = app.pkg;
                    keepVisible(v);
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
        final GradientDrawable bg = Look.rounded(this, 0x18FFFFFF, 14);
        bg.setStroke(dp(2), 0x40FFFFFF, dp(8), dp(6));
        card.setBackground(bg);
        Glyph plus = new Glyph(this, Glyph.PLUS, 0xCCFFFFFF);
        card.addView(plus, new FrameLayout.LayoutParams(h / 3, h / 3, Gravity.CENTER));
        final View tile = wrap(card, "Add app", w, h, true);
        tile.setContentDescription("Add an app");
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                animateFocus(v, has, true);
                bg.setColor(has ? 0x40FFFFFF : 0x18FFFFFF);
                if (has) {
                    focusPkg = "__add";
                    keepVisible(v);
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

    private void addSheen(FrameLayout card, int h) {
        View sheen = new View(this);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x22FFFFFF, 0x00FFFFFF}));
        card.addView(sheen, new FrameLayout.LayoutParams(-1, h / 2));
    }

    /** Card plus its name underneath. Tag 0 = card, tag 1 = label (for animation). */
    private View wrap(FrameLayout card, String name, int w, int h, boolean showName) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setClipChildren(false);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.addView(card, new LinearLayout.LayoutParams(w, h));
        TextView label = Look.text(this, 15, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);
        label.setText(name);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(showName ? 0.75f : 0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = dp(10);
        tile.addView(label, llp);
        tile.setTag(new View[]{card, label});
        return tile;
    }

    private void animateFocus(View tile, boolean has, boolean alwaysName) {
        View[] parts = (View[]) tile.getTag();
        float s = has ? 1.1f : 1f;
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(parts[0], "scaleX", s),
                ObjectAnimator.ofFloat(parts[0], "scaleY", s),
                ObjectAnimator.ofFloat(parts[0], "translationZ", has ? dp(16) : 0),
                ObjectAnimator.ofFloat(parts[1], "alpha", has ? 1f : (alwaysName ? 0.75f : 0f)),
                ObjectAnimator.ofFloat(parts[1], "translationY", has ? dp(8) : 0));
        set.setDuration(160);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void keepVisible(View tile) {
        View row = (View) tile.getParent();
        int top = row.getTop() - dp(18);
        int bottom = row.getBottom() + dp(30);
        int y = gridScroll.getScrollY();
        if (top < y) gridScroll.smoothScrollTo(0, Math.max(0, top));
        else if (bottom > y + gridScroll.getHeight()) gridScroll.smoothScrollTo(0, bottom - gridScroll.getHeight());
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
