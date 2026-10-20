package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Where backups go: Google Drive, Dropbox, an S3-compatible bucket (AWS S3, Backblaze B2,
 * Cloudflare R2, MinIO) or a WebDAV folder (Nextcloud, Synology, ownCloud). The account details
 * and sign-in tokens are encrypted with the device's keystore and never leave the TV; the backup
 * itself is encrypted with the passphrase before it's sent (see Backup).
 */
final class Cloud {

    static final String NONE = "", GOOGLE = "google", DROPBOX = "dropbox", S3 = "s3", WEBDAV = "webdav";
    private static final String PREFS = "backup";
    private static final int TIMEOUT_MS = 20000;

    private Cloud() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String service(Context c) {
        return prefs(c).getString("service", NONE);
    }

    static String name(Context c, String service) {
        switch (service) {
            case GOOGLE: return "Google Drive";
            case DROPBOX: return "Dropbox";
            case S3: return "S3-compatible";
            case WEBDAV: return "WebDAV";
            default: return c.getString(R.string.backup_none);
        }
    }

    /** Google Drive and Dropbox need the app's own keys, set when the release is built. */
    static boolean available(String service) {
        if (GOOGLE.equals(service)) return !BuildConfig.GOOGLE_CLIENT_ID.isEmpty();
        if (DROPBOX.equals(service)) return !BuildConfig.DROPBOX_APP_KEY.isEmpty();
        return true;
    }

    /** "Google Drive · ShackScreen folder" and the like, never a secret. */
    static String describe(Context c) {
        SharedPreferences p = prefs(c);
        switch (service(c)) {
            case S3: return "S3   ·   " + p.getString("s3_bucket", "") + " at " + Uri.parse(p.getString("s3_endpoint", "")).getHost();
            case WEBDAV: return "WebDAV   ·   " + Uri.parse(p.getString("dav_url", "")).getHost();
            case GOOGLE: return "Google Drive   ·   ShackScreen folder";
            case DROPBOX: return "Dropbox   ·   Apps/ShackScreen";
            default: return c.getString(R.string.backup_none);
        }
    }

    /** Stop backing up: forgets the service, its sign-in and the passphrase on this device (the backup stays on the service). */
    static void forget(Context c) {
        prefs(c).edit().remove("service").remove("s3_endpoint").remove("s3_region").remove("s3_bucket").remove("s3_access")
                .remove("s3_secret").remove("dav_url").remove("dav_user").remove("dav_pass").remove("google_refresh")
                .remove("google_file").remove("dropbox_refresh").remove("passphrase").remove("last").remove("error").apply();
    }

    static String secret(Context c, String key) {
        return Logins.unseal(prefs(c).getString(key, ""));
    }

    // --- Sending and fetching the backup --------------------------------------------------------

    static void upload(Context c, byte[] data) throws IOException {
        switch (service(c)) {
            case S3: s3(c, "PUT", data); return;
            case WEBDAV: dav(c, "PUT", data); return;
            case GOOGLE: googleUpload(c, data); return;
            case DROPBOX: dropboxUpload(c, data); return;
            default: throw new IOException("No backup service chosen");
        }
    }

    /** The backup on the service, or null if there's none yet. */
    static byte[] download(Context c) throws IOException {
        switch (service(c)) {
            case S3: return s3(c, "GET", null);
            case WEBDAV: return dav(c, "GET", null);
            case GOOGLE: return googleDownload(c);
            case DROPBOX: return dropboxDownload(c);
            default: throw new IOException("No backup service chosen");
        }
    }

    // --- WebDAV ---------------------------------------------------------------------------------

    static void setWebDav(Context c, String url, String user, String pass) {
        if (!url.endsWith("/")) url += "/";
        prefs(c).edit().putString("service", WEBDAV).putString("dav_url", url).putString("dav_user", user)
                .putString("dav_pass", Logins.seal(pass)).apply();
    }

    private static byte[] dav(Context c, String method, byte[] body) throws IOException {
        SharedPreferences p = prefs(c);
        String auth = Base64.encodeToString((p.getString("dav_user", "") + ":" + secret(c, "dav_pass"))
                .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        Map<String, String> h = new TreeMap<>();
        h.put("Authorization", "Basic " + auth);
        return expect(method, p.getString("dav_url", "") + Backup.FILE_NAME, h, body, method.equals("GET"));
    }

    // --- S3-compatible --------------------------------------------------------------------------

    static void setS3(Context c, String endpoint, String region, String bucket, String access, String secret) {
        if (!endpoint.startsWith("http")) endpoint = "https://" + endpoint;
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        prefs(c).edit().putString("service", S3).putString("s3_endpoint", endpoint)
                .putString("s3_region", region.isEmpty() ? "us-east-1" : region).putString("s3_bucket", bucket)
                .putString("s3_access", access).putString("s3_secret", Logins.seal(secret)).apply();
    }

    /** One object, signed with AWS Signature Version 4, which B2, R2 and MinIO all accept. */
    private static byte[] s3(Context c, String method, byte[] body) throws IOException {
        SharedPreferences p = prefs(c);
        String endpoint = p.getString("s3_endpoint", ""), region = p.getString("s3_region", "us-east-1");
        String bucket = p.getString("s3_bucket", ""), access = p.getString("s3_access", "");
        Uri e = Uri.parse(endpoint);
        // AWS wants the bucket in the host name; the others take it in the path.
        boolean aws = e.getHost() != null && e.getHost().endsWith("amazonaws.com") && !bucket.contains(".");
        String host = aws ? bucket + "." + e.getHost() : e.getHost();
        if (e.getPort() > 0) host += ":" + e.getPort();
        String path = (aws ? "" : "/" + bucket) + "/ShackScreen/" + Backup.FILE_NAME;
        String url = e.getScheme() + "://" + host + path;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String amzDate = f.format(new Date()), day = amzDate.substring(0, 8);
            String payload = hex(sha256(body == null ? new byte[0] : body));
            String canonical = method + "\n" + path + "\n\n" + "host:" + host + "\n" + "x-amz-content-sha256:" + payload
                    + "\n" + "x-amz-date:" + amzDate + "\n\nhost;x-amz-content-sha256;x-amz-date\n" + payload;
            String scope = day + "/" + region + "/s3/aws4_request";
            String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
            byte[] k = hmac(("AWS4" + secret(c, "s3_secret")).getBytes(StandardCharsets.UTF_8), day);
            k = hmac(k, region);
            k = hmac(k, "s3");
            k = hmac(k, "aws4_request");
            String sig = hex(hmac(k, toSign));
            Map<String, String> h = new TreeMap<>();
            h.put("x-amz-date", amzDate);
            h.put("x-amz-content-sha256", payload);
            h.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + access + "/" + scope
                    + ", SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=" + sig);
            return expect(method, url, h, body, method.equals("GET"));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    // --- Google Drive (sign in on a phone with a code: Google's flow for TVs) -------------------

    static final class DeviceCode {
        String deviceCode, userCode, url;
        int interval = 5;
        long expires;
    }

    static DeviceCode googleStart() throws IOException {
        JSONObject o = form("https://oauth2.googleapis.com/device/code",
                "client_id=" + enc(BuildConfig.GOOGLE_CLIENT_ID) + "&scope=" + enc("https://www.googleapis.com/auth/drive.file"));
        DeviceCode d = new DeviceCode();
        d.deviceCode = o.optString("device_code");
        d.userCode = o.optString("user_code");
        d.url = o.optString("verification_url", "https://www.google.com/device");
        d.interval = Math.max(5, o.optInt("interval", 5));
        d.expires = System.currentTimeMillis() + o.optInt("expires_in", 900) * 1000L;
        return d;
    }

    /** Waits for the user to approve on their phone; true when signed in, false if they said no or it ran out. */
    static boolean googleFinish(Context c, DeviceCode d) throws IOException, InterruptedException {
        while (System.currentTimeMillis() < d.expires) {
            Thread.sleep(d.interval * 1000L);
            JSONObject o;
            try {
                o = form("https://oauth2.googleapis.com/token", "client_id=" + enc(BuildConfig.GOOGLE_CLIENT_ID)
                        + "&client_secret=" + enc(BuildConfig.GOOGLE_CLIENT_SECRET) + "&device_code=" + enc(d.deviceCode)
                        + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"));
            } catch (HttpError e) {
                String err = e.json.optString("error");
                if (err.equals("authorization_pending")) continue;
                if (err.equals("slow_down")) {
                    d.interval += 5;
                    continue;
                }
                return false; // access_denied or expired_token
            }
            prefs(c).edit().putString("service", GOOGLE).putString("google_refresh", Logins.seal(o.optString("refresh_token")))
                    .remove("google_file").apply();
            return true;
        }
        return false;
    }

    private static String googleToken(Context c) throws IOException {
        return form("https://oauth2.googleapis.com/token", "client_id=" + enc(BuildConfig.GOOGLE_CLIENT_ID)
                + "&client_secret=" + enc(BuildConfig.GOOGLE_CLIENT_SECRET) + "&refresh_token=" + enc(secret(c, "google_refresh"))
                + "&grant_type=refresh_token").optString("access_token");
    }

    /** The backup's id in Drive: remembered, or found in the ShackScreen folder; null if there's none. */
    private static String googleFile(Context c, String token, boolean create) throws IOException {
        String known = prefs(c).getString("google_file", "");
        if (!known.isEmpty()) return known;
        String folder = googleFind(token, "name='ShackScreen' and mimeType='application/vnd.google-apps.folder' and trashed=false");
        if (folder == null) {
            if (!create) return null;
            Map<String, String> h = bearer(token);
            h.put("Content-Type", "application/json");
            folder = json(expect("POST", "https://www.googleapis.com/drive/v3/files", h,
                    "{\"name\":\"ShackScreen\",\"mimeType\":\"application/vnd.google-apps.folder\"}".getBytes(StandardCharsets.UTF_8), true)).optString("id");
        }
        String file = googleFind(token, "name='" + Backup.FILE_NAME + "' and '" + folder + "' in parents and trashed=false");
        if (file == null && create) {
            String boundary = "shackscreen" + System.nanoTime();
            String meta = "{\"name\":\"" + Backup.FILE_NAME + "\",\"parents\":[\"" + folder + "\"]}";
            byte[] body = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + boundary
                    + "\r\nContent-Type: application/octet-stream\r\n\r\n\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
            Map<String, String> h = bearer(token);
            h.put("Content-Type", "multipart/related; boundary=" + boundary);
            file = json(expect("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", h, body, true)).optString("id");
        }
        if (file != null) prefs(c).edit().putString("google_file", file).apply();
        return file;
    }

    private static String googleFind(String token, String query) throws IOException {
        JSONArray files = json(expect("GET", "https://www.googleapis.com/drive/v3/files?fields=files(id)&q=" + enc(query),
                bearer(token), null, true)).optJSONArray("files");
        return files != null && files.length() > 0 ? files.optJSONObject(0).optString("id") : null;
    }

    private static void googleUpload(Context c, byte[] data) throws IOException {
        String token = googleToken(c);
        String id = googleFile(c, token, true);
        Map<String, String> h = bearer(token);
        h.put("Content-Type", "application/octet-stream");
        h.put("X-HTTP-Method-Override", "PATCH"); // HttpURLConnection can't send PATCH itself
        try {
            expect("POST", "https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media", h, data, false);
        } catch (HttpError e) {
            if (e.code != 404) throw e;
            prefs(c).edit().remove("google_file").apply(); // deleted in Drive: make a new one
            googleUpload(c, data);
        }
    }

    private static byte[] googleDownload(Context c) throws IOException {
        String token = googleToken(c);
        String id = googleFile(c, token, false);
        if (id == null) return null;
        return expect("GET", "https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", bearer(token), null, true);
    }

    // --- Dropbox (sign in on a phone, then type the code it shows) ------------------------------

    /** The address to open on a phone, and the verifier kept for dropboxFinish. */
    static String[] dropboxStart() {
        byte[] r = new byte[48];
        new SecureRandom().nextBytes(r);
        String verifier = Base64.encodeToString(r, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String challenge;
        try {
            challenge = Base64.encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        String url = "https://www.dropbox.com/oauth2/authorize?client_id=" + enc(BuildConfig.DROPBOX_APP_KEY)
                + "&response_type=code&token_access_type=offline&code_challenge_method=S256&code_challenge=" + challenge;
        return new String[]{url, verifier};
    }

    static void dropboxFinish(Context c, String code, String verifier) throws IOException {
        JSONObject o = form("https://api.dropboxapi.com/oauth2/token", "code=" + enc(code.trim()) + "&grant_type=authorization_code"
                + "&code_verifier=" + enc(verifier) + "&client_id=" + enc(BuildConfig.DROPBOX_APP_KEY));
        prefs(c).edit().putString("service", DROPBOX).putString("dropbox_refresh", Logins.seal(o.optString("refresh_token"))).apply();
    }

    private static String dropboxToken(Context c) throws IOException {
        return form("https://api.dropboxapi.com/oauth2/token", "grant_type=refresh_token&refresh_token="
                + enc(secret(c, "dropbox_refresh")) + "&client_id=" + enc(BuildConfig.DROPBOX_APP_KEY)).optString("access_token");
    }

    private static void dropboxUpload(Context c, byte[] data) throws IOException {
        Map<String, String> h = bearer(dropboxToken(c));
        h.put("Content-Type", "application/octet-stream");
        h.put("Dropbox-API-Arg", "{\"path\":\"/" + Backup.FILE_NAME + "\",\"mode\":\"overwrite\",\"mute\":true}");
        expect("POST", "https://content.dropboxapi.com/2/files/upload", h, data, false);
    }

    private static byte[] dropboxDownload(Context c) throws IOException {
        Map<String, String> h = bearer(dropboxToken(c));
        h.put("Dropbox-API-Arg", "{\"path\":\"/" + Backup.FILE_NAME + "\"}");
        try {
            return expect("POST", "https://content.dropboxapi.com/2/files/download", h, new byte[0], true);
        } catch (HttpError e) {
            if (e.code == 409) return null; // not found
            throw e;
        }
    }

    // --- HTTP -----------------------------------------------------------------------------------

    static final class HttpError extends IOException {
        final int code;
        final JSONObject json;

        HttpError(int code, String body) {
            super("HTTP " + code + (body.isEmpty() ? "" : ": " + (body.length() > 160 ? body.substring(0, 160) : body)));
            this.code = code;
            JSONObject j;
            try {
                j = new JSONObject(body);
            } catch (Exception e) {
                j = new JSONObject();
            }
            json = j;
        }
    }

    /** Sends a request and returns the body, or null for a 404 on GET (no backup yet). */
    private static byte[] expect(String method, String url, Map<String, String> headers, byte[] body, boolean wantBody) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", "ShackScreen/" + BuildConfig.VERSION_NAME);
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (body != null && !method.equals("GET")) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = c.getResponseCode();
            if (code == 404 && method.equals("GET")) return null;
            if (code >= 300) {
                InputStream err = c.getErrorStream();
                throw new HttpError(code, err == null ? "" : new String(read(err), StandardCharsets.UTF_8));
            }
            try (InputStream in = c.getInputStream()) {
                return wantBody ? read(in) : new byte[0];
            }
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject form(String url, String body) throws IOException {
        Map<String, String> h = new TreeMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return json(expect("POST", url, h, body.getBytes(StandardCharsets.UTF_8), true));
    }

    private static JSONObject json(byte[] b) throws IOException {
        try {
            return new JSONObject(new String(b == null ? new byte[0] : b, StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException("Unexpected reply", e);
        }
    }

    private static Map<String, String> bearer(String token) {
        Map<String, String> h = new TreeMap<>();
        h.put("Authorization", "Bearer " + token);
        return h;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static byte[] sha256(byte[] b) throws java.security.GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    private static byte[] hmac(byte[] key, String data) throws java.security.GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }
}
