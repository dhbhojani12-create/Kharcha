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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the SBI Prepaid site inside Kharcha. The user logs in themselves.
 *
 * Capture mode: records which screens the site shows (field names and button labels, never
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(14));
        bar.setBackgroundColor(Color.parseColor("#0E1513"));
        copyB = btn("Copy for Claude (0)", "#5EEAB4", "#0E1513");
        Button done = btn("Close", "#1E2A26", "#FFFFFF");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(0, 0, dp(8), 0);
        bar.addView(copyB, lp);
        bar.addView(done, new LinearLayout.LayoutParams(dp(96), dp(48)));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bar, bp);
        web.setPadding(0, 0, 0, 0);
        setContentView(root);
        bar.post(() -> {
            FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) web.getLayoutParams();
            wp.bottomMargin = bar.getHeight();
            web.setLayoutParams(wp);
        });

        copyB.setOnClickListener(v -> copy());
        done.setOnClickListener(v -> finish());

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
                    Toast.makeText(SbiActivity.this, "No app found to open this", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                v.evaluateJavascript(HOOK, null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(HOOK, null);
            }
        });
        web.loadUrl(SITE);
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
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
