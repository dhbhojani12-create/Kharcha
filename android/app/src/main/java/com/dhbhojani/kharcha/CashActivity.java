package com.dhbhojani.kharcha;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Opened from the home-screen widget.
 * "+ Cash": just the Kharcha pop-up. Mic: listen first, then the same pop-up filled in from what was said.
 */
public class CashActivity extends Activity {
    View mine;
    SpeechRecognizer sr;
    TextView status, said;
    boolean gotResult;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.35f);
        // tapping the dimmed area above the card closes it
        getWindow().getDecorView().setOnClickListener(v -> finishPopup());

        if (getIntent().getBooleanExtra("voice", false)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen();
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 5);
        } else {
            showPopup();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) listen();
        else {
            Toast.makeText(this, "Mic permission is needed to add by voice", Toast.LENGTH_SHORT).show();
            showPopup();
        }
    }

    void showPopup() {
        getWindow().getDecorView().post(() -> {
            try {
                Popup.show(this, "", System.currentTimeMillis(), null,
                        WindowManager.LayoutParams.TYPE_APPLICATION, true, this::finish);
                mine = Popup.showing;
            } catch (Exception e) {
                finish();
            }
        });
    }

    /* ---------- voice ---------- */

    void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice typing isn't available on this phone", Toast.LENGTH_SHORT).show();
            showPopup();
            return;
        }
        buildSheet();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { status.setText("Listening…"); }
            public void onBeginningOfSpeech() { }
            public void onRmsChanged(float v) { }
            public void onBufferReceived(byte[] buf) { }
            public void onEndOfSpeech() { status.setText("Got it…"); }
            public void onError(int e) {
                if (gotResult) return;
                Toast.makeText(CashActivity.this,
                        e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                ? "Didn't catch that – type it instead" : "Voice didn't work – type it instead",
                        Toast.LENGTH_SHORT).show();
                done("");
            }
            public void onResults(Bundle r) {
                gotResult = true;
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                done(l == null || l.isEmpty() ? "" : l.get(0));
            }
            public void onPartialResults(Bundle r) {
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && !l.isEmpty() && !l.get(0).isEmpty()) {
                    said.setText("“" + l.get(0) + "”");
                    said.setVisibility(View.VISIBLE);
                }
            }
            public void onEvent(int t, Bundle p) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        sr.startListening(i);
    }

    /** speech finished: fill the cash pop-up and show it */
    void done(String text) {
        stopListening();
        setContentView(new FrameLayout(this));
        if (!text.isEmpty()) {
            Voice v = Voice.parse(text, getSharedPreferences("k", MODE_PRIVATE).getString("cats", Popup.DEFAULT_CATS));
            Popup.preAmt = v.amt;
            Popup.prePayee = v.what;
            Popup.preCat = v.cat;
            Popup.preHeard = text;
        }
        showPopup();
    }

    void stopListening() {
        if (sr != null) {
            try { sr.cancel(); sr.destroy(); } catch (Exception ignored) { }
            sr = null;
        }
    }

    /** the "Listening…" card at the bottom, in Kharcha's style */
    void buildSheet() {
        Popup.theme(this);
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finishPopup());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Popup.topRounded(this, Popup.CARD, 28));
        card.setPadding(Popup.dp(this, 18), Popup.dp(this, 26), Popup.dp(this, 18), Popup.dp(this, 34));
        card.setClickable(true);

        TextView mic = new TextView(this);
        mic.setText("🎤");
        mic.setTextSize(30);
        mic.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ring.setColor(0xFF0F766E);
        ring.setStroke(Popup.dp(this, 10), Popup.dark ? 0xFF1F3A35 : 0xFFD7F0EB);
        mic.setBackground(ring);
        int sz = Popup.dp(this, 96);
        card.addView(mic, new LinearLayout.LayoutParams(sz, sz));

        status = Popup.label(this, "Starting…", 17, Popup.INK, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = Popup.dp(this, 14);
        card.addView(status, sp);

        TextView hint = Popup.label(this, "Say the amount, what it was, and a category", 13, Popup.MUT, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = Popup.dp(this, 6);
        card.addView(hint, hp);

        said = Popup.label(this, "", 15, Popup.INK, false);
        said.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        said.setGravity(Gravity.CENTER);
        said.setBackground(Popup.box(this, Popup.FIELD, 0, 14, 0));
        said.setPadding(Popup.dp(this, 12), Popup.dp(this, 10), Popup.dp(this, 12), Popup.dp(this, 10));
        said.setVisibility(View.GONE);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = Popup.dp(this, 14);
        card.addView(said, dp);

        TextView type = Popup.label(this, "Type instead", 13.5f, Popup.MUT, true);
        type.setPadding(Popup.dp(this, 12), Popup.dp(this, 10), Popup.dp(this, 12), Popup.dp(this, 4));
        type.setOnClickListener(v -> { gotResult = true; done(""); });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Popup.dp(this, 8);
        card.addView(type, tp);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        cp.leftMargin = cp.rightMargin = Popup.dp(this, 8);
        root.addView(card, cp);
        setContentView(root);
    }

    /* ---------- closing ---------- */

    void finishPopup() {
        if (mine != null && Popup.showing == mine) Popup.close(getWindowManager(), Popup.showing);
        else finish();
    }

    @Override
    public void onBackPressed() { finishPopup(); }

    @Override
    protected void onDestroy() {
        stopListening();
        if (mine != null && Popup.showing == mine) {
            Popup.onClosed = null;
            try { getWindowManager().removeViewImmediate(Popup.showing); } catch (Exception ignored) { }
            Popup.showing = null;
            Popup.curAmt = null;
        }
        super.onDestroy();
    }
}
