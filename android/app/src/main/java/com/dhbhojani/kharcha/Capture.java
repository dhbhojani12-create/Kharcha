package com.dhbhojani.kharcha;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;

/** One place that records a captured payment and shows the pop-up. */
public class Capture {
    static final Handler H = new Handler(Looper.getMainLooper());
    static long lastTs = 0;
    static String lastKey = "";
    static double lastAmt = 0;
    static long lastScreenTs = 0;

    /** The success screen was read recently – notifications/SMS for it are duplicates. */
    static boolean screenRecent(long now) {
        return now - lastScreenTs < 3 * 60 * 1000;
    }

    /** A capture of the same payment happened recently (screen + notification + SMS all describe one payment). */
    static boolean sameRecent(long now, double amt, String payee) {
        if (now - lastTs > 3 * 60 * 1000) return false;
        if (amt > 0 && lastAmt > 0) return Math.abs(amt - lastAmt) < 0.5;
        String k = Parse.key(payee);
        if (!k.isEmpty() && !lastKey.isEmpty()) return k.startsWith(lastKey) || lastKey.startsWith(k);
        return true;
    }

    static void go(Context c, String txt, long ts, boolean fromScreen) {
        lastTs = ts;
        lastAmt = Parse.amount(txt);
        lastKey = Parse.key(Parse.payee(txt));
        PayListener.add(c, txt, ts);
        H.post(() -> {
            try {
                if (fromScreen) {
                    Popup.show(c, txt, ts, () -> PayListener.notifyUser(c, txt),
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                } else if (Settings.canDrawOverlays(c)) {
                    Popup.show(c, txt, ts, () -> PayListener.notifyUser(c, txt),
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                } else {
                    PayListener.notifyUser(c, txt);
                }
            } catch (Exception e) {
                PayListener.notifyUser(c, txt);
            }
        });
    }
}
