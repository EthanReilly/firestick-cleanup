package com.firehome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backup Home redirect: whenever Amazon's home screen comes to the front, show ours instead.
 * Works on every Fire OS version, but Amazon's screen may flash briefly first.
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_STARTER = "com.amazon.firehomestarter";

    /** Fire Remote (the phone app) connects here to open this Home screen directly. */
    static final int REMOTE_PORT = 47100;

    private final Set<String> homeClasses = new HashSet<String>();
    private long lastRedirect;
    private volatile ServerSocket server;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static volatile HomeRedirectService instance;
    /** Picture of the Home screen, taken when it was last left; shown instantly while Fire OS holds it back. */
    static volatile Bitmap snapshot;
    private View cover;

    /** Set when Fire TV Settings is opened or shown, so its screens aren't mistaken for Amazon's home. */
    static volatile long settingsSeenAt;

    /** Words on Fire TV's Settings screen (it is shown by Amazon's home app, so its window looks the same). */
    private static final String[] SETTINGS_WORDS = {"My Fire TV", "Display & Sounds", "Controllers & Bluetooth",
            "Equipment Control", "Accessibility", "Network", "Applications", "Notifications", "Live TV", "Privacy",
            "Preferences", "Alexa"};

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        homeClasses.clear();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (AMAZON_LAUNCHER.equals(pkg) || AMAZON_HOME_STARTER.equals(pkg)) {
                    homeClasses.add(ri.activityInfo.name);
                }
            }
        } catch (Exception ignored) {
        }
        startRemoteListener();
        // Fire OS 7.7 refuses ADB connections from the Fire Stick itself, so there's no shell here;
        // Home opens as an app (Fire OS may hold it back ~5 s after a remote Home press).
        Log.i(LocalShell.TAG, "redirect service connected");
    }

    /**
     * Tiny line-based listener for Fire Remote: "HOME" opens this Home screen and replies "OK".
     * Opening a screen this way is instant, unlike pressing Fire OS's Home key, which shows
     * Amazon's home and blocks other screens for 5 seconds. It only runs while this service is on,
     * so turning the Home button off in Settings also makes Fire Remote use Amazon's Home.
     */
    private void startRemoteListener() {
        if (server != null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket ss = new ServerSocket(REMOTE_PORT);
                    ss.setReuseAddress(true);
                    server = ss;
                    while (!ss.isClosed()) {
                        final Socket s = ss.accept();
                        handle(s);
                    }
                } catch (Exception ignored) {
                    server = null;
                }
            }
        }, "remote-listener");
        t.setDaemon(true);
        t.start();
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(2000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = in.readLine();
            OutputStream out = s.getOutputStream();
            if (line != null && line.trim().equalsIgnoreCase("HOME")) {
                lastRedirect = SystemClock.uptimeMillis();
                settingsSeenAt = 0;
                Intent i = new Intent(this, HomeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(i);
                out.write("OK\n".getBytes("UTF-8"));
            } else if (line != null && line.trim().toUpperCase().startsWith("KEY ")) {
                // Remote keys Fire OS's remote service can't send (volume, mute), via the shell.
                String code = line.trim().substring(4).trim();
                boolean ok = code.matches("\\d{1,3}") && LocalShell.run(this, "input keyevent " + code);
                out.write((ok ? "OK\n" : "NOSHELL\n").getBytes("UTF-8"));
            } else if (line != null && line.trim().equalsIgnoreCase("PING")) {
                out.write(("FIREHOME shell=" + (LocalShell.isReady() ? 1 : 0) + "\n").getBytes("UTF-8"));
            } else {
                out.write("?\n".getBytes("UTF-8"));
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideCover();
        if (instance == this) instance = null;
        ServerSocket ss = server;
        server = null;
        if (ss != null) {
            try { ss.close(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.startsWith("com.amazon.tv.settings")) settingsSeenAt = SystemClock.uptimeMillis();
        if (!AMAZON_LAUNCHER.equals(pkg) && !AMAZON_HOME_STARTER.equals(pkg)) return;
        CharSequence c = e.getClassName();
        String cls = c == null ? "" : c.toString();
        boolean isHome = homeClasses.contains(cls)
                || cls.contains("HomeActivity")
                || cls.contains("SettingsActivity")
                || (AMAZON_HOME_STARTER.equals(pkg) && cls.endsWith("Activity"));
        if (!isHome) return;
        if (cls.contains("SettingsActivity")) {
            settingsSeenAt = SystemClock.uptimeMillis();
            Log.i(LocalShell.TAG, "Fire TV Settings opened, not redirecting");
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < 500) return;
        // Fire TV Settings is drawn by Amazon's home app: if Settings was in use recently, give the
        // screen a moment to draw, then only redirect if it really is the home screen.
        long delay = now - settingsSeenAt < 30 * 60 * 1000 ? 450 : 0;
        handler.removeCallbacks(check);
        handler.postDelayed(check, delay);
    }

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (showingSettings()) {
                settingsSeenAt = SystemClock.uptimeMillis();
                Log.i(LocalShell.TAG, "Fire TV Settings on screen, not redirecting");
                return;
            }
            lastRedirect = SystemClock.uptimeMillis();
            settingsSeenAt = 0;
            boolean viaShell = LocalShell.openHome(HomeRedirectService.this);
            Log.i(LocalShell.TAG, "Amazon home on screen, opening ours " + (viaShell ? "via shell" : "as app"));
            if (viaShell) return;
            showCover(); // Fire OS may hold the real Home back for 5 s: show its picture meanwhile
            Intent i = new Intent(HomeRedirectService.this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
    };

    private final Runnable hideCoverLater = new Runnable() {
        @Override
        public void run() {
            hideCover();
        }
    };

    private void showCover() {
        if (cover != null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            ImageView v = new ImageView(this);
            v.setScaleType(ImageView.ScaleType.FIT_XY); // same size as the screen, so drawn 1:1
            Bitmap b = snapshot;
            if (b != null) v.setImageBitmap(b);
            v.setBackgroundColor(Look.baseColor(this));
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            v.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int code, android.view.KeyEvent e) {
                    return true; // don't let presses reach Amazon's home underneath
                }
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            wm.addView(v, lp);
            v.requestFocus();
            cover = v;
            handler.removeCallbacks(hideCoverLater);
            handler.postDelayed(hideCoverLater, 6500);
            Log.i(LocalShell.TAG, "cover shown");
        } catch (Exception e) {
            Log.i(LocalShell.TAG, "cover failed: " + e);
        }
    }

    /** Called when the real Home screen is showing. */
    void hideCover() {
        handler.removeCallbacks(hideCoverLater);
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (Exception ignored) {
        }
        cover = null;
    }

    /** True if Amazon's app is currently showing Fire TV Settings rather than its home screen. */
    private boolean showingSettings() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return false;
        }
        if (root == null) return false;
        int hits = 0;
        for (String w : SETTINGS_WORDS) {
            try {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(w);
                if (found != null && !found.isEmpty()) hits++;
            } catch (Exception ignored) {
            }
            if (hits >= 3) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
