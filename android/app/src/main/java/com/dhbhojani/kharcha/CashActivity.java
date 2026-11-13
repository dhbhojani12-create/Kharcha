package com.dhbhojani.kharcha;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** Opened from the home-screen widget's "+ Cash" button: just the Kharcha pop-up, no app behind it. */
public class CashActivity extends Activity {
    android.view.View mine;
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.35f);
        getWindow().getDecorView().post(() -> {
            try {
                Popup.show(this, "", System.currentTimeMillis(), null,
                        WindowManager.LayoutParams.TYPE_APPLICATION, true, this::finish);
                mine = Popup.showing;
            } catch (Exception e) {
                finish();
            }
        });
        // tapping the dimmed area above the card closes it
        getWindow().getDecorView().setOnClickListener(v -> finishPopup());
    }

    void finishPopup() {
        if (mine != null && Popup.showing == mine) Popup.close(getWindowManager(), Popup.showing);
        else finish();
    }

    @Override
    public void onBackPressed() { finishPopup(); }

    @Override
    protected void onDestroy() {
        if (mine != null && Popup.showing == mine) {
            Popup.onClosed = null;
            try { getWindowManager().removeViewImmediate(Popup.showing); } catch (Exception ignored) { }
            Popup.showing = null;
            Popup.curAmt = null;
        }
        super.onDestroy();
    }
}
