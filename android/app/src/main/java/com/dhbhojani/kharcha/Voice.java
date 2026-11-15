package com.dhbhojani.kharcha;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns "120 chai and samosa, food" into amount 120, "Chai and samosa", category food. */
public class Voice {
    static final String[][] WORDS = {
        {"food", "chai", "tea", "coffee", "samosa", "vada", "pav", "lunch", "dinner", "breakfast", "snack", "snacks", "dosa", "idli", "juice", "zomato", "swiggy", "khana", "nashta", "canteen", "biryani", "pizza", "burger", "sandwich", "maggi", "lassi", "thali", "restaurant", "hotel", "cafe"},
        {"travel", "auto", "rickshaw", "taxi", "cab", "uber", "ola", "rapido", "bus", "train", "metro", "ticket", "petrol", "fuel", "parking", "toll", "local"},
        {"groc", "grocery", "groceries", "vegetables", "vegetable", "sabzi", "sabji", "milk", "doodh", "kirana", "fruits", "fruit", "eggs", "bread", "atta", "rice", "dal", "blinkit", "zepto", "dmart"},
        {"bills", "bill", "recharge", "electricity", "light", "wifi", "internet", "rent", "gas", "maintenance", "dth"},
        {"health", "medicine", "medicines", "doctor", "pharmacy", "chemist", "tablet", "tablets", "hospital", "clinic", "gym"},
        {"shop", "shopping", "clothes", "shirt", "shoes", "amazon", "flipkart", "myntra", "meesho"},
        {"fun", "movie", "cinema", "game", "games", "netflix", "party"},
        {"study", "book", "books", "notes", "xerox", "print", "printout", "stationery", "pen", "class", "classes", "fees", "coaching", "exam"},
        {"gift", "gift", "gifts", "present"},
        {"friends", "friend", "friends", "treat"},
        {"home", "home", "house", "furniture", "repair", "kitchen"}
    };
    static final Pattern NUM = Pattern.compile("(?:₹|rs\\.?|rupees?)?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(?:₹|rs\\.?|rupees?|bucks)?", Pattern.CASE_INSENSITIVE);

    double amt = 0;
    String what = "", cat = "";

    static Voice parse(String said, String catsJson) {
        Voice v = new Voice();
        String t = said == null ? "" : said.trim();
        Matcher m = NUM.matcher(t);
        if (m.find()) {
            try { v.amt = Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception ignored) { }
            t = (t.substring(0, m.start()) + " " + t.substring(m.end())).trim();
        }
        String low = " " + t.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9 ]", " ") + " ";

        // 1. a category the user named (their own category list first)
        String spokenCat = "";
        try {
            JSONArray cats = new JSONArray(catsJson);
            for (int i = 0; i < cats.length() && v.cat.isEmpty(); i++) {
                JSONObject o = cats.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name").toLowerCase(Locale.ENGLISH).trim();
                if (!name.isEmpty() && low.contains(" " + name + " ")) { v.cat = o.optString("id"); spokenCat = name; }
            }
        } catch (Exception ignored) { }
        // 2. otherwise guess from the words
        if (v.cat.isEmpty()) {
            for (String[] w : WORDS) {
                for (int k = 1; k < w.length && v.cat.isEmpty(); k++) if (low.contains(" " + w[k] + " ")) v.cat = w[0];
                if (!v.cat.isEmpty()) break;
            }
        }

        // what it was: what's left, minus filler words and the spoken category
        String w = t;
        if (!spokenCat.isEmpty()) w = w.replaceAll("(?i)[,\\s]*\\b(in |under |category )?" + Pattern.quote(spokenCat) + "\\b[.,\\s]*$", "");
        w = w.replaceAll("(?i)^(i )?(spent|paid|gave|for|on|kharcha)\\b\\s*", "");
        w = w.replaceAll("(?i)^(for|on)\\b\\s*", "");
        w = w.replaceAll("[\\s,.]+$", "").replaceAll("^[\\s,.]+", "").trim();
        if (!w.isEmpty()) w = w.substring(0, 1).toUpperCase(Locale.ENGLISH) + w.substring(1);
        v.what = w;
        return v;
    }
}
