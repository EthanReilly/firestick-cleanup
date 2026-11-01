package com.firehome.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared look: backgrounds, colours and text helpers. */
final class Look {

    static final String[] BACKGROUND_NAMES = {"Midnight", "Ocean", "Graphite", "Aurora", "Sunset"};
    private static final int[][] BACKGROUNDS = {
            {0xFF0B0D11, 0xFF141A24},
            {0xFF06131F, 0xFF0D2E4A},
            {0xFF151618, 0xFF2A2C30},
            {0xFF071A17, 0xFF1C1242},
            {0xFF1A0D14, 0xFF3A1C1A},
    };

    static final String[] SIZE_NAMES = {"Large", "Medium", "Small"};
    static final int[] SIZE_COLUMNS = {4, 5, 6};

    private Look() {
    }

    static GradientDrawable background(Context c) {
        int i = Apps.prefs(c).getInt("background", 0);
        if (i < 0 || i >= BACKGROUNDS.length) i = 0;
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, BACKGROUNDS[i]);
    }

    /** Solid colour of the chosen background (Spotlight fades into it). */
    static int baseColor(Context c) {
        int i = Apps.prefs(c).getInt("background", 0);
        if (i < 0 || i >= BACKGROUNDS.length) i = 0;
        return BACKGROUNDS[i][0];
    }

    /** Spotlight tile width in dp for the Tile size setting (Large / Medium / Small). */
    static int spotlightTileWidth(Context c) {
        int i = Apps.prefs(c).getInt("size", 1);
        int[] w = {236, 196, 164};
        return w[Math.max(0, Math.min(w.length - 1, i))];
    }

    static int columns(Context c) {
        int i = Apps.prefs(c).getInt("size", 1);
        return SIZE_COLUMNS[Math.max(0, Math.min(SIZE_COLUMNS.length - 1, i))];
    }

    static TextView text(Context c, float sp, int color, String family, int style) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, style));
        return t;
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Turns off Android's own grey focus box (API 26+), which would sit on top of our highlight. */
    static void noSystemFocusHighlight(android.view.View v) {
        try {
            android.view.View.class.getMethod("setDefaultFocusHighlightEnabled", boolean.class).invoke(v, false);
        } catch (Exception ignored) {
        }
    }

    static int lighten(int c, float f) {
        return Color.rgb((int) (Color.red(c) + (255 - Color.red(c)) * f),
                (int) (Color.green(c) + (255 - Color.green(c)) * f),
                (int) (Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * (1 - f)), (int) (Color.green(c) * (1 - f)),
                (int) (Color.blue(c) * (1 - f)));
    }
}
