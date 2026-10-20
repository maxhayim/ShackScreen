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
        final String id, name, url, icon, category;
        final int port;          // default port for a self-hosted server, 0 if none
        final boolean token;     // the API needs a token (MeshMonitor)
        final boolean audio;     // plays sound, so autoplay is allowed in the viewer
        final boolean home;      // starts on the home row when added as a default
        final int zoom;          // page size in percent; under 100 shows more of a site laid out large
        final int seed;          // added to everyone's apps in this round of defaults; 0 = never
        final Set<String> feeds; // dashboard panels this app can supply: space, pota, mesh
        final List<String[]> pages; // {name, url}: the app's main pages, switched between in the viewer

        Type(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name", id);
            url = o.optString("url", "");
            icon = o.optString("icon", "");
            port = o.optInt("port", 0);
            token = o.optBoolean("token");
            audio = o.optBoolean("audio");
            home = o.optBoolean("home");
            zoom = o.optInt("zoom", 100);
            // Older catalogs said "seed": true for the first round.
            Object sd = o.opt("seed");
            seed = sd instanceof Boolean ? ((Boolean) sd ? 1 : 0) : o.optInt("seed");
            category = o.optString("category", "Other");
            pages = new ArrayList<>();
            JSONArray pg = o.optJSONArray("pages");
            for (int i = 0; pg != null && i < pg.length(); i++) {
                JSONObject page = pg.optJSONObject(i);
                if (page != null) pages.add(new String[]{page.optString("name"), page.optString("url")});
            }
            feeds = new HashSet<>();
            JSONArray f = o.optJSONArray("feeds");
            for (int i = 0; f != null && i < f.length(); i++) feeds.add(f.optString(i));
        }
    }

    private static List<Type> types;
    private static List<String> categories;

    private static final class Parsed {
        int revision; // bumped whenever catalog.json changes, so an older copy never wins
        final List<Type> types = new ArrayList<>();
        final List<String> categories = new ArrayList<>();
    }

    static synchronized List<Type> all(Context c) {
        if (types != null) return types;
        Parsed p = bundled(c);
        File cached = cache(c);
        if (cached.exists()) {
            try (InputStream in = new FileInputStream(cached)) {
                Parsed remote = parse(read(in));
                if (remote.revision >= p.revision) p = merge(remote, p);
            } catch (Exception e) {
                cached.delete();
            }
        }
        types = p.types;
        categories = p.categories;
        return types;
    }

    /** Fetches the catalog from GitHub and keeps it if it's valid. Blocking; failures are ignored. */
    static void refresh(Context c) {
        try {
            Object o = Net.json(REMOTE, null);
            if (!(o instanceof JSONObject)) return;
            String json = o.toString();
            // Throws if it isn't usable; an older revision than the APK's own is no use either.
            if (parse(json).revision < bundled(c).revision) return;
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

    private static Parsed bundled(Context c) {
        try (InputStream in = c.getAssets().open("catalog.json")) {
            return parse(read(in));
        } catch (Exception e) {
            throw new IllegalStateException("catalog.json is missing or broken", e);
        }
    }

    /** The newer catalog, plus any app or category only the APK's own catalog knows. */
    private static Parsed merge(Parsed newer, Parsed own) {
        for (Type t : own.types) {
            boolean have = false;
            for (Type n : newer.types) have |= n.id.equals(t.id);
            if (!have) newer.types.add(t);
        }
        for (String cat : own.categories) if (!newer.categories.contains(cat)) newer.categories.add(cat);
        return newer;
    }

    private static Parsed parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version") > FORMAT) throw new IllegalArgumentException("newer catalog format");
        Parsed p = new Parsed();
        p.revision = root.optInt("revision");
        JSONArray c = root.optJSONArray("categories");
        for (int i = 0; c != null && i < c.length(); i++) p.categories.add(c.optString(i));
        JSONArray apps = root.getJSONArray("apps");
        List<Type> list = p.types;
        boolean custom = false;
        for (int i = 0; i < apps.length(); i++) {
            Type t = new Type(apps.getJSONObject(i));
            if (t.id.isEmpty()) continue;
            custom |= t.id.equals("custom");
            list.add(t);
        }
        if (!custom) throw new IllegalArgumentException("catalog has no custom type");
        for (Type t : list) if (!p.categories.contains(t.category)) p.categories.add(t.category);
        return p;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    /** Category names in display order. */
    static synchronized List<String> categories(Context c) {
        all(c);
        return categories;
    }

    /** The newest round of default apps in the catalog. */
    static int seedRound(Context c) {
        int max = 0;
        for (Type t : all(c)) max = Math.max(max, t.seed);
        return max;
    }

    private static Type android;

    static Type get(Context c, String id) {
        if (Launcher.TYPE.equals(id)) {
            // Android apps on the home row in launcher mode: built in, never in catalog.json.
            if (android == null) {
                try {
                    android = new Type(new JSONObject().put("id", Launcher.TYPE).put("name", "Android app").put("category", Launcher.CATEGORY));
                } catch (org.json.JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
            return android;
        }
        for (Type t : all(c)) if (t.id.equals(id)) return t;
        for (Type t : all(c)) if (t.id.equals("custom")) return t;
        throw new IllegalStateException("catalog.json has no custom type");
    }
}
