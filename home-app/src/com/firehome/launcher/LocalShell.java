package com.firehome.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * A shell on this Fire Stick through its own ADB debugging service (127.0.0.1:5555).
 *
 * Commands run this way come from the system "shell" user, which Fire OS does not hold back
 * after a Home press (apps are blocked from opening screens for 5 seconds). That lets the Home
 * button open this Home screen instantly, and lets Fire Remote change the volume.
 *
 * The first connection makes the TV ask "Allow USB debugging?" once; after "Always allow" it
 * reconnects silently.
 */
final class LocalShell {

    interface Callback {
        void onPrompt();

        void onConnected();

        void onFailed(String why);
    }

    static final String TAG = "FireHome";
    private static final String PREF = "local_shell";

    private static volatile AdbClient client;
    private static volatile boolean connecting;

    private LocalShell() {
    }

    static boolean isReady() {
        AdbClient c = client;
        return c != null && c.isConnected();
    }

    static boolean isConnecting() {
        return connecting;
    }

    /** True once the user has set this up (so it should reconnect by itself). */
    static boolean isEnabled(Context ctx) {
        return Apps.prefs(ctx).getBoolean(PREF, false);
    }

    /** Runs a shell command if connected. Returns false (and reconnects in the background) if not. */
    static boolean run(Context ctx, String command) {
        AdbClient c = client;
        if (c != null && c.isConnected()) {
            c.shell(command);
            Log.i(TAG, "shell: " + command);
            return true;
        }
        if (isEnabled(ctx)) connect(ctx, null);
        return false;
    }

    static void connect(Context ctx, final Callback cb) {
        if (connecting || isReady()) {
            if (isReady() && cb != null) cb.onConnected();
            return;
        }
        connecting = true;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AdbKeys keys = AdbKeys.loadOrCreate(app.getFilesDir());
                    AdbClient c = new AdbClient(keys, new AdbClient.Listener() {
                        @Override
                        public void onAuthPromptShown() {
                            Log.i(TAG, "shell: waiting for Allow USB debugging on the TV");
                            if (cb != null) cb.onPrompt();
                        }

                        @Override
                        public void onDisconnected(String reason) {
                            Log.i(TAG, "shell: disconnected: " + reason);
                        }
                    });
                    // Some Fire OS versions refuse ADB from 127.0.0.1, so also try this device's Wi-Fi address.
                    Exception last = null;
                    boolean ok = false;
                    for (String host : candidates()) {
                        try {
                            Log.i(TAG, "shell: connecting to " + host);
                            c.connect(host, 5555);
                            ok = true;
                            break;
                        } catch (Exception ex) {
                            last = ex;
                            Log.i(TAG, "shell: " + host + " failed: " + ex);
                            c = new AdbClient(keys, c.listener());
                        }
                    }
                    if (!ok) throw last != null ? last : new java.io.IOException("No address to connect to");
                    client = c;
                    SharedPreferences.Editor e = Apps.prefs(app).edit();
                    e.putBoolean(PREF, true).apply();
                    Log.i(TAG, "shell: connected");
                    connecting = false;
                    if (cb != null) cb.onConnected();
                } catch (Exception ex) {
                    connecting = false;
                    String why = ex instanceof java.net.ConnectException
                            ? "ADB debugging is off (Settings > My Fire TV > Developer options)."
                            : (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                    Log.i(TAG, "shell: connect failed: " + why);
                    if (cb != null) cb.onFailed(why);
                }
            }
        }, "local-shell").start();
    }

    private static java.util.List<String> candidates() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                java.net.NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> a = ni.getInetAddresses();
                while (a.hasMoreElements()) {
                    java.net.InetAddress x = a.nextElement();
                    if (x instanceof java.net.Inet4Address && x.isSiteLocalAddress()) out.add(x.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        out.add("127.0.0.1");
        return out;
    }

    /** Opens this Home screen through the shell. */
    static boolean openHome(Context ctx) {
        return run(ctx, "am start -n com.firehome.launcher/.HomeActivity -f 0x34010000");
    }
}
