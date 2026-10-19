package com.maxhayim.shackscreen;

import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small blocking HTTP helpers; call from a background thread. */
final class Net {

    private static final int TIMEOUT_MS = 8000;

    /** GETs a URL and returns its JSON (a JSONObject or JSONArray). */
    static Object json(String url, String token) throws IOException {
        HttpURLConnection c = open(url, token);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " from " + url);
            try (InputStream in = c.getInputStream()) {
                return new JSONTokener(read(in)).nextValue();
            } catch (org.json.JSONException e) {
                throw new IOException("Not JSON: " + url, e);
            }
        } finally {
            c.disconnect();
        }
    }

    /** True if the server answers with anything below 500. */
    static boolean up(String url) {
        try {
            HttpURLConnection c = open(url, null);
            try {
                return c.getResponseCode() < 500;
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static HttpURLConnection open(String url, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "ShackScreen/" + BuildConfig.VERSION_NAME);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
