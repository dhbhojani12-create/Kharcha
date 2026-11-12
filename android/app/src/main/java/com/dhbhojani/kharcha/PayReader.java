package com.dhbhojani.kharcha;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the payment app's success screen (only the UPI apps listed in res/xml/kharcha_access.xml)
 * and pops up the tagging card instantly.
 */
public class PayReader extends AccessibilityService {

    static final Pattern SUCCESS = Pattern.compile(
            "(transaction status|^paid to$|^sent to$|^paid to |payment successful|paid successfully|transaction successful|money sent|sent successfully|payment complete|payment done|transfer successful|^sent$|^paid$|^success$|^successful$)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    static final Pattern BAD = Pattern.compile(
            "\\b(failed|pending|declined|processing|enter amount|enter upi pin|insufficient|cancelled|canceled)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern DATETIME = Pattern.compile("\\d{1,2}\\s+[A-Za-z]{3,9},?\\s+\\d{4},?\\s+\\d{1,2}:\\d{2}(\\s*[AaPp][Mm])?");
    static final Pattern AMT = Pattern.compile("₹\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z .&'-]{2,45}$");
    static final Pattern BANKISH = Pattern.compile("\\b(bank|banking|ltd|limited|upi lite|wallet|account|a/c|savings)\\b", Pattern.CASE_INSENSITIVE);
    static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "sent", "paid", "transaction status", "transaction successful", "payment successful", "successful",
            "success", "completed", "complete", "done", "share", "share receipt", "view details", "back", "more",
            "close", "banking name", "upi transaction id", "upi ref no", "to", "from", "debited from", "credited to",
            "check balance", "pay again", "split", "split bill", "transaction details", "payment complete",
            "powered by upi", "google pay", "phonepe", "paytm", "samsung wallet", "bhim", "contact support", "help",
            "note", "message", "amount", "rewards", "cashback", "scratch card", "view history", "bank account", "via",
            "receipt", "powered by", "powered by upi", "navigate up", "transaction id", "upi ref", "reference", "details", "payment details", "transaction details", "view receipt", "share screenshot", "report a problem", "need help", "pay", "back to home", "continue", "go back", "ok", "home", "paid to", "sent to", "money sent", "payment done",
            "verified", "verified merchant", "upi id", "your bank", "view balance", "rate us", "download", "copy"));

    static String lastSig = "";
    static boolean armed = true;
    static long lastSigTs = 0;
    long lastScan = 0;

    static double waitAmt = 0;
    static long waitSince = 0;
    final Runnable rescan = this::scan;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastScan < 300) return;
        lastScan = now;
        scan();
    }

    void scan() {
        try {
            long now = System.currentTimeMillis();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (pkg.equals(getPackageName())) return;

            List<String> lines = new ArrayList<>();
            collect(root, lines, 0);
            String all = String.join("\n", lines);
            if (!SUCCESS.matcher(all).find() || BAD.matcher(all).find()) {
                // left the success screen → ready for the next payment
                if (!lines.isEmpty()) armed = true;
                return;
            }

            Matcher m = AMT.matcher(all);
            int count = 0;
            double amt = 0;
            while (m.find()) {
                double a = Parse.num(m.group(1));
                if (a > 0) {
                    if (count == 0) amt = a;
                    count++;
                }
            }
            if (amt <= 0 || count > 3) return;   // history lists have many amounts – ignore them

            String payee = findPayee(lines);
            boolean hasUpiId = all.contains("@");
            // The name card animates in after "₹ Sent" – wait up to 2.5 s for it.
            if (payee.isEmpty() || !hasUpiId) {
                if (Math.abs(amt - waitAmt) > 0.001 || now - waitSince > 8000) { waitAmt = amt; waitSince = now; }
                if (now - waitSince < 2500) {
                    Capture.H.removeCallbacks(rescan);
                    Capture.H.postDelayed(rescan, 400);
                    return;
                }
            }
            Capture.H.removeCallbacks(rescan);
            Matcher dt = DATETIME.matcher(all);
            String when = dt.find() ? dt.group() : "";
            String sig = Parse.fmt(amt) + "|" + Parse.key(payee) + "|" + when;
            if (sig.equals(lastSig) && (!armed || now - lastSigTs < 20 * 1000)) return;
            armed = false;
            lastSig = sig;
            lastSigTs = now;

            String txt = "You paid ₹" + Parse.fmt(amt) + (payee.isEmpty() ? "" : " to " + payee) + ".";
            String raw = String.join(" | ", lines);
            PayListener.log(this, pkg, "Screen: ₹" + Parse.fmt(amt) + " → " + (payee.isEmpty() ? "?" : payee) + "\nRAW: " + (raw.length() > 600 ? raw.substring(0, 600) : raw), "CAPTURED (screen)");
            Capture.lastScreenTs = now;
            Capture.go(this, txt, now, true);
        } catch (Exception ignored) {
        }
    }

    static void collect(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || depth > 45 || out.size() > 400) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null) {
            // drop icons/invisible symbols (e.g. the verified tick after the name)
            String s = t.toString().replaceAll("[^\\p{L}\\p{N}₹ .,:&'@*/()-]", " ").replaceAll("\\s+", " ").trim();
            if (!s.isEmpty()) out.add(s);
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    static boolean isName(String l) {
        String s = l.trim();
        if (!NAME.matcher(s).matches()) return false;
        if (STOP.contains(s.toLowerCase())) return false;
        if (s.replaceAll("[^A-Za-z]", "").length() < 3) return false;
        if (BANKISH.matcher(s).find()) return false;
        if (s.toLowerCase().startsWith("powered")) return false;
        return s.split("\\s+").length <= 6;
    }

    static String findPayee(List<String> lines) {
        int n = lines.size();
        int amtLine = -1;
        for (int i = 0; i < n; i++) if (lines.get(i).contains("₹")) { amtLine = i; break; }
        String best = "";
        int bestScore = -1;
        for (int i = 0; i < n; i++) {
            String l = lines.get(i).trim();
            String low = l.toLowerCase();
            String cand = l;
            int score = 0;
            if ((low.startsWith("paid to ") || low.startsWith("sent to ")) && l.length() > 8) { cand = l.substring(8).trim(); score += 4; }
            if (!isName(cand)) continue;
            // name sits right above the UPI ID (******0050@hdfc)
            for (int j = i + 1; j <= Math.min(n - 1, i + 2); j++) if (lines.get(j).contains("@")) { score += 5; break; }
            if (i > 0) {
                String prev = lines.get(i - 1).trim().toLowerCase();
                if (prev.equals("to") || prev.equals("paid to") || prev.equals("sent to")) score += 4;
            }
            String letters = cand.replaceAll("[^A-Za-z]", "");
            if (letters.equals(letters.toUpperCase()) && cand.trim().split("\\s+").length >= 2) score += 2;
            if (amtLine >= 0 && i > amtLine && i <= amtLine + 6) score += 1;
            if (score > bestScore) { bestScore = score; best = cand.trim(); }
        }
        return best;
    }

    @Override
    public void onInterrupt() {
    }
}
