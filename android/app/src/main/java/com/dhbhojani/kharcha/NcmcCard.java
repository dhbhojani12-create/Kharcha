package com.dhbhojani.kharcha;

import android.nfc.tech.IsoDep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads an NCMC transit card (Mumbai Metro / Chalo / any RuPay NCMC) over NFC.
 * Read only: balance, card number and recent journeys. It cannot change anything on the card.
 *
 * Protocol, as documented by the open-source NCMC readers:
 *   SELECT PPSE -> AID -> SELECT AID -> GPO (with NCMC PDOL) -> tag DF33 holds the purse balance
 *   READ RECORD SFI 1/2  -> PAN + effective date
 *   READ RECORD SFI 16   -> Common Service Area: 4 journeys per record
 */
public class NcmcCard {

    static final byte[] PPSE = "2PAY.SYS.DDF01".getBytes();
    static final int LOG_SFI = 16;
    static final int LOG_RECORDS = 10;

    /** PDOL the NCMC terminals send; the card needs it before it returns the purse balance. */
    static final byte[] PDOL = hex(
            "FF80F00001" +        // 9F40 terminal capabilities
            "0040000000" +        // DF3A
            "000848" +            // 9F33
            "0002" +              // 9F09
            "0743" +              // 9F15
            "260206" +            // 9A  date
            "060235" +            // 9F21 time
            "CFBAFEBD" +          // 9F37 unpredictable number
            "FF01" +              // DF16 service id
            "3032343638373000");  // 9F1C terminal id

    static byte[] hex(String s) {
        int n = s.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    static String hx(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    /* ---------------- TLV ---------------- */
    static class Tlv {
        int tag;
        byte[] value;
        Tlv(int t, byte[] v) { tag = t; value = v; }
    }

    static boolean constructed(int tag) {
        int first = tag;
        while (first > 0xFF) first >>= 8;
        return (first & 0x20) != 0;
    }

    static List<Tlv> parseTlv(byte[] d) {
        List<Tlv> out = new ArrayList<>();
        int off = 0;
        while (off < d.length) {
            int b0 = d[off] & 0xFF;
            if (b0 == 0x00 || b0 == 0xFF) { off++; continue; }
            int tag = b0;
            off++;
            if ((b0 & 0x1F) == 0x1F) {
                while (off < d.length) {
                    int b = d[off] & 0xFF;
                    tag = (tag << 8) | b;
                    off++;
                    if ((b & 0x80) == 0) break;
                }
            }
            if (off >= d.length) break;
            int len = d[off] & 0xFF;
            off++;
            if (len > 0x80) {
                int nb = len & 0x7F;
                if (nb > 4 || off + nb > d.length) break;
                len = 0;
                for (int i = 0; i < nb; i++) { len = (len << 8) | (d[off] & 0xFF); off++; }
            } else if (len == 0x80) {
                break; // indefinite length is not used by these cards
            }
            if (len < 0 || off + len > d.length) break;
            out.add(new Tlv(tag, Arrays.copyOfRange(d, off, off + len)));
            off += len;
        }
        return out;
    }

    static List<byte[]> findTag(byte[] data, int wanted) {
        List<byte[]> found = new ArrayList<>();
        for (Tlv t : parseTlv(data)) {
            if (t.tag == wanted) found.add(t.value);
            if (constructed(t.tag)) found.addAll(findTag(t.value, wanted));
        }
        return found;
    }

    /* ---------------- APDU ---------------- */
    static byte[] apdu(int cla, int ins, int p1, int p2, byte[] data, int le) {
        int n = 4 + (data != null && data.length > 0 ? 1 + data.length : 0) + 1;
        byte[] a = new byte[n];
        a[0] = (byte) cla; a[1] = (byte) ins; a[2] = (byte) p1; a[3] = (byte) p2;
        int i = 4;
        if (data != null && data.length > 0) {
            a[i++] = (byte) data.length;
            System.arraycopy(data, 0, a, i, data.length);
            i += data.length;
        }
        a[i] = (byte) le;
        return a;
    }

    static int sw(byte[] r) {
        if (r == null || r.length < 2) return 0;
        return ((r[r.length - 2] & 0xFF) << 8) | (r[r.length - 1] & 0xFF);
    }

    static byte[] body(byte[] r) {
        return r == null || r.length < 2 ? new byte[0] : Arrays.copyOfRange(r, 0, r.length - 2);
    }

    /* ---------------- the read ---------------- */
    static final String[] LANGS = {"English", "Hindi", "Bengali", "Marathi", "Telugu", "Tamil",
            "Gujarati", "Urdu", "Kannada", "Odia", "Malayalam", "Punjabi", "Sanskrit", "Assamese",
            "Maithili", "Santali", "Kashmiri", "Nepali", "Sindhi", "Dogri", "Konkani", "Manipuri", "Bodo"};
    static final String[] STATUS = {"Exit", "Entry", "Penalty", "One tap"};

    /** Reads the card and returns everything as JSON for the app to show. */
    public static JSONObject read(IsoDep iso) throws Exception {
        JSONObject out = new JSONObject();
        JSONArray trace = new JSONArray();
        iso.setTimeout(5000);
        iso.connect();
        try {
            // 1. PPSE -> AID
            byte[] r = iso.transceive(apdu(0x00, 0xA4, 0x04, 0x00, PPSE, 0x00));
            trace.put("PPSE -> " + String.format("%04X", sw(r)) + " " + hx(r));
            if (sw(r) != 0x9000) throw new Exception("Card did not answer the first command (SW " + String.format("%04X", sw(r)) + ")");
            byte[] ppse = body(r);
            List<byte[]> labels = findTag(ppse, 0x50);
            if (!labels.isEmpty()) out.put("cardType", new String(labels.get(0)).trim());
            List<byte[]> aids = findTag(ppse, 0x4F);
            if (aids.isEmpty()) throw new Exception("Not a payment / NCMC card");
            byte[] aid = aids.get(0);
            out.put("aid", hx(aid));

            // 2. SELECT AID
            r = iso.transceive(apdu(0x00, 0xA4, 0x04, 0x00, aid, 0x00));
            trace.put("AID -> " + String.format("%04X", sw(r)));
            if (sw(r) != 0x9000) throw new Exception("Could not open the card application");

            // 3. GPO -> DF33 purse balance
            byte[] cmd = new byte[2 + PDOL.length];
            cmd[0] = (byte) 0x83; cmd[1] = (byte) PDOL.length;
            System.arraycopy(PDOL, 0, cmd, 2, PDOL.length);
            r = iso.transceive(apdu(0x80, 0xA8, 0x00, 0x00, cmd, 0x00));
            if ((sw(r) & 0xFF00) == 0x6C00) r = iso.transceive(apdu(0x80, 0xA8, 0x00, 0x00, cmd, r[r.length - 1] & 0xFF));
            trace.put("GPO -> " + String.format("%04X", sw(r)) + " " + hx(body(r)));
            byte[] df = null;
            byte[] afl = null;
            if (sw(r) == 0x9000) {
                byte[] gpo = body(r);
                List<byte[]> df33 = findTag(gpo, 0xDF33);
                if (!df33.isEmpty()) {
                    df = df33.get(0);
                    trace.put("DF33(" + df.length + ") " + hx(df));
                    if (df.length >= 29) {
                        try {
                            out.put("balance", Long.parseLong(hx(Arrays.copyOfRange(df, 23, 29))) / 100.0);
                        } catch (Exception ignored) { }
                    }
                }
                List<byte[]> aflList = findTag(gpo, 0x94);
                if (!aflList.isEmpty()) {
                    afl = aflList.get(0);
                    trace.put("AFL " + hx(afl));
                }
            }

            // 4. card number + effective date
            long effMs = 0;
            for (int sfi = 1; sfi <= 2 && (!out.has("pan") || effMs == 0); sfi++) {
                for (int rec = 1; rec <= 3; rec++) {
                    byte[] d = readRecord(iso, sfi, rec);
                    if (d == null) continue;
                    List<byte[]> pan = findTag(d, 0x5A);
                    if (!pan.isEmpty() && !out.has("pan")) {
                        String p = hx(pan.get(0)).replace("F", "");
                        if (p.length() >= 8) out.put("pan", p.substring(0, 4) + " ···· " + p.substring(p.length() - 4));
                    }
                    List<byte[]> eff = findTag(d, 0x5F25);
                    if (!eff.isEmpty() && effMs == 0) effMs = bcdDate(eff.get(0));
                    List<byte[]> exp = findTag(d, 0x5F24);
                    if (!exp.isEmpty() && !out.has("expiry")) {
                        String e = hx(exp.get(0));
                        if (e.length() >= 4) out.put("expiry", e.substring(2, 4) + "/" + e.substring(0, 2));
                    }
                }
            }

            // 5. journeys. Read where the card's own directory (AFL) says its records are,
            //    then the log SFI, then DF33 itself. Everything is dumped so an unknown
            //    layout can be worked out from one tap.
            JSONArray journeys = new JSONArray();
            List<byte[]> blobs = new ArrayList<>();

            if (afl != null) {
                for (int k = 0; k + 3 < afl.length; k += 4) {
                    int sfi = (afl[k] & 0xFF) >> 3;
                    int first = afl[k + 1] & 0xFF, last = afl[k + 2] & 0xFF;
                    if (sfi == 0 || first == 0 || last < first || last - first > 16) continue;
                    for (int rec = first; rec <= last; rec++) {
                        byte[] d = readRecord(iso, sfi, rec);
                        if (d == null) continue;
                        trace.put("SFI" + sfi + "/" + rec + "(" + d.length + ") " + hx(d));
                        blobs.add(d);
                    }
                }
            }
            for (int rec = 1; rec <= LOG_RECORDS; rec++) {
                byte[] d = readRecord(iso, LOG_SFI, rec);
                if (d == null) break;
                trace.put("SFI16/" + rec + "(" + d.length + ") " + hx(d));
                blobs.add(d);
            }
            if (df != null) blobs.add(df);

            // any 96-byte stretch is a Common Service Area: 1 validation + 4 journeys
            List<byte[]> csas = new ArrayList<>();
            for (byte[] d : blobs) {
                if (d.length == 96) { csas.add(d); continue; }
                for (Tlv t : parseTlv(d)) if (t.value.length == 96) csas.add(t.value);
                if (d.length > 96) {
                    csas.add(Arrays.copyOfRange(d, d.length - 96, d.length));
                    if (d.length >= 128) csas.add(Arrays.copyOfRange(d, 32, 128));
                }
            }
            int recNo = 0;
            for (byte[] csa : csas) {
                recNo++;
                if (!out.has("language")) {
                    int lang = (csa[1] >> 3) & 0x1F;
                    if (lang < LANGS.length) out.put("language", LANGS[lang]);
                }
                for (int i = 0; i < 4; i++) {
                    int o = 21 + i * 17;
                    int acq = csa[o] & 0xFF;
                    int opId = u16(csa, o + 1);
                    int term = u24(csa, o + 3);
                    int mins = u24(csa, o + 6);
                    int seq = u16(csa, o + 9);
                    double amt = u16(csa, o + 11) / 10.0;
                    long bs = ((long) (csa[o + 13] & 0xFF) << 16) | ((csa[o + 14] & 0xFF) << 8) | (csa[o + 15] & 0xFF);
                    double balBefore = ((bs >> 4) & 0xFFFFF) / 10.0;
                    int st = (int) (bs & 0x0F);
                    // a real journey has a time, a sequence number and a sane amount
                    if (mins <= 0 || seq <= 0 || amt > 10000 || balBefore > 100000) continue;
                    JSONObject j = new JSONObject();
                    j.put("seq", seq);
                    j.put("mins", mins);
                    j.put("acq", acq);
                    j.put("op", opId);
                    j.put("term", term);
                    j.put("amt", amt);
                    j.put("balBefore", balBefore);
                    j.put("status", st < STATUS.length ? STATUS[st] : "?");
                    if (effMs > 0) j.put("ts", effMs + (long) mins * 60000L);
                    journeys.put(j);
                }
            }
            out.put("journeys", journeys);
            if (effMs > 0) out.put("effective", effMs);
        } finally {
            try { iso.close(); } catch (Exception ignored) { }
        }
        out.put("trace", trace);
        return out;
    }

    static byte[] readRecord(IsoDep iso, int sfi, int rec) {
        try {
            int p2 = (sfi << 3) | 0x04;
            byte[] r = iso.transceive(apdu(0x00, 0xB2, rec, p2, null, 0x00));
            if ((sw(r) & 0xFF00) == 0x6C00) r = iso.transceive(apdu(0x00, 0xB2, rec, p2, null, r[r.length - 1] & 0xFF));
            if (sw(r) != 0x9000) return null;
            return body(r);
        } catch (Exception e) {
            return null;
        }
    }

    static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
    static int u24(byte[] b, int o) { return ((b[o] & 0xFF) << 16) | ((b[o + 1] & 0xFF) << 8) | (b[o + 2] & 0xFF); }

    /** YYMMDD in BCD -> epoch millis (local midnight). */
    static long bcdDate(byte[] b) {
        try {
            String s = hx(b);
            if (s.length() < 6) return 0;
            int yy = Integer.parseInt(s.substring(0, 2));
            int mm = Integer.parseInt(s.substring(2, 4));
            int dd = Integer.parseInt(s.substring(4, 6));
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.clear();
            c.set(2000 + yy, mm - 1, dd, 0, 0, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
