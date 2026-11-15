package com.dhbhojani.kharcha;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: travel card balances (as last read by tapping the card) + a quick "+ Cash" button. */
public class KharchaWidget extends AppWidgetProvider {

    static final int[] ROW = {R.id.w_card1, R.id.w_card2};
    static final int[] NAME = {R.id.w_name1, R.id.w_name2};
    static final int[] BAL = {R.id.w_bal1, R.id.w_bal2};
    static final int[] WHEN = {R.id.w_when1, R.id.w_when2};
    static final int[] LOW = {R.id.w_low1, R.id.w_low2};
    static final double LOW_AT = 50;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    /** Called when the app saves new card balances. */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, KharchaWidget.class));
        if (ids.length > 0) for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        JSONArray cards;
        try { cards = new JSONArray(c.getSharedPreferences("k", Context.MODE_PRIVATE).getString("cards", "[]")); }
        catch (Exception e) { cards = new JSONArray(); }

        int shown = 0;
        for (int i = 0; i < ROW.length; i++) {
            JSONObject o = cards.optJSONObject(i);
            if (o == null) { v.setViewVisibility(ROW[i], View.GONE); continue; }
            shown++;
            v.setViewVisibility(ROW[i], View.VISIBLE);
            String last4 = o.optString("last4", "");
            v.setTextViewText(NAME[i], o.optString("name", "Travel card") + (last4.isEmpty() ? "" : " ··" + last4));
            v.setTextViewText(BAL[i], o.isNull("balance") ? "—" : "₹" + Parse.fmt(o.optDouble("balance")));
            v.setViewVisibility(LOW[i], !o.isNull("balance") && o.optDouble("balance") < LOW_AT ? View.VISIBLE : View.GONE);
            long at = o.optLong("readAt", 0);
            v.setTextViewText(WHEN[i], at > 0 ? "read " + new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(new Date(at)) : "");
        }
        v.setViewVisibility(R.id.w_empty, shown == 0 ? View.VISIBLE : View.GONE);
        totals(c, v);

        // tap the cards -> Kharcha's travel card tab (tap your card there to refresh the balance)
        Intent open = new Intent(c, MainActivity.class).putExtra("a", "card")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_cards, PendingIntent.getActivity(c, 11, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        // + Cash -> the pop-up straight on the home screen
        Intent cash = new Intent(c, CashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        v.setOnClickPendingIntent(R.id.w_cash, PendingIntent.getActivity(c, 12, cash,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        // mic -> speak an expense, then the pop-up opens filled in
        Intent voice = new Intent(c, CashActivity.class).putExtra("voice", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        v.setOnClickPendingIntent(R.id.w_mic, PendingIntent.getActivity(c, 13, voice,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent month = new Intent(c, MainActivity.class).putExtra("a", "month")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_tot, PendingIntent.getActivity(c, 14, month,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    /** today / this month: totals the app last saved + anything added from the widget since (not yet in the app). */
    static void totals(Context c, RemoteViews v) {
        android.content.SharedPreferences p = c.getSharedPreferences("k", Context.MODE_PRIVATE);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        long monthStart = cal.getTimeInMillis();

        double today = 0, month = 0;
        try {
            JSONObject t = new JSONObject(p.getString("totals", "{}"));
            if (t.optLong("dayStart") == dayStart) today = t.optDouble("today", 0);
            if (t.optLong("monthStart") == monthStart) month = t.optDouble("month", 0);
        } catch (Exception ignored) { }
        try {
            JSONArray q = new JSONArray(p.getString("q", "[]"));
            for (int i = 0; i < q.length(); i++) {
                JSONObject o = q.getJSONObject(i);
                if (!o.optBoolean("done")) continue;
                long ts = o.optLong("ts");
                double a = o.optDouble("amt", 0);
                if (ts >= dayStart) today += a;
                if (ts >= monthStart) month += a;
            }
        } catch (Exception ignored) { }
        v.setTextViewText(R.id.w_today, rupees(today));
        v.setTextViewText(R.id.w_month, rupees(month));
        v.setTextViewText(R.id.w_monthname, new SimpleDateFormat("MMMM", Locale.ENGLISH).format(new Date()).toUpperCase(Locale.ENGLISH));
    }

    static String rupees(double a) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMaximumFractionDigits(a < 1000 && a != Math.floor(a) ? 2 : 0);
        return "₹" + f.format(a);
    }
}
