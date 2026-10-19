package com.maxhayim.shackscreen;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The kinds of app ShackScreen knows about. The copy built into the APK (assets/catalog.json) is
 * replaced by a newer one from GitHub when updates are on, so new apps appear without a new APK.
 */
final class Catalog {

    /** The catalog format this build understands; a newer format from GitHub is ignored. */
    private static final int FORMAT = 1;
    private static final String REMOTE =
            "https://raw.githubusercontent.com/" + Updater.REPO + "/main/app/src/main/assets/catalog.json";

    static final class Type {
        final String id, name, url, health, icon;
        final int port;          // default port for a self-hosted server, 0 if none
        final boolean token;     // the API needs a token (MeshMonitor)
        final boolean audio;     // plays sound, so autoplay is allowed in the viewer
        final boolean seed;      // added on first run
        final Set<String> feeds; // dashboard panels this app can supply: space, pota, mesh

        Type(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name", id);
            url = o.optString("url", "");
            health = o.optString("health", "");
            icon = o.optString("icon", "");
            port = o.optInt("port", 0);
            token = o.optBoolean("token");
            audio = o.optBoolean("audio");
            seed = o.optBoolean("seed");
            feeds = new HashSet<>();
            JSONArray f = o.optJSONArray("feeds");
            for (int i = 0; f != null && i < f.length(); i++) feeds.add(f.optString(i));
        }
    }

    private static List<Type> types;

    static synchronized List<Type> all(Context c) {
        if (types != null) return types;
        File cached = cache(c);
        if (cached.exists()) {
            try (InputStream in = new FileInputStream(cached)) {
                types = parse(read(in));
                return types;
            } catch (Exception e) {
                cached.delete();
            }
        }
        try (InputStream in = c.getAssets().open("catalog.json")) {
            types = parse(read(in));
        } catch (Exception e) {
            throw new IllegalStateException("catalog.json is missing or broken", e);
        }
        return types;
    }

    /** Fetches the catalog from GitHub and keeps it if it's valid. Blocking; failures are ignored. */
    static void refresh(Context c) {
        try {
            Object o = Net.json(REMOTE, null);
            if (!(o instanceof JSONObject)) return;
            String json = o.toString();
            parse(json); // throws if it isn't usable
            File tmp = new File(cache(c).getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes("UTF-8"));
            }
            if (tmp.renameTo(cache(c))) {
                synchronized (Catalog.class) {
                    types = null;
                }
            }
        } catch (Exception ignored) {
            // Keep whatever catalog we already have.
        }
    }

    /** Named by app version, so a newly installed APK starts from its own built-in catalog. */
    private static File cache(Context c) {
        return new File(c.getFilesDir(), "catalog-" + BuildConfig.VERSION_CODE + ".json");
    }

    private static List<Type> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version") > FORMAT) throw new IllegalArgumentException("newer catalog format");
        JSONArray apps = root.getJSONArray("apps");
        List<Type> list = new ArrayList<>();
        boolean custom = false;
        for (int i = 0; i < apps.length(); i++) {
            Type t = new Type(apps.getJSONObject(i));
            if (t.id.isEmpty()) continue;
            custom |= t.id.equals("custom");
            list.add(t);
        }
        if (!custom) throw new IllegalArgumentException("catalog has no custom type");
        return list;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    static Type get(Context c, String id) {
        for (Type t : all(c)) if (t.id.equals(id)) return t;
        for (Type t : all(c)) if (t.id.equals("custom")) return t;
        throw new IllegalStateException("catalog.json has no custom type");
    }
}
