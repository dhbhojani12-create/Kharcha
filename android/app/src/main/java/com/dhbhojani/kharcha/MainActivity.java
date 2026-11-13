package com.dhbhojani.kharcha;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    static final String HOME = "https://dhbhojani12-create.github.io/Kharcha/";

    WebView web;
    ValueCallback<Uri[]> fileCb;
    Uri cameraUri;
    PermissionRequest pendingPerm;
    NfcAdapter nfc;
    boolean nfcOn;

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
                String host = u.getHost();
                if (host != null && host.endsWith("github.io")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    toast("No app found to open this");
                }
                return true;
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
                    if (p.isCaptureEnabled()) {
                        File dir = new File(getCacheDir(), "shared");
                        dir.mkdirs();
                        File f = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
                        cameraUri = FilesProvider.uriFor(f);
                        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(i, 4);
                    } else {
                        startActivityForResult(p.createIntent(), 2);
                    }
                } catch (Exception e) {
                    fileCb = null;
                    toast("Could not open picker");
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
        web.loadUrl(HOME + action(getIntent()));
    }

    static String action(Intent i) {
        String a = i == null ? null : i.getStringExtra("a");
        return a == null || a.isEmpty() ? "" : "?a=" + a;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String a = action(i);
        if (!a.isEmpty() && web != null) web.loadUrl(HOME + a);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.kharchaPoll&&kharchaPoll()", null);
        if (nfcOn) nfcStart();
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
        if (fileCb != null && code == 2) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCb = null;
            return;
        }
        if (fileCb != null && code == 4) {
            fileCb.onReceiveValue(result == RESULT_OK && cameraUri != null ? new Uri[]{cameraUri} : null);
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

    /** Start listening for a tapped transit card; results go to the page via kharchaCard(). */
    void nfcStart() {
        if (nfc == null) nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) { toJs("{\"error\":\"This phone has no NFC\"}"); return; }
        if (!nfc.isEnabled()) { toJs("{\"error\":\"NFC is switched off – turn it on in Settings\"}"); return; }
        nfcOn = true;
        nfc.enableReaderMode(this, tag -> {
            IsoDep iso = IsoDep.get(tag);
            if (iso == null) { toJs("{\"error\":\"That card is not a payment / NCMC card\"}"); return; }
            try {
                toJs(NcmcCard.read(iso).toString());
            } catch (Exception e) {
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("error", m);
                    toJs(o.toString());
                } catch (Exception ignored) { }
            }
        }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
    }

    void nfcStop() {
        nfcOn = false;
        try { if (nfc != null) nfc.disableReaderMode(this); } catch (Exception ignored) { }
    }

    void toJs(String json) {
        final String js = "window.kharchaCard && kharchaCard(" + json + ")";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcOn) try { if (nfc != null) nfc.disableReaderMode(this); } catch (Exception ignored) { }
    }

    void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    File writeCache(String name, String b64) throws Exception {
        File dir = new File(getCacheDir(), "shared");
        dir.mkdirs();
        String safe = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        File f = new File(dir, safe);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(Base64.decode(b64, Base64.DEFAULT));
        }
        return f;
    }

    class Bridge {
        @JavascriptInterface
        public String version() {
            return "7";
        }

        @JavascriptInterface
        public boolean hasAccessibility() {
            String f = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return f != null && f.contains(getPackageName());
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
        }

        @JavascriptInterface
        public void openAppInfo() {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
        }

        @JavascriptInterface
        public String takePending() {
            return PayListener.take(MainActivity.this);
        }

        @JavascriptInterface
        public String getLog() {
            return getSharedPreferences("k", MODE_PRIVATE).getString("log", "[]");
        }

        @JavascriptInterface
        public void setCats(String json) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putString("cats", json).apply();
        }

        @JavascriptInterface
        public void setRules(String json) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putString("rules", json).apply();
        }

        /** compact list of travel cards for the home-screen widget: [{name,last4,balance,readAt}] */
        @JavascriptInterface
        public void setCards(String json) {
            getSharedPreferences("k", MODE_PRIVATE).edit().putString("cards", json).apply();
            KharchaWidget.refresh(MainActivity.this);
        }

        @JavascriptInterface
        public boolean hasNfc() {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(MainActivity.this);
            return a != null && a.isEnabled();
        }

        @JavascriptInterface
        public void readCard() {
            runOnUiThread(() -> nfcStart());
        }

        @JavascriptInterface
        public void sbiTopup(String amt, String last4, String label) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, SbiActivity.class);
                try { i.putExtra("amt", Double.parseDouble(amt)); } catch (Exception e) { return; }
                i.putExtra("last4", last4);
                i.putExtra("label", label);
                startActivity(i);
            });
        }

        @JavascriptInterface
        public boolean sbiHasLogin() { return SbiLogin.has(MainActivity.this); }

        @JavascriptInterface
        public void sbiForget() { SbiLogin.forget(MainActivity.this); }

        @JavascriptInterface
        public void openSbi() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, SbiActivity.class)));
        }

        @JavascriptInterface
        public void stopCard() {
            runOnUiThread(() -> nfcStop());
        }

        @JavascriptInterface
        public boolean hasApp(String pkg) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Save to the phone's Downloads folder. */
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

        /** Send a file to another app (e.g. PoolDrive). Saved in Downloads first, then shared from there. */
        @JavascriptInterface
        public boolean shareFile(String name, String b64, String mime, String pkg) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Kharcha");
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (u == null) { toast("Could not prepare file"); return false; }
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    o.write(Base64.decode(b64, Base64.DEFAULT));
                }
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.putExtra(Intent.EXTRA_TITLE, name);
                i.setClipData(android.content.ClipData.newRawUri(name, u));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                Intent go = i;
                if (pkg != null && !pkg.isEmpty() && hasApp(pkg)) i.setPackage(pkg);
                else go = Intent.createChooser(i, "Send backup to").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                final Intent run = go;
                runOnUiThread(() -> {
                    try { startActivity(run); } catch (Exception e) { toast("Could not open the app"); }
                });
                toast("Also saved in Downloads/Kharcha");
                return true;
            } catch (Exception e) {
                toast("Could not prepare file");
                return false;
            }
        }

        /** Open a document (PDF etc.) in the phone's viewer. */
        @JavascriptInterface
        public boolean openFile(String name, String b64, String mime) {
            try {
                File f = writeCache(name, b64);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(FilesProvider.uriFor(f), mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                runOnUiThread(() -> {
                    try { startActivity(i); } catch (Exception e) { toast("No app to open this file"); }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
