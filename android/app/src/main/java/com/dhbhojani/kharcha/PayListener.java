package com.dhbhojani.kharcha;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads payment notifications from UPI apps and queues them for tagging in Kharcha. */
public class PayListener extends NotificationListenerService {

    static final Set<String> PAY_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "com.samsung.android.spay",               // Samsung Wallet
            "in.org.npci.upiapp",                     // BHIM
            "in.amazon.mShop.android.shopping",
            "com.dreamplug.androidapp",               // CRED
            "com.mobikwik_new",
            "com.sbi.upi", "com.sbi.lotusintouch",
            "com.snapwork.hdfc", "com.hdfcbank.payzapp",
            "com.csam.icici.bank.imobile", "com.icicibank.pockets",
            "com.axis.mobile", "com.msf.kbank.mobile",
            "com.fss.pnbpsp", "com.bankofbaroda.mconnect"));

    static final Set<String> SMS_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"));

    static final Pattern MONEY = Pattern.compile("((₹|\\brs\\.?|\\binr)\\s*[\\d,]+)|((debited|paid|sent|spent|transferred)\\s+(by|for|of|with)?\\s*[\\d,]+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern OUT = Pattern.compile("\\b(paid|sent|debited|payment|transferred|successful|spent|debit|trf|txn)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern NOT = Pattern.compile("\\b(received|credited|cashback|offer|reward|requested|request|due|win|won|remind|reminder|scratch|coupon|refund|refunded|failed|declined|otp|expires|expiring|loan|emi due)\\b", Pattern.CASE_INSENSITIVE);

    static String last = "";
    static long lastTs = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            boolean sms = SMS_APPS.contains(pkg)
                    && getSharedPreferences("k", MODE_PRIVATE).getBoolean("sms", false);
            if (pkg.equals(getPackageName())) return;
            Notification n = sbn.getNotification();
            Bundle x = n.extras;
            String title = str(x.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(x.getCharSequence(Notification.EXTRA_TEXT));
            String all = (title + "\n" + text).trim();
            boolean watched = PAY_APPS.contains(pkg) || SMS_APPS.contains(pkg);
            boolean money = MONEY.matcher(all).find();
            String why;
            if (!watched && !money) return;            // ignore unrelated apps entirely
            if (!PAY_APPS.contains(pkg) && !sms) why = SMS_APPS.contains(pkg) ? "SMS reading is OFF" : "app not in list";
            else if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) why = "summary";
            else if (!money) why = "no amount";
            else if (!OUT.matcher(all).find()) why = "not a payment";
            else if (NOT.matcher(all).find()) why = "looks like offer/credit/OTP";
            else why = "CAPTURED";
            log(this, pkg, all, why);
            if (!"CAPTURED".equals(why)) return;
            long now = System.currentTimeMillis();
            if (all.equals(last) && now - lastTs < 10 * 60 * 1000) return;
            last = all;
            lastTs = now;
            add(this, all, now);
            final String txt = all;
            if (Settings.canDrawOverlays(this)) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try { Popup.show(PayListener.this, txt, now, () -> notifyUser(txt)); }
                    catch (Exception e) { notifyUser(txt); }
                });
            } else {
                notifyUser(all);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        try { requestRebind(new ComponentName(this, PayListener.class)); } catch (Exception ignored) { }
    }

    @Override
    public void onListenerConnected() {
        getSharedPreferences("k", MODE_PRIVATE).edit().putLong("conn", System.currentTimeMillis()).apply();
    }

    static synchronized void log(Context c, String pkg, String t, String why) {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);
        JSONArray a;
        try { a = new JSONArray(p.getString("log", "[]")); } catch (Exception e) { a = new JSONArray(); }
        JSONArray b = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("pkg", pkg); o.put("t", t.length() > 160 ? t.substring(0, 160) : t); o.put("why", why); o.put("ts", System.currentTimeMillis());
            b.put(o);
            for (int i = 0; i < a.length() && i < 24; i++) b.put(a.get(i));
        } catch (Exception ignored) { }
        p.edit().putString("log", b.toString()).apply();
    }

    static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    static synchronized void add(Context c, String t, long ts) {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);
        JSONArray a;
        try {
            a = new JSONArray(p.getString("q", "[]"));
        } catch (Exception e) {
            a = new JSONArray();
        }
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("ts", ts);
            a.put(o);
        } catch (Exception ignored) {
        }
        p.edit().putString("q", a.toString()).apply();
    }

    static synchronized void markDone(Context c, long ts, double amt, String payee, String cat, String note) {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(p.getString("q", "[]"));
            boolean found = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("ts") == ts) { found = true; fill(o, amt, payee, cat, note); }
            }
            if (!found) { JSONObject o = new JSONObject(); o.put("ts", ts); o.put("t", ""); fill(o, amt, payee, cat, note); a.put(o); }
            p.edit().putString("q", a.toString()).apply();
        } catch (Exception ignored) { }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(7);
    }

    static void fill(JSONObject o, double amt, String payee, String cat, String note) throws Exception {
        o.put("done", true); o.put("amt", amt); o.put("payee", payee); o.put("cat", cat); o.put("note", note);
    }

    static synchronized String take(Context c) {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);
        String s = p.getString("q", "[]");
        p.edit().putString("q", "[]").apply();
        return s;
    }

    void notifyUser(String t) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("pay", "Payments to tag", NotificationManager.IMPORTANCE_HIGH));
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "pay")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("💸 Tag this payment")
                .setContentText(t.replace("\n", " · "))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(7, n);
    }
}
