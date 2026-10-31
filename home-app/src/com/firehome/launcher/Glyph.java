package com.firehome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Small vector glyphs (gear, plus, chevron) so no image resources are needed. */
final class Glyph extends View {
    static final int GEAR = 1, PLUS = 2, CHEVRON = 3;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;

    Glyph(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        this.color = color;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        p.setColor(color);
        p.setStrokeWidth(2f);
        switch (kind) {
            case GEAR:
                c.drawCircle(12, 12, 3.2f, p);
                c.drawCircle(12, 12, 7, p);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, 12, 12);
                    c.drawLine(12, 2.5f, 12, 5, p);
                    c.restore();
                }
                break;
            case PLUS:
                p.setStrokeWidth(2.4f);
                c.drawLine(12, 4, 12, 20, p);
                c.drawLine(4, 12, 20, 12, p);
                break;
            case CHEVRON:
                c.drawLine(9, 6, 15, 12, p);
                c.drawLine(15, 12, 9, 18, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    @SuppressWarnings("unused")
    private static RectF r(float l, float t, float rr, float b) {
        return new RectF(l, t, rr, b);
    }
}
