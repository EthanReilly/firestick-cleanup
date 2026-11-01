package com.firehome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches the Home key before Fire OS acts on it, so Amazon's home screen is never drawn.
 * Holding Home opens Settings. If Fire OS doesn't pass the Home key to this service, the
 * separate HomeRedirectService still swaps Amazon's screen for ours (with a brief flash).
 */
public class HomeKeyService extends AccessibilityService {

    private static final long LONG_PRESS_MS = 650;
    static volatile long lastHomeKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longFired;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longFired = true;
            open(SettingsActivity.class);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(LocalShell.TAG, "key service connected");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getRepeatCount() == 0) {
            Log.i(LocalShell.TAG, "key seen: " + KeyEvent.keyCodeToString(e.getKeyCode())
                    + (e.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up"));
        }
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                longFired = false;
                handler.postDelayed(longPress, LONG_PRESS_MS);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            if (!longFired) {
                lastHomeKey = SystemClock.uptimeMillis();
                open(HomeActivity.class);
            }
        }
        return true;
    }

    private void open(Class<?> cls) {
        if (cls == HomeActivity.class && LocalShell.openHome(this)) return;
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (cls == HomeActivity.class) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
