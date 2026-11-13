package com.dhbhojani.kharcha;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** The card that slides up over the payment app right after a payment, to tag it. */
public class Popup {
    static final String DEFAULT_CATS = "[{\"id\":\"food\",\"name\":\"Food\",\"emoji\":\"🍔\"},{\"id\":\"groc\",\"name\":\"Groceries\",\"emoji\":\"🛒\"},{\"id\":\"travel\",\"name\":\"Travel\",\"emoji\":\"🚕\"},{\"id\":\"shop\",\"name\":\"Shopping\",\"emoji\":\"🛍️\"},{\"id\":\"bills\",\"name\":\"Bills\",\"emoji\":\"💡\"},{\"id\":\"study\",\"name\":\"Study\",\"emoji\":\"📚\"},{\"id\":\"health\",\"name\":\"Health\",\"emoji\":\"💊\"},{\"id\":\"fun\",\"name\":\"Fun\",\"emoji\":\"🎬\"},{\"id\":\"friends\",\"name\":\"Friends\",\"emoji\":\"🤝\"},{\"id\":\"home\",\"name\":\"Home\",\"emoji\":\"🏠\"},{\"id\":\"gift\",\"name\":\"Gifts\",\"emoji\":\"🎁\"},{\"id\":\"other\",\"name\":\"Other\",\"emoji\":\"📦\"}]";

    // theme values, matching the web app
    static boolean dark;
    static int CARD, INK, MUT, FAINT, SOFT, ACCENT, ONACCENT, REDBG, RED, OKINK, FIELD, MARK;

    interface OnLater { void later(); }

    static void theme(Context c) {
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            CARD = 0xFF141A18; INK = 0xFFEEF3F1; MUT = 0xFF9AA6A1; FAINT = 0xFF7D8884;
            SOFT = 0xFF1A211F; ACCENT = 0xFF5EEAB4; ONACCENT = 0xFF0E1513; REDBG = 0xFF3A1A17; RED = 0xFFFF8B7E;
            OKINK = 0xFF6FD9C4; FIELD = 0xFF1A211F; MARK = 0xFF22302B;
        } else {
            CARD = 0xFFFFFFFF; INK = 0xFF0E1513; MUT = 0xFF5B6461; FAINT = 0xFF8A9490;
            SOFT = 0xFFF6F8F7; ACCENT = 0xFF0E1513; ONACCENT = 0xFFFFFFFF; REDBG = 0xFFFDECEA; RED = 0xFFB42318;
            OKINK = 0xFF0B6B47; FIELD = 0xFFF3F5F4; MARK = 0xFF0E1513;
        }
    }

    /** per-category tile background, light and dark */
    static int tileBg(String id) {
        switch (id) {
            case "food":    return dark ? 0xFF3A2616 : 0xFFFFE8D6;
            case "groc":    return dark ? 0xFF1C2E14 : 0xFFE3F5D8;
            case "travel":  return dark ? 0xFF18283F : 0xFFDCEBFF;
            case "shop":    return dark ? 0xFF3A1428 : 0xFFFDE2F0;
            case "bills":   return dark ? 0xFF352A08 : 0xFFFFF4CC;
            case "study":   return dark ? 0xFF241D45 : 0xFFE8E4FF;
            case "health":  return dark ? 0xFF3A1715 : 0xFFFFE3E3;
            case "fun":     return dark ? 0xFF1B2047 : 0xFFE6E9FF;
            case "friends": return dark ? 0xFF14302A : 0xFFE0F2EF;
            case "home":    return dark ? 0xFF2C241C : 0xFFEFE9E2;
            case "gift":    return dark ? 0xFF3A1521 : 0xFFFFE1EA;
            default:        return dark ? 0xFF232A2F : 0xFFECEEF0;
        }
    }

    static View showing;
    static EditText curAmt;

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable box(Context c, int fill, int stroke, float r, float sw) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, r));
        if (stroke != 0) g.setStroke(dp(c, sw), stroke);
        return g;
    }

    static GradientDrawable topRounded(Context c, int fill, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        float p = dp(c, r);
        g.setCornerRadii(new float[]{p, p, p, p, 0, 0, 0, 0});
        return g;
    }

    static TextView label(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static void show(Context c, String text, long ts, OnLater onLater, int winType) {
        show(c, text, ts, onLater, winType, false, null);
    }

    /** Called whenever the pop-up goes away (used by the widget's cash screen to close itself). */
    static Runnable onClosed;

    /** cash = opened from the home-screen widget: no payment behind it, so ask for amount + where, saved as Cash. */
    static void show(Context c, String text, long ts, OnLater onLater, int winType, boolean cash, Runnable closed) {
        theme(c);
        Runnable prev = onClosed;
        onClosed = null;
        if (prev != null) prev.run();
        onClosed = closed;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (showing != null) {
            try { wm.removeView(showing); } catch (Exception ignored) { }
            showing = null;
        }
        SharedPreferences p = c.getSharedPreferences("k", Context.MODE_PRIVATE);
        JSONArray cats;
        JSONObject rules;
        try { cats = new JSONArray(p.getString("cats", DEFAULT_CATS)); } catch (Exception e) { cats = new JSONArray(); }
        if (cats.length() == 0) try { cats = new JSONArray(DEFAULT_CATS); } catch (Exception ignored) { }
        try { rules = new JSONObject(p.getString("rules", "{}")); } catch (Exception e) { rules = new JSONObject(); }

        double amt = Parse.amount(text);
        String payee = Parse.payee(text);
        final String remembered = rules.optString(Parse.key(payee), "");
        final String[] sel = {remembered};

        FrameLayout root = new FrameLayout(c);
        root.setPadding(dp(c, 8), 0, dp(c, 8), 0);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16));
        card.setBackground(topRounded(c, CARD, 28));
        card.setElevation(dp(c, 16));
        root.addView(card);

        /* header: brand chip + "Not an expense" */
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label(c, "₹", 12, 0xFF5EEAB4, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(box(c, MARK, 0, 7, 0));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
        mlp.rightMargin = dp(c, 8);
        head.addView(mark, mlp);
        head.addView(label(c, cash ? "Kharcha · cash spent" : "Kharcha · just paid", 12.5f, MUT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView ignore = label(c, cash ? "Cancel" : "Not an expense", 12, RED, true);
        ignore.setBackground(box(c, REDBG, 0, 10, 0));
        ignore.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
        head.addView(ignore);
        card.addView(head);

        /* amount + payee side by side */
        LinearLayout mid = new LinearLayout(c);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams midlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        midlp.topMargin = dp(c, 12);
        card.addView(mid, midlp);

        TextView rs = label(c, "₹", 20, FAINT, true);
        rs.setPadding(0, 0, dp(c, 2), dp(c, 3));
        mid.addView(rs);

        EditText amtE = new EditText(c);
        amtE.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amtE.setText(amt > 0 ? Parse.fmt(amt) : "");
        amtE.setHint("0");
        amtE.setTextSize(34);
        amtE.setTypeface(Typeface.DEFAULT_BOLD);
        amtE.setTextColor(INK);
        amtE.setHintTextColor(FAINT);
        amtE.setBackground(null);
        amtE.setPadding(0, 0, dp(c, 10), 0);
        amtE.setMinWidth(dp(c, 54));
        mid.addView(amtE);
        curAmt = amtE;

        LinearLayout pbox = new LinearLayout(c);
        pbox.setOrientation(LinearLayout.VERTICAL);
        pbox.setBackground(box(c, FIELD, 0, 13, 0));
        pbox.setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7));
        pbox.addView(label(c, cash ? "Spent at" : "Paid to", 10.5f, MUT, false));
        EditText payeeE = new EditText(c);
        payeeE.setText(payee);
        payeeE.setHint(cash ? "Where?" : "Who?");
        payeeE.setTextSize(14);
        payeeE.setSingleLine(true);
        payeeE.setTypeface(Typeface.DEFAULT_BOLD);
        payeeE.setTextColor(INK);
        payeeE.setHintTextColor(FAINT);
        payeeE.setBackground(null);
        payeeE.setPadding(0, 0, 0, 0);
        pbox.addView(payeeE);
        mid.addView(pbox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        /* category grid */
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(c, 12);
        card.addView(grid, glp);

        final TextView[] chips = new TextView[cats.length()];
        final TextView hint = label(c, "", 11.5f, OKINK, false);
        for (int i = 0; i < cats.length(); i++) {
            JSONObject o = cats.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("id");
            TextView t = new TextView(c);
            t.setText(o.optString("emoji", "🏷️") + "\n" + o.optString("name"));
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setMaxLines(2);
            t.setTextColor(INK);
            t.setPadding(dp(c, 2), dp(c, 7), dp(c, 2), dp(c, 7));
            t.setTag(id);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
            chips[i] = t;
            t.setOnClickListener(v -> {
                sel[0] = id;
                hint.setText("");
                for (TextView x : chips) if (x != null) paint(c, x, id.equals(x.getTag()));
            });
            paint(c, t, id.equals(sel[0]));
            grid.addView(t, lp);
        }
        if (!remembered.isEmpty()) {
            String nm = remembered;
            for (int i = 0; i < cats.length(); i++) {
                JSONObject o = cats.optJSONObject(i);
                if (o != null && remembered.equals(o.optString("id"))) nm = o.optString("name");
            }
            hint.setText(nm + " picked automatically, like last time");
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(c, 7);
            hlp.leftMargin = dp(c, 3);
            card.addView(hint, hlp);
        }

        EditText note = new EditText(c);
        note.setHint("Add a note (optional)");
        note.setTextSize(14);
        note.setSingleLine(true);
        note.setTextColor(INK);
        note.setHintTextColor(FAINT);
        note.setBackground(box(c, FIELD, 0, 13, 0));
        note.setPadding(dp(c, 12), dp(c, 11), dp(c, 12), dp(c, 11));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(c, 9);
        card.addView(note, nlp);

        /* Later + Save */
        LinearLayout btns = new LinearLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(c, 10);
        card.addView(btns, blp);
        TextView later = button(c, "Later", false);
        TextView save = button(c, "Save", true);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(c, 50), 1);
        l1.rightMargin = dp(c, 8);
        if (!cash) btns.addView(later, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, dp(c, 50), 2));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | (cash ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        if (cash) amtE.requestFocus();

        ignore.setOnClickListener(v -> {
            close(wm, root);
            if (ts >= 0 && !cash) PayListener.remove(c, ts);
            if (!cash) Toast.makeText(c, "Skipped", Toast.LENGTH_SHORT).show();
        });
        later.setOnClickListener(v -> {
            close(wm, root);
            if (onLater != null) onLater.later();
        });
        save.setOnClickListener(v -> {
            double a = Parse.num(amtE.getText().toString());
            if (a <= 0) { Toast.makeText(c, "Enter the amount", Toast.LENGTH_SHORT).show(); return; }
            if (sel[0].isEmpty()) { Toast.makeText(c, "Tap a category", Toast.LENGTH_SHORT).show(); return; }
            if (ts < 0) { close(wm, root); Toast.makeText(c, "Pop-up works ✓ (test not saved)", Toast.LENGTH_SHORT).show(); return; }
            String py = payeeE.getText().toString().trim();
            PayListener.markDone(c, ts, a, py, sel[0], note.getText().toString().trim(), cash ? "Cash" : "UPI");
            try {
                JSONObject r = new JSONObject(p.getString("rules", "{}"));
                if (!py.isEmpty()) r.put(Parse.key(py), sel[0]);
                p.edit().putString("rules", r.toString()).apply();
            } catch (Exception ignored) { }
            close(wm, root);
            Toast.makeText(c, "Saved ✓ ₹" + Parse.fmt(a), Toast.LENGTH_SHORT).show();
        });

        wm.addView(root, lp);
        showing = root;
    }

    static boolean setAmount(double a) {
        if (showing == null || curAmt == null || a <= 0) return false;
        if (Parse.num(curAmt.getText().toString()) <= 0) curAmt.setText(Parse.fmt(a));
        return true;
    }

    static void close(WindowManager wm, View v) {
        try { wm.removeView(v); } catch (Exception ignored) { }
        if (showing == v) { showing = null; curAmt = null; }
        Runnable r = onClosed;
        onClosed = null;
        if (r != null) r.run();
    }

    static void paint(Context c, TextView t, boolean on) {
        String id = String.valueOf(t.getTag());
        t.setBackground(on ? box(c, tileBg(id), ACCENT, 15, 2) : box(c, tileBg(id), 0, 15, 0));
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    static TextView button(Context c, String s, boolean pri) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(pri ? ONACCENT : INK);
        b.setBackground(box(c, pri ? ACCENT : SOFT, 0, 16, 0));
        return b;
    }
}
