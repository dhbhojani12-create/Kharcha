package com.dhbhojani.kharcha;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The user's own SBI Prepaid User ID and password, kept only on this phone and encrypted with
 * a key in the Android Keystore (the key never leaves the secure hardware).
 */
public class SbiLogin {
    static final String ALIAS = "kharcha_sbi";
    static final String PREFS = "kharcha_sbi_login";

    static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    static String enc(String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    static String dec(String s) throws Exception {
        String[] p = s.split(":");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static boolean has(Context ctx) { return prefs(ctx).contains("u") && prefs(ctx).contains("p"); }

    static boolean save(Context ctx, String user, String pass) {
        try {
            prefs(ctx).edit().putString("u", enc(user)).putString("p", enc(pass)).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** {user, pass}, or null if nothing is saved or it can't be read. */
    static String[] load(Context ctx) {
        try {
            if (!has(ctx)) return null;
            return new String[]{dec(prefs(ctx).getString("u", "")), dec(prefs(ctx).getString("p", ""))};
        } catch (Exception e) {
            return null;
        }
    }

    static void forget(Context ctx) {
        prefs(ctx).edit().clear().apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }
}
