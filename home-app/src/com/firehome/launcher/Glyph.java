package com.firehome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Small vector glyphs (gear, plus, chevron) so no image resources are needed. */
final class Glyph extends View {
    static final int GEAR = 1, PLUS = 2, CHEVRON = 3, WIFI = 4, DISPLAY = 5, APPS = 6, CONTROLLER = 7,
            ACCESS = 8, DEVICE = 9, BELL = 10, SLIDERS = 11, TV = 12, REMOTE = 13, HELP = 14, PERSON = 15,
            SHIELD = 16, HOME = 17, INFO = 18;

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
            case WIFI:
                c.drawArc(new RectF(2, 6, 22, 26), -135, 90, false, p);
                c.drawArc(new RectF(5.5f, 9.5f, 18.5f, 22.5f), -135, 90, false, p);
                c.drawArc(new RectF(9, 13, 15, 19), -135, 90, false, p);
                c.drawCircle(12, 19.5f, 0.8f, p);
                break;
            case DISPLAY:
                c.drawRoundRect(new RectF(3, 4, 21, 16), 1.5f, 1.5f, p);
                c.drawLine(9, 20, 15, 20, p);
                c.drawLine(12, 16, 12, 20, p);
                break;
            case APPS:
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        c.drawRoundRect(new RectF(4 + i * 9, 4 + j * 9, 11 + i * 9, 11 + j * 9), 1.5f, 1.5f, p);
                    }
                }
                break;
            case CONTROLLER:
                c.drawRoundRect(new RectF(2.5f, 7, 21.5f, 17), 5, 5, p);
                c.drawLine(7, 10, 7, 14, p);
                c.drawLine(5, 12, 9, 12, p);
                c.drawCircle(15.5f, 11, 0.7f, p);
                c.drawCircle(17.5f, 13, 0.7f, p);
                break;
            case ACCESS:
                c.drawCircle(12, 5, 1.6f, p);
                c.drawLine(5, 8.5f, 19, 8.5f, p);
                c.drawLine(12, 8.5f, 12, 14, p);
                c.drawLine(12, 14, 8, 20.5f, p);
                c.drawLine(12, 14, 16, 20.5f, p);
                break;
            case DEVICE:
                c.drawRoundRect(new RectF(3, 9, 21, 15), 1.5f, 1.5f, p);
                c.drawLine(7, 12, 11, 12, p);
                break;
            case BELL:
                c.drawArc(new RectF(6, 4, 18, 16), 180, 180, false, p);
                c.drawLine(6, 10, 6, 16, p);
                c.drawLine(18, 10, 18, 16, p);
                c.drawLine(4, 16.5f, 20, 16.5f, p);
                c.drawLine(10.5f, 20, 13.5f, 20, p);
                break;
            case SLIDERS:
                c.drawLine(4, 7, 20, 7, p);
                c.drawLine(4, 12, 20, 12, p);
                c.drawLine(4, 17, 20, 17, p);
                c.drawCircle(9, 7, 1.8f, p);
                c.drawCircle(15, 12, 1.8f, p);
                c.drawCircle(7, 17, 1.8f, p);
                break;
            case TV:
                c.drawRoundRect(new RectF(3, 7, 21, 19), 1.5f, 1.5f, p);
                c.drawLine(8, 3, 12, 7, p);
                c.drawLine(16, 3, 12, 7, p);
                break;
            case REMOTE:
                c.drawRoundRect(new RectF(8, 2.5f, 16, 21.5f), 3, 3, p);
                c.drawCircle(12, 8, 1.8f, p);
                c.drawLine(10.5f, 13, 13.5f, 13, p);
                c.drawLine(10.5f, 16.5f, 13.5f, 16.5f, p);
                break;
            case HELP:
                c.drawCircle(12, 12, 9, p);
                c.drawArc(new RectF(9.2f, 6.5f, 14.8f, 12), 200, 250, false, p);
                c.drawLine(12, 12, 12, 13.8f, p);
                c.drawCircle(12, 16.8f, 0.5f, p);
                break;
            case PERSON:
                c.drawCircle(12, 8, 3.8f, p);
                c.drawArc(new RectF(4.5f, 14, 19.5f, 28), 180, 180, false, p);
                break;
            case SHIELD:
                c.drawLine(12, 3, 19.5f, 6, p);
                c.drawLine(12, 3, 4.5f, 6, p);
                c.drawLine(4.5f, 6, 4.5f, 11, p);
                c.drawLine(19.5f, 6, 19.5f, 11, p);
                c.drawArc(new RectF(4.5f, 3, 19.5f, 21), 0, 180, false, p);
                break;
            case HOME:
                c.drawLine(3, 11.5f, 12, 4, p);
                c.drawLine(12, 4, 21, 11.5f, p);
                c.drawLine(6, 9.5f, 6, 20, p);
                c.drawLine(18, 9.5f, 18, 20, p);
                c.drawLine(6, 20, 18, 20, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, p);
                c.drawLine(12, 11, 12, 17, p);
                c.drawCircle(12, 7.5f, 0.5f, p);
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
