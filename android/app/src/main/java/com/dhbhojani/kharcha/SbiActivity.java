package com.dhbhojani.kharcha;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SBI Prepaid inside Kharcha. The user's own login; Kharcha never solves the captcha.
 *
 * Top-up mode (amount passed in): the site runs out of sight behind a Kharcha screen. The user
 * sees only the captcha image (typed by them), the OTP (typed, or filled from SBI's SMS after
 * they tap Allow in Android's one-message consent popup) and then their UPI app's payment popup.
 * "Show SBI page" reveals the real site at any time.
 *
 * Capture mode (no amount): the plain site plus a masked capture for debugging.
 */
public class SbiActivity extends Activity {
    static final String SITE = "https://prepaid.sbi.bank.in/web/#/login";
    static final int MAX = 300;
    static final int REQ_SMS = 71;

    WebView web;
    Button copyB;
    final List<String> cap = new ArrayList<>();

    /** Runs inside the site. Notes each screen's fields and the site's data calls, masked. */
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
    String state = "";
    boolean pageShown, smsOn, finishedOk;

    // views
    FrameLayout stage;
    LinearLayout panel;
    TextView titleV, subV, errV, pageB;
    ImageView capImg;
    LinearLayout capRow, loginBox, stepsBox;
    EditText userE, passE, codeE;
    CheckBox rememberC;
    Button mainB, altB;
    ProgressBar spin;
    View[] seg = new View[3];
    TextView[] stepMark = new TextView[3];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        amt = in.getDoubleExtra("amt", 0);
        last4 = in.getStringExtra("last4");
        label = in.getStringExtra("label");

        web = new WebView(this);
        copyB = btn("Copy for Claude (0)", "#5EEAB4", "#0E1513");
        if (amt > 0) buildTopup(); else buildCapture();

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
            public void onPageStarted(WebView v, String url, Bitmap f) {
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

    /* ---------------- capture mode ---------------- */
    void buildCapture() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
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
        setContentView(root);
    }

    /* ---------------- top-up mode ---------------- */
    void buildTopup() {
        try (InputStream is = getAssets().open("sbi_driver.js")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            driver = bo.toString("UTF-8");
        } catch (Exception e) { driver = ""; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F3F5F2"));

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(Color.parseColor("#0E1513"));
        head.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView cardV = text((label != null && !label.isEmpty() ? label : "Card") + (last4 != null && !last4.isEmpty() ? " · ···· " + last4 : ""), 13, "#A9B8B2", true);
        TextView amtV = text("₹" + fmt(amt) + " top-up", 28, "#FFFFFF", true);
        tcol.addView(cardV);
        tcol.addView(amtV);
        row.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = btn("Cancel", "#1E2A26", "#A9B8B2");
        cancel.setTextSize(13);
        cancel.setOnClickListener(v -> finish());
        row.addView(cancel, new LinearLayout.LayoutParams(dp(88), dp(40)));
        head.addView(row);
        LinearLayout bars = new LinearLayout(this);
        for (int i = 0; i < 3; i++) {
            seg[i] = new View(this);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(4), 1);
            if (i < 2) sp.setMargins(0, 0, dp(6), 0);
            bars.addView(seg[i], sp);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(4));
        blp.setMargins(0, dp(12), 0, 0);
        head.addView(bars, blp);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // stage: the site underneath, Kharcha's panel on top
        stage = new FrameLayout(this);
        stage.addView(web, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor("#F3F5F2"));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(22), dp(20), dp(16));
        sv.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        titleV = text("", 19, "#0E1513", true);
        panel.addView(titleV);
        subV = text("", 13.5f, "#5B6461", false);
        panel.addView(subV, mtop(4));

        spin = new ProgressBar(this);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(dp(44), dp(44));
        spl.gravity = Gravity.CENTER_HORIZONTAL;
        spl.setMargins(0, dp(24), 0, dp(8));
        panel.addView(spin, spl);

        loginBox = new LinearLayout(this);
        loginBox.setOrientation(LinearLayout.VERTICAL);
        userE = field("SBI Prepaid User ID", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        passE = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        rememberC = new CheckBox(this);
        rememberC.setText("Remember on this phone (encrypted)");
        rememberC.setChecked(true);
        loginBox.addView(userE, mtop(12));
        loginBox.addView(passE, mtop(10));
        loginBox.addView(rememberC, mtop(6));
        panel.addView(loginBox);

        capRow = new LinearLayout(this);
        capRow.setGravity(Gravity.CENTER);
        capRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        capRow.setBackground(round("#FFFFFF", 18));
        capImg = new ImageView(this);
        capImg.setAdjustViewBounds(true);
        capImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        capRow.addView(capImg, new LinearLayout.LayoutParams(0, dp(72), 1));
        Button again = btn("↻", "#F6F8F7", "#0E1513");
        again.setTextSize(20);
        again.setContentDescription("New captcha");
        again.setOnClickListener(v -> newCaptcha());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
        alp.setMargins(dp(10), 0, 0, 0);
        capRow.addView(again, alp);
        panel.addView(capRow, mtop(14));

        codeE = field("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeE.setTextSize(24);
        codeE.setLetterSpacing(0.25f);
        codeE.setGravity(Gravity.CENTER);
        codeE.setTypeface(Typeface.DEFAULT_BOLD);
        codeE.setImeOptions(EditorInfo.IME_ACTION_DONE);
        codeE.setOnEditorActionListener((v, a, e) -> { mainB.performClick(); return true; });
        codeE.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) {
                if ("otp".equals(state) && s.toString().replaceAll("\\D", "").length() == 6) mainB.performClick();
            }
        });
        panel.addView(codeE, mtop(12));

        errV = text("", 13.5f, "#B42318", true);
        panel.addView(errV, mtop(10));

        mainB = btn("Continue", "#0E1513", "#FFFFFF");
        mainB.setTextSize(16);
        panel.addView(mainB, sized(-1, 54, 14));
        altB = btn("", "#FFFFFF", "#0E1513");
        panel.addView(altB, sized(-1, 48, 10));

        stepsBox = new LinearLayout(this);
        stepsBox.setOrientation(LinearLayout.VERTICAL);
        stepsBox.setBackground(round("#FFFFFF", 18));
        stepsBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        String[] names = {"Login", "OTP", "Top-up and SBIePay"};
        for (int i = 0; i < 3; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, dp(4), 0, dp(4));
            stepMark[i] = text("", 14, "#0B6B47", true);
            r.addView(stepMark[i], new LinearLayout.LayoutParams(dp(26), -2));
            r.addView(text(names[i], 13.5f, "#0E1513", false));
            stepsBox.addView(r);
        }
        panel.addView(stepsBox, mtop(18));

        View gap = new View(this);
        panel.addView(gap, new LinearLayout.LayoutParams(-1, 0, 1));
        pageB = text("Something stuck? Show the SBI page", 13, "#5B6461", false);
        pageB.setGravity(Gravity.CENTER);
        pageB.setPadding(0, dp(14), 0, dp(6));
        pageB.setPaintFlags(pageB.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        pageB.setOnClickListener(v -> showPage(true));
        pageB.setOnLongClickListener(v -> { copy(); return true; }); // masked log for fixing problems
        panel.addView(pageB);

        // shown on top of the site when it is visible
        Button backB = btn("Back to Kharcha view", "#0E1513", "#FFFFFF");
        backB.setTag("back");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = dp(18);
        backB.setPadding(dp(18), 0, dp(18), 0);
        backB.setOnClickListener(v -> showPage(false));
        backB.setVisibility(View.GONE);
        stage.addView(backB, bp);

        setContentView(root);
        show("connecting", null);
    }

    void showPage(boolean on) {
        pageShown = on;
        ((View) panel.getParent()).setVisibility(on ? View.GONE : View.VISIBLE);
        stage.findViewWithTag("back").setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) hideKeyboard();
    }

    void steps(int doneN, int active) {
        for (int i = 0; i < 3; i++) {
            seg[i].setBackgroundColor(Color.parseColor(i < active || i < doneN ? "#5EEAB4" : "#2C3A35"));
            stepMark[i].setText(i < doneN ? "✓" : i == active - 1 ? "…" : "");
        }
    }

    /** One screen at a time: connecting, login, captcha, checking, otp, working, card, paying, done, fail. */
    void show(String st, String msg) {
        state = st;
        spin.setVisibility(View.GONE);
        loginBox.setVisibility(View.GONE);
        capRow.setVisibility(View.GONE);
        codeE.setVisibility(View.GONE);
        mainB.setVisibility(View.GONE);
        altB.setVisibility(View.GONE);
        errV.setVisibility(View.GONE);
        subV.setVisibility(View.VISIBLE);
        switch (st) {
            case "connecting":
                titleV.setText("Connecting to SBI…");
                subV.setText("Getting the login page ready.");
                spin.setVisibility(View.VISIBLE);
                steps(0, 1);
                break;
            case "captcha": {
                boolean saved = SbiLogin.has(this);
                titleV.setText("Type the captcha");
                subV.setText(saved ? "Your login is filled. Letters are case-sensitive." : "First time: enter your SBI Prepaid login and the captcha.");
                loginBox.setVisibility(saved ? View.GONE : View.VISIBLE);
                capRow.setVisibility(View.VISIBLE);
                codeE.setVisibility(View.VISIBLE);
                codeE.setHint("Captcha");
                codeE.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                codeE.setText("");
                mainB.setText("Continue");
                mainB.setVisibility(View.VISIBLE);
                mainB.setOnClickListener(v -> submitLogin());
                if (msg != null) { errV.setText(msg); errV.setVisibility(View.VISIBLE); }
                steps(0, 1);
                focus(saved ? codeE : userE);
                break;
            }
            case "checking":
                titleV.setText("Checking…");
                subV.setText("SBI is sending an OTP to your mobile.");
                spin.setVisibility(View.VISIBLE);
                steps(0, 1);
                break;
            case "otp":
                titleV.setText("Enter the OTP");
                subV.setText("Sent by SBI to your mobile. Tap Allow when Android asks, and Kharcha fills it.");
                codeE.setVisibility(View.VISIBLE);
                codeE.setHint("6-digit OTP");
                codeE.setInputType(InputType.TYPE_CLASS_NUMBER);
                codeE.setText("");
                mainB.setText("Verify");
                mainB.setVisibility(View.VISIBLE);
                mainB.setOnClickListener(v -> submitOtp());
                if (msg != null) { errV.setText(msg); errV.setVisibility(View.VISIBLE); }
                steps(1, 2);
                focus(codeE);
                break;
            case "working":
                titleV.setText(msg == null ? "Working…" : msg);
                subV.setText("Kharcha is filling the top-up on SBI. Nothing to do yet.");
                spin.setVisibility(View.VISIBLE);
                steps(2, 3);
                hideKeyboard();
                break;
            case "card":
                titleV.setText("Which card?");
                subV.setText(msg);
                mainB.setText("Show the SBI page to switch");
                mainB.setVisibility(View.VISIBLE);
                mainB.setOnClickListener(v -> showPage(true));
                altB.setText("It's the right card – continue");
                altB.setVisibility(View.VISIBLE);
                altB.setOnClickListener(v -> {
                    synchronized (done) { done.add("cardok"); }
                    web.evaluateJavascript("if(window.__KP){window.__KP.done.cardok=1;location.hash='#/app/management/topUp'}", null);
                    show("working", "Opening top-up…");
                });
                steps(2, 3);
                break;
            case "paying":
                titleV.setText("Opening your payment app…");
                subV.setText("Approve ₹" + fmt(amt) + " there, then come back here.");
                spin.setVisibility(View.VISIBLE);
                steps(2, 3);
                hideKeyboard();
                break;
            case "done":
                titleV.setText("✓ ₹" + fmt(amt) + " paid");
                subV.setText("Tap the card at a metro gate to load it onto the chip.");
                mainB.setText("Done");
                mainB.setVisibility(View.VISIBLE);
                mainB.setOnClickListener(v -> finish());
                steps(3, 3);
                break;
            case "fail":
                titleV.setText("The payment didn't go through");
                subV.setText("Nothing more will be clicked. Check the SBI page or try again.");
                mainB.setText("Close");
                mainB.setVisibility(View.VISIBLE);
                mainB.setOnClickListener(v -> finish());
                altB.setText("Show the SBI page");
                altB.setVisibility(View.VISIBLE);
                altB.setOnClickListener(v -> showPage(true));
                steps(2, 3);
                break;
        }
    }

    void submitLogin() {
        String c = codeE.getText().toString().trim();
        String u = null, p = null;
        if (!SbiLogin.has(this)) {
            u = userE.getText().toString().trim();
            p = passE.getText().toString();
            if (u.isEmpty() || p.isEmpty()) { err("Enter your User ID and password"); return; }
            if (rememberC.isChecked()) SbiLogin.save(this, u, p);
        }
        if (c.isEmpty()) { err("Type the captcha"); return; }
        startSms();
        web.evaluateJavascript("window.__kSubmitLogin&&window.__kSubmitLogin(" + JSONObject.quote(u == null ? "" : u) + ","
                + JSONObject.quote(p == null ? "" : p) + "," + JSONObject.quote(c) + ")", null);
        show("checking", null);
    }

    void submitOtp() {
        String c = codeE.getText().toString().replaceAll("\\D", "");
        if (c.length() != 6) { err("The OTP has 6 digits"); return; }
        web.evaluateJavascript("window.__kSubmitOtp&&window.__kSubmitOtp(" + JSONObject.quote(c) + ")", null);
        show("working", "Logging in…");
        steps(1, 2);
    }

    void newCaptcha() {
        show("connecting", null);
        web.loadUrl(SITE);
    }

    void err(String m) { errV.setText(m); errV.setVisibility(View.VISIBLE); }

    void focus(View v) {
        if (pageShown) return;
        v.requestFocus();
        v.postDelayed(() -> {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            im.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (f != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    /* ---------------- OTP from SMS, with the user's one-message consent ---------------- */
    final BroadcastReceiver smsRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(i.getAction())) return;
            Bundle x = i.getExtras();
            if (x == null) return;
            Status st = (Status) x.get(SmsRetriever.EXTRA_STATUS);
            if (st == null || st.getStatusCode() != CommonStatusCodes.SUCCESS) return;
            Intent consent = x.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
            if (consent == null) return;
            try { startActivityForResult(consent, REQ_SMS); } catch (Exception ignored) { }
        }
    };

    void startSms() {
        try {
            SmsRetriever.getClient(this).startSmsUserConsent(null);
            if (!smsOn) {
                IntentFilter f = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
                if (Build.VERSION.SDK_INT >= 33)
                    registerReceiver(smsRx, f, SmsRetriever.SEND_PERMISSION, null, Context.RECEIVER_EXPORTED);
                else
                    registerReceiver(smsRx, f, SmsRetriever.SEND_PERMISSION, null);
                smsOn = true;
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SMS || res != RESULT_OK || data == null) return;
        String msg = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
        if (msg == null) return;
        Matcher m = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(msg);
        if (!m.find()) return;
        String otp = m.group(1);
        if ("otp".equals(state)) codeE.setText(otp); // the watcher submits it
        else pendingOtp = otp;
    }

    String pendingOtp;

    @Override
    protected void onDestroy() {
        if (smsOn) try { unregisterReceiver(smsRx); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (amt > 0 && pageShown) { showPage(false); return; }
        if (amt <= 0 && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }

    /* ---------------- helpers ---------------- */
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
        return "window.__KP={amt:" + fmt(amt) + ",last4:" + JSONObject.quote(last4 == null ? "" : last4) + ",done:" + d + ",bg:1};";
    }

    boolean onSbi() { return host.endsWith("prepaid.sbi.bank.in"); }

    TextView text(String s, float size, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setTextColor(Color.parseColor("#0E1513"));
        e.setPadding(dp(16), 0, dp(16), 0);
        e.setMinHeight(dp(54));
        GradientDrawable d = round("#FFFFFF", 16);
        d.setStroke(dp(2), Color.parseColor("#E2E6E3"));
        e.setBackground(d);
        return e;
    }

    GradientDrawable round(String color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(r));
        return d;
    }

    LinearLayout.LayoutParams mtop(int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(m), 0, 0);
        return p;
    }

    LinearLayout.LayoutParams sized(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, dp(h));
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    Button btn(String text, String bg, String fg) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextColor(Color.parseColor(fg));
        bt.setTextSize(15);
        bt.setBackground(round(bg, 16));
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
        Toast.makeText(this, "Log copied – paste it to Claude", Toast.LENGTH_SHORT).show();
    }

    class Cap {
        @JavascriptInterface
        public void add(String json) {
            synchronized (cap) {
                if (cap.size() >= MAX) cap.remove(0);
                cap.add(json);
            }
            if (amt <= 0) runOnUiThread(() -> copyB.setText("Copy for Claude (" + cap.size() + ")"));
        }

        /** the driver did a step; remembered across page loads so nothing is clicked twice */
        @JavascriptInterface
        public void did(String k) { synchronized (done) { done.add(k); } }

        /** progress from the driver once past the OTP */
        @JavascriptInterface
        public void status(String text, String step) {
            if (amt <= 0) return;
            runOnUiThread(() -> {
                if ("card".equals(step)) { if (!"card".equals(state)) show("card", text); }
                else if ("3".equals(step)) { if (!"paying".equals(state)) show("paying", null); }
                else if ("2".equals(step)) {
                    if ("working".equals(state)) titleV.setText(text);
                    else if (!"done".equals(state) && !"fail".equals(state)) show("working", text);
                }
            });
        }

        /** the login page's captcha image, as a data: URL */
        @JavascriptInterface
        public void captcha(String dataUrl, String error) {
            if (amt <= 0) return;
            try {
                String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                byte[] raw = Base64.decode(b64, Base64.DEFAULT);
                Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                runOnUiThread(() -> {
                    String keep = "captcha".equals(state) && !SbiLogin.has(SbiActivity.this) ? userE.getText().toString() : null;
                    show("captcha", error == null || error.isEmpty() ? null : error);
                    if (keep != null) userE.setText(keep);
                    capImg.setImageBitmap(bm);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showPage(true));
            }
        }

        @JavascriptInterface
        public void otpPage(String error) {
            if (amt <= 0) return;
            runOnUiThread(() -> {
                show("otp", error == null || error.isEmpty() ? null : error);
                if (pendingOtp != null) { codeE.setText(pendingOtp); pendingOtp = null; }
            });
        }

        /** a page Kharcha can't handle by itself: let the user see it */
        @JavascriptInterface
        public void needUser(String why) {
            if (amt <= 0) return;
            runOnUiThread(() -> {
                Toast.makeText(SbiActivity.this, why, Toast.LENGTH_LONG).show();
                showPage(true);
            });
        }

        /** saved login, only ever handed to the SBI Prepaid page itself */
        @JavascriptInterface
        public String creds() {
            if (!onSbi()) return null;
            String[] l = SbiLogin.load(SbiActivity.this);
            if (l == null) return null;
            try {
                return new JSONObject().put("u", l[0]).put("p", l[1]).toString();
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public void offerLogin(String u, String p) { }

        @JavascriptInterface
        public void reached(String where) { }

        @JavascriptInterface
        public void keyboard() { }

        @JavascriptInterface
        public void finished(String result) {
            if (amt <= 0) return;
            runOnUiThread(() -> {
                finishedOk = "ok".equals(result);
                show(finishedOk ? "done" : "fail", null);
                if (pageShown) showPage(false);
            });
        }
    }
}
