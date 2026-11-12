package com.dhbhojani.kharcha;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small floating card shown over any app right after a payment. */
public class Popup {
    static final String DEFAULT_CATS = "[{\"id\":\"food\",\"name\":\"Food\",\"emoji\":\"🍔\"},{\"id\":\"groc\",\"name\":\"Groceries\",\"emoji\":\"🛒\"},{\"id\":\"travel\",\"name\":\"Travel\",\"emoji\":\"🚕\"},{\"id\":\"shop\",\"name\":\"Shopping\",\"emoji\":\"🛍️\"},{\"id\":\"bills\",\"name\":\"Bills\",\"emoji\":\"💡\"},{\"id\":\"study\",\"name\":\"Study\",\"emoji\":\"📚\"},{\"id\":\"health\",\"name\":\"Health\",\"emoji\":\"💊\"},{\"id\":\"fun\",\"name\":\"Fun\",\"emoji\":\"🎬\"},{\"id\":\"friends\",\"name\":\"Friends\",\"emoji\":\"🤝\"},{\"id\":\"home\",\"name\":\"Home\",\"emoji\":\"🏠\"},{\"id\":\"gift\",\"name\":\"Gifts\",\"emoji\":\"🎁\"},{\"id\":\"other\",\"name\":\"Other\",\"emoji\":\"📦\"}]";
    static final int TEAL = Color.parseColor("#0F766E");
    static View showing;
    static EditText curAmt;

    interface OnLater { void later(); }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable box(int fill, int stroke, float r, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, r));
        if (stroke != 0) g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    static void show(Context c, String text, long ts, OnLater onLater) {
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
        final String[] sel = {rules.optString(Parse.key(payee), "")};

        FrameLayout root = new FrameLayout(c);
        root.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 14));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        card.setBackground(box(Color.WHITE, 0, 22, c));
        card.setElevation(dp(c, 14));
        root.addView(card);

        TextView head = new TextView(c);
        head.setText("💸 Kharcha · just paid");
        head.setTextColor(Color.parseColor("#64748B"));
        head.setTextSize(12);
        card.addView(head);

        LinearLayout amtRow = new LinearLayout(c);
        amtRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView rs = new TextView(c);
        rs.setText("₹");
        rs.setTextSize(26);
        rs.setTextColor(Color.parseColor("#64748B"));
        amtRow.addView(rs);
        EditText amtE = new EditText(c);
        amtE.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amtE.setText(amt > 0 ? Parse.fmt(amt) : "");
        amtE.setHint("0");
        amtE.setTextSize(28);
        amtE.setTypeface(Typeface.DEFAULT_BOLD);
        amtE.setTextColor(Color.parseColor("#0F172A"));
        amtE.setBackground(null);
        amtE.setPadding(dp(c, 4), 0, 0, 0);
        amtRow.addView(amtE, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        curAmt = amtE;
        card.addView(amtRow);

        EditText payeeE = new EditText(c);
        payeeE.setText(payee);
        payeeE.setHint("Paid to");
        payeeE.setTextSize(15);
        payeeE.setSingleLine(true);
        payeeE.setTextColor(Color.parseColor("#0F172A"));
        payeeE.setBackground(box(Color.parseColor("#F4F6F8"), 0, 12, c));
        payeeE.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        card.addView(payeeE);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(c, 10);
        card.addView(grid, glp);
        final TextView[] chips = new TextView[cats.length()];
        for (int i = 0; i < cats.length(); i++) {
            JSONObject o = cats.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("id");
            TextView t = new TextView(c);
            t.setText(o.optString("emoji", "🏷️") + "\n" + o.optString("name"));
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setMaxLines(2);
            t.setTextColor(Color.parseColor("#0F172A"));
            t.setPadding(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 6));
            t.setTag(id);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
            chips[i] = t;
            t.setOnClickListener(v -> {
                sel[0] = id;
                for (TextView x : chips) if (x != null) paint(c, x, id.equals(x.getTag()));
            });
            paint(c, t, id.equals(sel[0]));
            grid.addView(t, lp);
        }

        EditText note = new EditText(c);
        note.setHint("Note (optional)");
        note.setTextSize(15);
        note.setSingleLine(true);
        note.setTextColor(Color.parseColor("#0F172A"));
        note.setBackground(box(Color.parseColor("#F4F6F8"), 0, 12, c));
        note.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(c, 8);
        card.addView(note, nlp);

        LinearLayout btns = new LinearLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(c, 10);
        card.addView(btns, blp);
        TextView later = button(c, "Later", false);
        TextView save = button(c, "Save", true);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(c, 46), 1);
        l1.rightMargin = dp(c, 8);
        btns.addView(later, l1);
        btns.addView(save, new LinearLayout.LayoutParams(0, dp(c, 46), 2));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

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
            PayListener.markDone(c, ts, a, py, sel[0], note.getText().toString().trim());
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
    }

    static void paint(Context c, TextView t, boolean on) {
        t.setBackground(box(on ? Color.parseColor("#CCFBF1") : Color.WHITE, on ? TEAL : Color.parseColor("#E5E9F0"), 12, c));
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    static TextView button(Context c, String s, boolean pri) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(pri ? Color.WHITE : Color.parseColor("#0F172A"));
        b.setBackground(box(pri ? TEAL : Color.parseColor("#E5E9F0"), 0, 14, c));
        return b;
    }
}
