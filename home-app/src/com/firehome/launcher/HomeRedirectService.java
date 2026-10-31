package com.firehome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

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

    private final Set<String> homeClasses = new HashSet<String>();
    private long lastRedirect;

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
