package com.maxhayim.shackscreen;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Saved logins, one per app and site, like a password manager's. Each is encrypted with AES-GCM
 * under a key that lives in the Android Keystore and never leaves it, so the stored file alone is
 * useless. A login is only ever filled into the site it was saved for.
 */
final class Logins {

    static final class Login {
        String host, user = "", pass = "";
        boolean auto = true; // press the sign-in button after filling
    }

    private static final String PREFS = "logins", NEVER = "logins_never", ALIAS = "shackscreen_logins";
    private static final String SEP = "\n";

    private Logins() {}

    /** The Keystore can hold AES keys from Android 6. */
    static boolean supported() {
        return Build.VERSION.SDK_INT >= 23;
    }

    static List<Login> list(Context c, String id) {
        List<Login> out = new ArrayList<>();
        if (!supported()) return out;
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (!e.getKey().startsWith(id + SEP)) continue;
            Login l = decode(e.getKey().substring(id.length() + 1), (String) e.getValue());
            if (l != null) out.add(l);
        }
        return out;
    }

    static Login find(Context c, String id, String host) {
        if (!supported() || host == null) return null;
        String v = prefs(c).getString(id + SEP + host, null);
        return v == null ? null : decode(host, v);
    }

    static boolean save(Context c, String id, Login l) {
        if (!supported()) return false;
        try {
            JSONObject o = new JSONObject().put("user", l.user).put("pass", l.pass).put("auto", l.auto);
            prefs(c).edit().putString(id + SEP + l.host, encrypt(o.toString())).apply();
            never(c).edit().remove(id + SEP + l.host).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void delete(Context c, String id, String host) {
        prefs(c).edit().remove(id + SEP + host).apply();
    }

    /** "Never for this site": stop offering to save. */
    static void setNever(Context c, String id, String host) {
        never(c).edit().putBoolean(id + SEP + host, true).apply();
    }

    static boolean isNever(Context c, String id, String host) {
        return never(c).getBoolean(id + SEP + host, false);
    }

    /** Drops the logins of apps that were deleted. */
    static void prune(Context c, Collection<String> ids) {
        for (SharedPreferences p : new SharedPreferences[]{prefs(c), never(c)}) {
            SharedPreferences.Editor ed = p.edit();
            for (String k : p.getAll().keySet()) {
                int cut = k.indexOf(SEP);
                if (cut < 0 || !ids.contains(k.substring(0, cut))) ed.remove(k);
            }
            ed.apply();
        }
    }

    private static Login decode(String host, String stored) {
        try {
            JSONObject o = new JSONObject(decrypt(stored));
            Login l = new Login();
            l.host = host;
            l.user = o.optString("user");
            l.pass = o.optString("pass");
            l.auto = o.optBoolean("auto", true);
            return l;
        } catch (Exception e) {
            return null; // the key is gone (app data cleared), so the login can't be read
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences never(Context c) {
        return c.getSharedPreferences(NEVER, Context.MODE_PRIVATE);
    }

    // --- Encryption ---------------------------------------------------------------------------

    @TargetApi(23)
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    private static String decrypt(String stored) throws Exception {
        int cut = stored.indexOf(':');
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(stored.substring(0, cut), Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(stored.substring(cut + 1), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
