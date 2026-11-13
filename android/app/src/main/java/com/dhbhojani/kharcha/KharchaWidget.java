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
            long at = o.optLong("readAt", 0);
            v.setTextViewText(WHEN[i], at > 0 ? "read " + new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(new Date(at)) : "");
        }
        v.setViewVisibility(R.id.w_empty, shown == 0 ? View.VISIBLE : View.GONE);

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
        return v;
    }
}
