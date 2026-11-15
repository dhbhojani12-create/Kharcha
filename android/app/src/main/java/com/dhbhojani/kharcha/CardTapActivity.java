package com.dhbhojani.kharcha;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Opens by itself when a travel card touches the back of the unlocked phone (Kharcha need not be open).
 * Reads the balance, updates the widget, shows a short message and queues the read for the app.
 */
public class CardTapActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handle(i);
    }

    void handle(Intent i) {
        Tag tag = i == null ? null : i.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        IsoDep iso = tag == null ? null : IsoDep.get(tag);
        if (iso == null) { finish(); return; }
        final Context app = getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                JSONObject res = NcmcCard.read(iso);
                if (!res.has("balance") || res.isNull("balance")) throw new Exception("no balance");
                res.put("readAt", System.currentTimeMillis());
                res.put("bg", true);
                res.remove("trace");
                msg = save(app, res);
            } catch (Exception e) {
                msg = "Kharcha couldn't read the card – hold it still a little longer";
            }
            final String m = msg;
            runOnUiThread(() -> {
                Toast.makeText(app, m, Toast.LENGTH_LONG).show();
                finish();
            });
        }).start();
    }

    /** Queue the read for the web app, update the widget's copy, and return the message to show. */
    static synchronized String save(Context c, JSONObject res) throws Exception {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);

        JSONArray q;
        try { q = new JSONArray(p.getString("cardq", "[]")); } catch (Exception e) { q = new JSONArray(); }
        q.put(res);

        String pan = res.optString("pan", "");
        String last4 = pan.length() >= 4 ? pan.substring(pan.length() - 4) : "";
        double bal = res.optDouble("balance");
        long at = res.optLong("readAt");

        JSONArray cards;
        try { cards = new JSONArray(p.getString("cards", "[]")); } catch (Exception e) { cards = new JSONArray(); }
        JSONObject card = null;
        for (int k = 0; k < cards.length(); k++) {
            JSONObject o = cards.optJSONObject(k);
            if (o != null && !last4.isEmpty() && last4.equals(o.optString("last4"))) { card = o; break; }
        }
        String extra = "";
        if (card == null) {
            card = new JSONObject();
            card.put("name", res.optString("cardType", "Travel card"));
            card.put("last4", last4);
            cards.put(card);
        } else if (!card.isNull("balance") && card.has("balance")) {
            double d = Math.round((card.optDouble("balance") - bal) * 100) / 100.0;
            if (d > 0.004) extra = " · ₹" + Parse.fmt(d) + " spent";
            else if (d < -0.004) extra = " · topped up ₹" + Parse.fmt(-d);
        }
        card.put("balance", bal);
        card.put("readAt", at);

        p.edit().putString("cardq", q.toString()).putString("cards", cards.toString()).apply();
        KharchaWidget.refresh(c);
        return card.optString("name", "Card") + " · ₹" + Parse.fmt(bal) + extra;
    }

    /** Reads made while the app was closed, handed to the web app once. */
    static synchronized String take(Context c) {
        SharedPreferences p = c.getSharedPreferences("k", MODE_PRIVATE);
        String s = p.getString("cardq", "[]");
        p.edit().putString("cardq", "[]").apply();
        return s;
    }
}
