package com.dhbhojani.kharcha;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads amount and payee from a payment notification / bank SMS (same rules as the web app). */
public class Parse {
    static final Pattern AMT1 = Pattern.compile("(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    static final Pattern AMT2 = Pattern.compile("(?:debited|paid|sent|spent|transferred)\\s+(?:by|for|of|with)?\\s*(?:₹|rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    static final Pattern PAYEE = Pattern.compile(
            "(?:paid to|sent to|payment to|to)\\s*[:\\n]?\\s*([A-Za-z][A-Za-z0-9 .&'@-]{1,40}?)"
                    + "(?=\\s+(?:on|via|using|from|ref|refno|ref\\.?no|re-?f\\.?\\s*no|upi|successfully|is|was|for|with|a/c|if|not|avl|bal)\\b|\\s+re-|\\s+\\d{6,}|[.,!?\\n]|$)",
            Pattern.CASE_INSENSITIVE);

    static double amount(String t) {
        if (t == null) return 0;
        Matcher m = AMT1.matcher(t);
        double a = m.find() ? num(m.group(1)) : 0;
        if (a <= 0) {
            m = AMT2.matcher(t);
            if (m.find()) a = num(m.group(1));
        }
        return a;
    }

    static String payee(String t) {
        if (t == null) return "";
        Matcher m = PAYEE.matcher(t);
        return m.find() ? m.group(1).trim() : "";
    }

    static double num(String s) {
        try {
            return Math.round(Double.parseDouble(s.replace(",", "")) * 100) / 100.0;
        } catch (Exception e) {
            return 0;
        }
    }

    static String key(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9@.]", "");
    }

    static String fmt(double a) {
        return a == Math.floor(a) ? String.valueOf((long) a) : String.format(java.util.Locale.US, "%.2f", a);
    }
}
