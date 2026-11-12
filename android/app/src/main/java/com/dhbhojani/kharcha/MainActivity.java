package com.dhbhojani.kharcha;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    static final String HOME = "https://dhbhojani12-create.github.io/Kharcha/";

    WebView web;
    ValueCallback<Uri[]> fileCb;
    PermissionRequest pendingPerm;
    String pendingImage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(online() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        web.addJavascriptInterface(new Bridge(), "KharchaApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme();
                String host = u.getHost();
                if (("https".equals(sc) || "http".equals(sc)) && host != null && host.endsWith("github.io")) return false;
                try {
                    Intent i = "intent".equals(sc)
                            ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            : new Intent(Intent.ACTION_VIEW, u);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    toast("No app found to open this");
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (pendingImage != null) {
                    String js = "setTimeout(function(){window.kharchaImage&&kharchaImage('" + pendingImage + "')},300)";
                    pendingImage = null;
                    v.evaluateJavascript(js, null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(req.getResources());
                    } else {
                        pendingPerm = req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), 2);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
        handle(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.kharchaPoll&&kharchaPoll()", null);
        try {
            String f = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (f != null && f.contains(getPackageName()))
                NotificationListenerService.requestRebind(new ComponentName(this, PayListener.class));
        } catch (Exception ignored) { }
    }

    void handle(Intent i, boolean first) {
        String url = HOME;
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String type = i.getType() == null ? "" : i.getType();
            if (type.startsWith("image/")) {
                Uri img = i.getParcelableExtra(Intent.EXTRA_STREAM);
                pendingImage = readDataUrl(img);
            } else {
                String t = i.getStringExtra(Intent.EXTRA_TEXT);
                if (t != null) url = HOME + "?a=log&t=" + Uri.encode(t);
            }
        }
        if (first || !url.equals(HOME) || pendingImage != null) web.loadUrl(url);
    }

    String readDataUrl(Uri u) {
        if (u == null) return null;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            Bitmap bm = BitmapFactory.decodeStream(in);
            if (bm == null) return null;
            int w = bm.getWidth(), h = bm.getHeight();
            if (w > 1080) {
                bm = Bitmap.createScaledBitmap(bm, 1080, (int) ((long) h * 1080 / w), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 1 && pendingPerm != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) pendingPerm.grant(pendingPerm.getResources());
            else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == 2 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(code, result, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){var o=document.getElementById('ov');if(o&&!o.classList.contains('hidden')){closeSheet();return 1}return 0})()",
                v -> { if ("0".equals(v)) finish(); });
    }

    boolean online() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    class Bridge {
        @JavascriptInterface
        public boolean openApp(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
            return true;
        }

        @JavascriptInterface
        public boolean openUpi(String url, String pkg) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                runOnUiThread(() -> {
                    try { startActivity(i); } catch (Exception e) { toast("App not found"); }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean hasNotifAccess() {
            String f = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return f != null && f.contains(getPackageName());
        }

        @JavascriptInterface
        public void openNotifSettings() {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
        }

        @JavascriptInterface
        public String takePending() {
            return PayListener.take(MainActivity.this);
        }

        @JavascriptInterface
        public void setReadSms(boolean on) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putBoolean("sms", on).apply();
        }

        @JavascriptInterface
        public boolean getReadSms() {
            return getSharedPreferences("k", MODE_PRIVATE).getBoolean("sms", false);
        }

        @JavascriptInterface
        public boolean saveFile(String name, String b64, String mime) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (u == null) return false;
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    o.write(Base64.decode(b64, Base64.DEFAULT));
                }
                toast("Saved to Downloads: " + name);
                return true;
            } catch (Exception e) {
                toast("Could not save file");
                return false;
            }
        }

        @JavascriptInterface
        public String version() {
            return "3";
        }

        @JavascriptInterface
        public boolean canOverlay() {
            return Settings.canDrawOverlays(MainActivity.this);
        }

        @JavascriptInterface
        public void openOverlaySettings() {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
        }

        @JavascriptInterface
        public void setCats(String json) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putString("cats", json).apply();
        }

        @JavascriptInterface
        public void setRules(String json) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putString("rules", json).apply();
        }

        @JavascriptInterface
        public void testPopup() {
            runOnUiThread(() -> {
                if (!Settings.canDrawOverlays(MainActivity.this)) { toast("Turn on 'Appear on top' first"); return; }
                Popup.show(MainActivity.this, "Test: You paid ₹10 to Test Shop", -1, null);
            });
        }

        @JavascriptInterface
        public String getLog() {
            return getSharedPreferences("k", MODE_PRIVATE).getString("log", "[]");
        }

        @JavascriptInterface
        public long listenerConnectedAt() {
            return getSharedPreferences("k", MODE_PRIVATE).getLong("conn", 0);
        }

        @JavascriptInterface
        public void openAppInfo() {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
        }
    }
}
