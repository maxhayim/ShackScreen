package com.maxhayim.shackscreen;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

/**
 * Updates from GitHub Releases. Off: never contacts GitHub. Notify: checks daily and shows a
 * badge. Auto: also downloads and installs; silent on Android 12+ once ShackScreen has installed
 * itself, one confirm click on older Android and Fire TV.
 */
final class Updater {

    static final String REPO = "maxhayim/ShackScreen";
    static final String OFF = "off", NOTIFY = "notify", AUTO = "auto";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long CHECK_EVERY_MS = 6 * 60 * 60 * 1000L;
    private static final int JOB_ID = 1;
    private static final String PREFS = "updater";

    /** Activities on screen; an install that needs a click is only shown while this is above 0. */
    static int visible;

    static final class Release {
        final String version, apkUrl;

        Release(String version, String apkUrl) {
            this.version = version;
            this.apkUrl = apkUrl;
        }
    }

    // --- Settings and state ----------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String mode(Context c) { return prefs(c).getString("mode", NOTIFY); }

    static void setMode(Context c, String mode) { prefs(c).edit().putString("mode", mode).apply(); }

    /** The newer version found by the last check, or null. */
    static Release available(Context c) {
        SharedPreferences p = prefs(c);
        String v = p.getString("version", null), url = p.getString("url", null);
        if (v == null || url == null || compare(v, BuildConfig.VERSION_NAME) <= 0) return null;
        return new Release(v, url);
    }

    /** A line for Settings: what happened last ("Up to date", "Install failed: …"). */
    static String status(Context c) { return prefs(c).getString("status", ""); }

    static void setStatus(Context c, String s) { prefs(c).edit().putString("status", s).apply(); }

    // --- Checking --------------------------------------------------------------------------

    /** Runs the daily job, and checks now if the last check is old. Called from the dashboard. */
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24 * 60 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    static boolean due(Context c) {
        return !OFF.equals(mode(c))
                && System.currentTimeMillis() - prefs(c).getLong("checked", 0) > CHECK_EVERY_MS;
    }

    /**
     * Asks GitHub for the latest release and refreshes the app catalog. Blocking. In Auto mode a
     * newer release is downloaded and installed too.
     */
    static void run(Context c, boolean userAsked) {
        if (OFF.equals(mode(c)) && !userAsked) return;
        Catalog.refresh(c);
        Release r;
        try {
            r = latest();
        } catch (IOException e) {
            setStatus(c, "Couldn't reach GitHub");
            return;
        }
        prefs(c).edit().putLong("checked", System.currentTimeMillis())
                .putString("version", r.version).putString("url", r.apkUrl).apply();
        if (compare(r.version, BuildConfig.VERSION_NAME) <= 0) {
            setStatus(c, "Up to date");
            return;
        }
        setStatus(c, "Version " + r.version + " is available");
        if (AUTO.equals(mode(c))) install(c, r);
    }

    private static Release latest() throws IOException {
        Object o = Net.json(LATEST, null);
        if (!(o instanceof JSONObject)) throw new IOException("unexpected reply");
        JSONObject rel = (JSONObject) o;
        String version = rel.optString("tag_name").replaceFirst("^v", "");
        JSONArray assets = rel.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && a.optString("name").endsWith(".apk")) {
                return new Release(version, a.optString("browser_download_url"));
            }
        }
        throw new IOException("release " + version + " has no APK");
    }

    /** Compares "1.10.0" with "1.9.2" by number, part by part. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Installing ------------------------------------------------------------------------

    /** Downloads, checks and hands the APK to Android's installer. Blocking. */
    static void install(Context c, Release r) {
        File apk = new File(c.getCacheDir(), "update.apk");
        try {
            setStatus(c, "Downloading " + r.version + "…");
            download(r.apkUrl, apk);
            String problem = verify(c, apk);
            if (problem != null) {
                apk.delete();
                setStatus(c, "Update refused: " + problem);
                return;
            }
            setStatus(c, "Installing " + r.version + "…");
            commit(c, apk);
        } catch (IOException e) {
            setStatus(c, "Update failed: " + e.getMessage());
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "ShackScreen/" + BuildConfig.VERSION_NAME);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                copy(in, out);
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * The APK must be ShackScreen, newer, and signed with the same key as this install. Android
     * enforces the key too; checking first gives a clear message instead of a failed install.
     */
    @SuppressWarnings("deprecation") // GET_SIGNATURES is the flag that reads an APK file on every API level
    private static String verify(Context c, File apk) {
        PackageManager pm = c.getPackageManager();
        PackageInfo update = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (update == null) return "not a valid APK";
        if (!c.getPackageName().equals(update.packageName)) return "wrong app";
        if (compare(update.versionName, BuildConfig.VERSION_NAME) <= 0) return "not newer";
        try {
            Signature[] mine = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            if (update.signatures != null && update.signatures.length > 0 && !Arrays.equals(mine, update.signatures)) {
                return "signed with a different key";
            }
        } catch (PackageManager.NameNotFoundException e) {
            return "can't read this app's key";
        }
        return null;
    }

    private static void commit(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // Silent when ShackScreen is the installer of record of itself; otherwise Android asks.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("base.apk", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                copy(in, out);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, id, new Intent(c, UpdateReceiver.class), flags);
            s.commit(done.getIntentSender());
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }
}
