package com.dhbhojani.kharcha;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the SBI Prepaid site inside Kharcha. The user logs in themselves.
 *
 * Top-up mode (amount passed in): fills the saved login, then the amount, and clicks through
 * SBIePay to the UPI app. The user always types the captcha and OTP and approves in UPI.
 *
 * Capture: also records which screens the site shows (field names and button labels, never
 * what is typed) and the data it loads, with passwords, OTPs, tokens and long numbers masked,
 * so a simpler top-up screen can be built on top of it. The capture stays on the phone until
 * the user taps Copy.
 */
public class SbiActivity extends Activity {
    static final String SITE = "https://prepaid.sbi.bank.in/web/#/login";
    static final int MAX = 300;

    WebView web;
    Button copyB;
    final List<String> cap = new ArrayList<>();

    /** Runs inside the site. Hooks its data calls and notes each screen's fields. */
    static final String HOOK = "(function(){if(window.__kc)return;window.__kc=1;" +
            "var SECRET=/pass|pwd|otp|token|auth|session|cookie|mpin|\\bpin\\b|cvv|secret|captcha|key|sign/i;" +
            "function mask(v,k){if(k&&SECRET.test(k))return'***';" +
            " if(typeof v==='string')return v.replace(/\\d{10,}/g,function(m){return m.slice(0,4)+'..'+m.slice(-4)}).replace(/[A-Za-z0-9+\\/=_\\-.]{40,}/g,'[long]');" +
            " if(Array.isArray(v))return v.slice(0,15).map(function(x){return mask(x)});" +
            " if(v&&typeof v==='object'){var o={};for(var kk in v)o[kk]=mask(v[kk],kk);return o}" +
            " if(typeof v==='number'&&String(v).length>=10)return'[num]';return v}" +
            "function body(t){if(t==null)return null;try{return mask(JSON.parse(t))}catch(e){return typeof t==='string'?mask(t.slice(0,500)):'[binary]'}}" +
            "function rec(o){try{var s=JSON.stringify(o);if(s.length>5000)s=s.slice(0,5000)+'...';KharchaCap.add(s)}catch(e){}}" +
            "function path(u){return String(u).split('?')[0]}" +
            "var O=XMLHttpRequest.prototype.open,S=XMLHttpRequest.prototype.send;" +
            "XMLHttpRequest.prototype.open=function(m,u){this.__m=m;this.__u=u;return O.apply(this,arguments)};" +
            "XMLHttpRequest.prototype.send=function(b){var x=this;x.addEventListener('loadend',function(){var r='';try{r=x.responseText}catch(e){}" +
            " rec({k:'xhr',at:location.hash,m:x.__m,u:path(x.__u),st:x.status,req:body(b),res:body(r)})});return S.apply(this,arguments)};" +
            "var F=window.fetch;if(F)window.fetch=function(i,init){var u=typeof i==='string'?i:i.url;return F.apply(this,arguments).then(function(r){" +
            " try{r.clone().text().then(function(t){rec({k:'fetch',at:location.hash,m:(init&&init.method)||'GET',u:path(u),st:r.status,req:body(init&&init.body),res:body(t)})})}catch(e){}return r})};" +
            "var last='';function snap(){var q=document.querySelectorAll('input,select,textarea,button,a,label,h1,h2,h3,h4,[role=button],[role=tab],mat-select,mat-radio-button');" +
            " var els=[];for(var i=0;i<q.length&&els.length<120;i++){var e=q[i];if(!e.offsetParent)continue;var t=e.tagName.toLowerCase();" +
            "  var txt=(e.placeholder||e.getAttribute('aria-label')||(t==='input'||t==='textarea'?'':(e.innerText||'')).trim()).slice(0,60);" +
            "  var nm=e.name||e.getAttribute('formcontrolname')||e.id||'';" +
            "  els.push(mask([t,e.type||'',nm,txt.replace(/\\s+/g,' ')].filter(function(x){return x}).join(' | ')))}" +
            " var s=location.hash+els.join();if(s!==last){last=s;rec({k:'screen',at:location.hash,title:document.title,els:els})}}" +
            "setInterval(snap,1500);snap();})();";

    // top-up mode
    double amt;
    String last4, label;
    final Set<String> done = new HashSet<>();
    String driver = "";
    volatile String host = "";
    String[] pendingLogin;
    boolean askedSave;

    LinearLayout strip;
    TextView titleV, statusV;
    View[] seg = new View[3];
    Button actB;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        amt = in.getDoubleExtra("amt", 0);
        last4 = in.getStringExtra("last4");
        label = in.getStringExtra("label");
        boolean assist = amt > 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        if (assist) {
            strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.VERTICAL);
            strip.setBackgroundColor(Color.parseColor("#0E1513"));
            strip.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            titleV = new TextView(this);
            titleV.setTextColor(Color.WHITE);
            titleV.setTextSize(16);
            titleV.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            titleV.setText((label != null && !label.isEmpty() ? label + " · " : "") + "₹" + fmt(amt) + " top-up");
            row.addView(titleV, new LinearLayout.LayoutParams(0, -2, 1));
            Button cancel = btn("Cancel", "#1E2A26", "#A9B8B2");
            cancel.setTextSize(13);
            cancel.setOnClickListener(v -> finish());
            row.addView(cancel, new LinearLayout.LayoutParams(dp(84), dp(38)));
            strip.addView(row);

            LinearLayout bars = new LinearLayout(this);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(4));
            blp.setMargins(0, dp(10), 0, dp(8));
            for (int i = 0; i < 3; i++) {
                seg[i] = new View(this);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(4), 1);
                if (i < 2) sp.setMargins(0, 0, dp(6), 0);
                bars.addView(seg[i], sp);
            }
            strip.addView(bars, blp);
            statusV = new TextView(this);
            statusV.setTextColor(Color.parseColor("#A9B8B2"));
            statusV.setTextSize(13.5f);
            strip.addView(statusV);
            // long-press the status to copy the masked capture (for fixing problems)
            statusV.setOnLongClickListener(v -> { copy(); return true; });
            actB = btn("", "#5EEAB4", "#0E1513");
            actB.setVisibility(View.GONE);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(44));
            alp.setMargins(0, dp(10), 0, 0);
            strip.addView(actB, alp);
            root.addView(strip, new LinearLayout.LayoutParams(-1, -2));
            setStep("Log in to SBI Prepaid", "1");
            try (InputStream is = getAssets().open("sbi_driver.js")) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                driver = bo.toString("UTF-8");
            } catch (Exception e) { driver = ""; }
        }

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        copyB = btn("Copy for Claude (0)", "#5EEAB4", "#0E1513");
        if (!assist) {
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(dp(12), dp(10), dp(12), dp(14));
            bar.setBackgroundColor(Color.parseColor("#0E1513"));
            Button close = btn("Close", "#1E2A26", "#FFFFFF");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(0, 0, dp(8), 0);
            bar.addView(copyB, lp);
            bar.addView(close, new LinearLayout.LayoutParams(dp(96), dp(48)));
            copyB.setOnClickListener(v -> copy());
            close.setOnClickListener(v -> finish());
            root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        }
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Cap(), "KharchaCap");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                // UPI apps and other payment intents from the payment page
                try {
                    Intent i = "intent".equals(sc) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            : new Intent(Intent.ACTION_VIEW, u);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    i.setComponent(null);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(SbiActivity.this, "No UPI app found to open this", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                host = hostOf(url);
                v.evaluateJavascript(HOOK, null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                host = hostOf(url);
                v.evaluateJavascript(HOOK, null);
                if (amt > 0 && !driver.isEmpty()) v.evaluateJavascript(params() + driver, null);
            }
        });
        web.loadUrl(SITE);
    }

    static String hostOf(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? "" : h; } catch (Exception e) { return ""; }
    }

    static String fmt(double a) {
        return a == Math.floor(a) ? String.valueOf((long) a) : String.format(java.util.Locale.US, "%.2f", a);
    }

    String params() {
        StringBuilder d = new StringBuilder("{");
        synchronized (done) { for (String k : done) d.append(d.length() > 1 ? "," : "").append('"').append(k).append("\":1"); }
        d.append('}');
        return "window.__KP={amt:" + fmt(amt) + ",last4:" + org.json.JSONObject.quote(last4 == null ? "" : last4) + ",done:" + d + "};";
    }

    void setStep(String text, String step) {
        statusV.setText(text);
        int n = "3".equals(step) ? 3 : "2".equals(step) || "card".equals(step) ? 2 : "ok".equals(step) ? 3 : 1;
        for (int i = 0; i < 3; i++)
            seg[i].setBackgroundColor(Color.parseColor(i < n ? "#5EEAB4" : "#2C3A35"));
        if ("card".equals(step)) {
            actB.setText("It's the right card – continue");
            actB.setVisibility(View.VISIBLE);
            actB.setOnClickListener(v -> {
                synchronized (done) { done.add("cardok"); }
                actB.setVisibility(View.GONE);
                web.evaluateJavascript("if(window.__KP){window.__KP.done.cardok=1;location.hash='#/app/management/topUp'}", null);
            });
        } else if ("ok".equals(step)) {
            actB.setText("Done");
            actB.setVisibility(View.VISIBLE);
            actB.setOnClickListener(v -> finish());
        } else if ("fail".equals(step)) {
            actB.setText("Close");
            actB.setVisibility(View.VISIBLE);
            actB.setOnClickListener(v -> finish());
        } else {
            actB.setVisibility(View.GONE);
        }
    }

    boolean onSbi() { return host.endsWith("prepaid.sbi.bank.in"); }

    void askSave() {
        if (askedSave || pendingLogin == null || SbiLogin.has(this)) return;
        askedSave = true;
        final String[] l = pendingLogin;
        pendingLogin = null;
        new AlertDialog.Builder(this)
                .setTitle("Save SBI login in Kharcha?")
                .setMessage("Your User ID and password stay on this phone, encrypted. Kharcha fills them next time; you still type the captcha and OTP.")
                .setPositiveButton("Save", (d, w) -> {
                    if (SbiLogin.save(this, l[0], l[1])) Toast.makeText(this, "Login saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    Button btn(String text, String bg, String fg) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextColor(Color.parseColor(fg));
        bt.setTextSize(15);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bg));
        d.setCornerRadius(dp(16));
        bt.setBackground(d);
        bt.setStateListAnimator(null);
        return bt;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    void copy() {
        StringBuilder sb = new StringBuilder("{\"sbiCapture\":1,\"items\":[\n");
        synchronized (cap) {
            for (int i = 0; i < cap.size(); i++) sb.append(i > 0 ? ",\n" : "").append(cap.get(i));
        }
        sb.append("\n]}");
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Kharcha SBI capture", sb.toString()));
        Toast.makeText(this, "Copied – paste it to Claude", Toast.LENGTH_SHORT).show();
    }

    class Cap {
        @JavascriptInterface
        public void add(String json) {
            synchronized (cap) {
                if (cap.size() >= MAX) cap.remove(0);
                cap.add(json);
            }
            runOnUiThread(() -> copyB.setText("Copy for Claude (" + cap.size() + ")"));
        }

        /** the driver did a step; remembered across page loads so nothing is clicked twice */
        @JavascriptInterface
        public void did(String k) { synchronized (done) { done.add(k); } }

        @JavascriptInterface
        public void status(String text, String step) {
            if (amt > 0) runOnUiThread(() -> setStep(text, step));
        }

        /** saved login, only ever handed to the SBI Prepaid page itself */
        @JavascriptInterface
        public String creds() {
            if (!onSbi()) return null;
            String[] l = SbiLogin.load(SbiActivity.this);
            if (l == null) return null;
            try {
                return new org.json.JSONObject().put("u", l[0]).put("p", l[1]).toString();
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public void offerLogin(String u, String p) {
            if (!onSbi() || SbiLogin.has(SbiActivity.this)) return;
            pendingLogin = new String[]{u, p};
        }

        /** the OTP page appeared, so the ID and password were right: offer to save them */
        @JavascriptInterface
        public void reached(String where) {
            if ("otp".equals(where)) runOnUiThread(() -> askSave());
        }

        @JavascriptInterface
        public void keyboard() {
            runOnUiThread(() -> {
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                web.requestFocus();
                im.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
            });
        }

        @JavascriptInterface
        public void finished(String result) {
            if (amt <= 0) return;
            runOnUiThread(() -> {
                if ("ok".equals(result))
                    setStep("✓ Top-up of ₹" + fmt(amt) + " paid. Tap the card at a metro gate to load it onto the chip.", "ok");
                else
                    setStep("The payment didn't go through. Nothing more will be clicked.", "fail");
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
