package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A backup of everything: profiles with their saved logins, applications (with their addresses and
 * tokens), widgets, location, theme, map keys and custom icons. It's encrypted on the device with
 * the user's backup passphrase (PBKDF2 then AES-256-GCM) before it goes to a cloud service, so the
 * service only ever holds an unreadable file. Restoring needs the same passphrase.
 */
final class Backup {

    static final String FILE_NAME = "shackscreen-backup.ssb";
    private static final String FORMAT = "shackscreen-backup";

    /** Settings files copied as they are. Logins and map keys are handled apart, decrypted then re-encrypted. */
    private static final String[] PLAIN = {"appearance", "callsigns", "shackscreen", "widgets"};

    private Backup() {}

    static final class BadPassphrase extends Exception {}

    // --- Running it -----------------------------------------------------------------------------

    private static final long DAY = 24 * 60 * 60 * 1000L;

    /** Needs the keystore (Android 6+) to keep the passphrase and sign-ins safe on the device. */
    static boolean supported() {
        return Logins.supported();
    }

    static String passphrase(Context c) {
        return Cloud.secret(c, "passphrase");
    }

    static void setPassphrase(Context c, String passphrase) {
        Cloud.prefs(c).edit().putString("passphrase", Logins.seal(passphrase)).apply();
    }

    static boolean ready(Context c) {
        return !Cloud.service(c).isEmpty() && !passphrase(c).isEmpty();
    }

    static boolean auto(Context c) {
        return Cloud.prefs(c).getBoolean("auto", true);
    }

    static void setAuto(Context c, boolean on) {
        Cloud.prefs(c).edit().putBoolean("auto", on).apply();
    }

    /** When the last backup went up (0 if never), and the error from the last try, if it failed. */
    static long last(Context c) {
        return Cloud.prefs(c).getLong("last", 0);
    }

    static String lastError(Context c) {
        return Cloud.prefs(c).getString("error", "");
    }

    /** Backs up now; blocking, so call it off the main thread. */
    static void now(Context c) throws Exception {
        try {
            Cloud.upload(c, seal(collect(c), passphrase(c)));
            Cloud.prefs(c).edit().putLong("last", System.currentTimeMillis()).remove("error").apply();
        } catch (Exception e) {
            Cloud.prefs(c).edit().putString("error", String.valueOf(e.getMessage())).apply();
            throw e;
        }
    }

    /** The daily backup, when it's switched on and due; quietly does nothing otherwise. */
    static void ifDue(Context c) {
        if (!supported() || !ready(c) || !auto(c) || System.currentTimeMillis() - last(c) < DAY) return;
        try {
            now(c);
        } catch (Exception ignored) {
            // The error is kept for Settings; it's tried again next time.
        }
    }

    // --- USB drives -----------------------------------------------------------------------------

    /**
     * ShackScreen's folder on each USB drive (or SD card) plugged in. Android lets an app write
     * there without asking: Android/data/com.maxhayim.shackscreen/files/ShackScreen on the drive.
     */
    static List<File> usbFolders(Context c) {
        List<File> out = new ArrayList<>();
        File[] dirs = c.getExternalFilesDirs(null);
        for (File d : dirs) {
            if (d == null) continue;
            boolean removable;
            try {
                removable = android.os.Environment.isExternalStorageRemovable(d);
            } catch (IllegalArgumentException e) {
                removable = false;
            }
            if (!removable) continue;
            File f = new File(d, "ShackScreen");
            if (f.isDirectory() || f.mkdirs()) out.add(f);
        }
        return out;
    }

    /** A new backup file name, so older ones on the drive are kept: shackscreen-backup-20261020-1915.ssb. */
    static String datedName() {
        return "shackscreen-backup-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".ssb";
    }

    /** Writes an encrypted backup into a USB folder; returns the file. Blocking. */
    static File saveToUsb(Context c, File folder) throws Exception {
        File f = new File(folder, datedName());
        writeTo(c, new FileOutputStream(f));
        return f;
    }

    /** Writes an encrypted backup to any stream (a USB folder, or a file the user picked), then closes it. */
    static void writeTo(Context c, OutputStream out) throws Exception {
        try (OutputStream o = out) {
            o.write(seal(collect(c), passphrase(c)));
        }
    }

    /** Backup files in the USB folders, newest first. */
    static List<File> usbBackups(Context c) {
        List<File> out = new ArrayList<>();
        for (File d : usbFolders(c)) {
            File[] fs = d.listFiles((dir, name) -> name.endsWith(".ssb"));
            if (fs != null) out.addAll(java.util.Arrays.asList(fs));
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    static byte[] readAll(java.io.InputStream in) throws IOException {
        try (java.io.InputStream i = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = i.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > 32 * 1024 * 1024) throw new IOException("File too large for a ShackScreen backup");
            }
            return out.toByteArray();
        }
    }

    /** Fetches the backup from the service and opens it; null if there's none. Blocking. */
    static JSONObject fetch(Context c, String passphrase) throws Exception {
        byte[] file = Cloud.download(c);
        return file == null ? null : open(file, passphrase);
    }

    // --- What's in a backup -------------------------------------------------------------------

    /** Everything to back up, as JSON, with secrets in the clear: only ever kept in memory, then encrypted. */
    static JSONObject collect(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", BuildConfig.VERSION_NAME).put("device", Build.MANUFACTURER + " " + Build.MODEL)
                .put("created", System.currentTimeMillis());
        JSONObject prefs = new JSONObject();
        for (String name : PLAIN) prefs.put(name, dump(c.getSharedPreferences(name, Context.MODE_PRIVATE)));
        root.put("prefs", prefs);

        JSONArray maps = new JSONArray();
        for (Maps.Style s : Maps.custom(c)) {
            maps.put(new JSONObject().put("name", s.name).put("url", s.url).put("provider", s.provider).put("key", s.key));
        }
        root.put("maps", maps).put("map_style", c.getSharedPreferences("maps", Context.MODE_PRIVATE).getString("style", ""));

        JSONObject logins = new JSONObject();
        for (CallSigns.Profile p : CallSigns.profiles(c)) {
            JSONArray list = new JSONArray();
            for (Object[] e : Logins.all(c, p.id)) {
                Logins.Login l = (Logins.Login) e[1];
                list.put(new JSONObject().put("app", e[0]).put("host", l.host).put("user", l.user).put("pass", l.pass).put("auto", l.auto));
            }
            logins.put(p.id, new JSONObject().put("logins", list).put("never", new JSONArray(Logins.nevers(c, p.id))));
        }
        root.put("logins", logins);

        JSONObject icons = new JSONObject();
        File[] files = new File(c.getFilesDir(), "icons").listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            if (!files[i].getName().endsWith("-custom.png")) continue;
            icons.put(files[i].getName(), Base64.encodeToString(read(files[i]), Base64.NO_WRAP));
        }
        root.put("icons", icons);
        return root;
    }

    /** Puts a backup back, replacing what's on this device. The app should be restarted afterwards. */
    static void apply(Context c, JSONObject root) throws Exception {
        JSONObject prefs = root.getJSONObject("prefs");
        for (String name : PLAIN) {
            JSONObject o = prefs.optJSONObject(name);
            if (o != null) load(c.getSharedPreferences(name, Context.MODE_PRIVATE), o);
        }

        JSONArray maps = root.optJSONArray("maps");
        JSONArray sealed = new JSONArray();
        for (int i = 0; maps != null && i < maps.length(); i++) {
            JSONObject m = maps.getJSONObject(i);
            JSONObject o = new JSONObject().put("name", m.optString("name")).put("url", m.optString("url"));
            if (!m.optString("provider").isEmpty()) {
                o.put("provider", m.optString("provider")).put("key", Logins.seal(m.optString("key")));
            }
            sealed.put(o);
        }
        c.getSharedPreferences("maps", Context.MODE_PRIVATE).edit()
                .putString("custom", sealed.toString()).putString("style", root.optString("map_style", "dark")).commit();

        JSONObject logins = root.optJSONObject("logins");
        if (logins != null) {
            for (java.util.Iterator<String> it = logins.keys(); it.hasNext(); ) {
                String profile = it.next();
                JSONObject p = logins.getJSONObject(profile);
                List<Object[]> list = new ArrayList<>();
                JSONArray a = p.optJSONArray("logins");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Logins.Login l = new Logins.Login();
                    l.host = o.optString("host");
                    l.user = o.optString("user");
                    l.pass = o.optString("pass");
                    l.auto = o.optBoolean("auto", true);
                    list.add(new Object[]{o.optString("app"), l});
                }
                List<String> nevers = new ArrayList<>();
                JSONArray n = p.optJSONArray("never");
                for (int i = 0; n != null && i < n.length(); i++) nevers.add(n.getString(i));
                Logins.replaceAll(c, profile, list, nevers);
            }
        }

        File dir = new File(c.getFilesDir(), "icons");
        dir.mkdirs();
        JSONObject icons = root.optJSONObject("icons");
        if (icons != null) {
            for (java.util.Iterator<String> it = icons.keys(); it.hasNext(); ) {
                String name = it.next();
                if (!name.matches("[A-Za-z0-9_-]+-custom\\.png")) continue; // never a path
                try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                    out.write(Base64.decode(icons.getString(name), Base64.NO_WRAP));
                }
            }
        }
    }

    private static JSONObject dump(SharedPreferences p) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            Object v = e.getValue();
            String t = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l"
                    : v instanceof Float ? "f" : v instanceof String ? "s" : null;
            if (t == null) continue; // string sets aren't used
            o.put(e.getKey(), new JSONObject().put("t", t).put("v", v));
        }
        return o;
    }

    private static void load(SharedPreferences p, JSONObject o) throws Exception {
        SharedPreferences.Editor ed = p.edit().clear();
        for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject e = o.getJSONObject(k);
            switch (e.getString("t")) {
                case "b": ed.putBoolean(k, e.getBoolean("v")); break;
                case "i": ed.putInt(k, e.getInt("v")); break;
                case "l": ed.putLong(k, e.getLong("v")); break;
                case "f": ed.putFloat(k, (float) e.getDouble("v")); break;
                default: ed.putString(k, e.getString("v"));
            }
        }
        ed.commit();
    }

    private static byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0;
            for (int n; off < b.length && (n = in.read(b, off, b.length - off)) > 0; ) off += n;
        }
        return b;
    }

    // --- Encryption ---------------------------------------------------------------------------

    /**
     * The backup file: a small JSON header (format, key derivation, salt, IV) and the AES-256-GCM
     * ciphertext of the backup. The header is authenticated with it, so neither can be swapped.
     */
    static byte[] seal(JSONObject backup, String passphrase) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[16], iv = new byte[12];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        String kdf = Build.VERSION.SDK_INT >= 26 ? "PBKDF2WithHmacSHA256" : "PBKDF2WithHmacSHA1";
        int iterations = kdf.endsWith("SHA256") ? 310_000 : 720_000;
        JSONObject head = new JSONObject().put("format", FORMAT).put("v", 1).put("kdf", kdf).put("iter", iterations)
                .put("salt", b64(salt)).put("iv", b64(iv));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(passphrase, kdf, iterations, salt), new GCMParameterSpec(128, iv));
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        c.updateAAD(headBytes);
        byte[] body = c.doFinal(backup.toString().getBytes(StandardCharsets.UTF_8));
        return (head + "\n" + b64(body)).getBytes(StandardCharsets.UTF_8);
    }

    static JSONObject open(byte[] file, String passphrase) throws Exception {
        String s = new String(file, StandardCharsets.UTF_8);
        int cut = s.indexOf('\n');
        if (cut < 0) throw new IOException("Not a ShackScreen backup");
        String headText = s.substring(0, cut);
        JSONObject head = new JSONObject(headText);
        if (!FORMAT.equals(head.optString("format"))) throw new IOException("Not a ShackScreen backup");
        if (head.optInt("v") > 1) throw new IOException("Made by a newer ShackScreen; update first");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(passphrase, head.getString("kdf"), head.getInt("iter"), unb64(head.getString("salt"))),
                new GCMParameterSpec(128, unb64(head.getString("iv"))));
        c.updateAAD(headText.getBytes(StandardCharsets.UTF_8));
        try {
            return new JSONObject(new String(c.doFinal(unb64(s.substring(cut + 1).trim())), StandardCharsets.UTF_8));
        } catch (javax.crypto.AEADBadTagException e) {
            throw new BadPassphrase();
        }
    }

    private static SecretKeySpec key(String passphrase, String kdf, int iterations, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256);
        try {
            return new SecretKeySpec(SecretKeyFactory.getInstance(kdf).generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
