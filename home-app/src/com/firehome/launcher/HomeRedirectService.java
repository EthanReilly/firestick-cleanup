package com.firehome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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
        Log.i(LocalShell.TAG, "redirect service connected, shell set up: " + LocalShell.isEnabled(this));
        if (LocalShell.isEnabled(this)) LocalShell.connect(this, null);
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
        if (!AMAZON_LAUNCHER.equals(pkg) && !AMAZON_HOME_STARTER.equals(pkg)) return;
        CharSequence c = e.getClassName();
        String cls = c == null ? "" : c.toString();
        boolean isHome = homeClasses.contains(cls)
                || cls.contains("HomeActivity")
                || (AMAZON_HOME_STARTER.equals(pkg) && cls.endsWith("Activity"));
        if (!isHome) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < 500) return;
        lastRedirect = now;
        // Through the shell this opens at once; a normal app launch waits 5 s after a Home press.
        boolean viaShell = LocalShell.openHome(this);
        Log.i(LocalShell.TAG, "Amazon home appeared (" + cls + "), opening ours " + (viaShell ? "via shell" : "as app"));
        if (viaShell) return;
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
