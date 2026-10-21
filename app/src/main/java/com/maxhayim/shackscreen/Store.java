package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The user's apps: each is a catalog type pointed at a public site or their own server.
 * Kept on the device only, so private addresses and tokens never leave it.
 */
final class Store {

    static final class Instance {
        String id, type, name, url, token;
        String icon = ""; // "" for the site's own icon, "symbol:<drawable>", or "file" for a custom image
        int zoom; // page size in percent, 0 for the catalog's
        boolean home; // on the home row; otherwise only in All apps
        List<String[]> pages = new ArrayList<>(); // {name, url}: extra pages the user added

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("type", type).put("name", name)
                    .put("url", url).put("token", token == null ? "" : token).put("home", home)
                    .put("pages", pagesJson()).put("icon", icon).put("zoom", zoom);
        }

        private JSONArray pagesJson() throws Exception {
            JSONArray a = new JSONArray();
            for (String[] p : pages) a.put(new JSONObject().put("name", p[0]).put("url", p[1]));
            return a;
        }

        static Instance fromJson(JSONObject o) {
            Instance i = new Instance();
            i.id = o.optString("id");
            i.type = o.optString("type");
            i.name = o.optString("name");
            i.url = o.optString("url");
            i.token = o.optString("token");
            i.icon = o.optString("icon");
            i.zoom = o.optInt("zoom");
            // Names the catalog has since shortened, still on apps added before.
            if (i.type.equals("meshmap") && i.name.equals("MeshMap (Meshtastic)")) i.name = "MeshMap";
            // Lists saved before the home row existed: start with the usual favourites there.
            i.home = o.has("home") ? o.optBoolean("home") : DEFAULT_HOME.contains(i.type);
            JSONArray pg = o.optJSONArray("pages");
            for (int k = 0; pg != null && k < pg.length(); k++) {
                JSONObject p = pg.optJSONObject(k);
                if (p != null) i.pages.add(new String[]{p.optString("name"), p.optString("url")});
            }
            return i;
        }

        boolean hasUrl() { return url != null && !url.isEmpty(); }
    }

    private static final java.util.Set<String> DEFAULT_HOME = new java.util.HashSet<>(java.util.Arrays.asList(
            "openhamclock", "shackboard", "websdr", "broadcastify", "n2yo", "flightradar24", "marinetraffic", "meshmap",
            "meshmonitor", "custom"));

    private static final String PREFS = "shackscreen", KEY = "instances", KEY_SEEDED = "seeded";

    static List<Instance> load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, null);
        List<Instance> list = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) list.add(Instance.fromJson(a.getJSONObject(i)));
            } catch (Exception ignored) {
                // A broken list is replaced by the next save rather than crashing the dashboard.
            }
        }
        // New default apps arrive in rounds. Each round is added once, so an app the user removed
        // stays removed. Installs from before rounds existed had round 1.
        int done = p.getInt(KEY_SEEDED, raw == null ? 0 : 1), latest = Catalog.seedRound(c);
        if (done < latest) {
            for (Catalog.Type t : Catalog.all(c)) {
                if (t.seed <= done || t.seed > latest) continue;
                boolean have = false;
                for (Instance i : list) have |= i.type.equals(t.id);
                if (!have) list.add(create(t));
            }
            save(c, list);
            p.edit().putInt(KEY_SEEDED, latest).apply();
        }
        return list;
    }

    static void save(Context c, List<Instance> list) {
        JSONArray a = new JSONArray();
        try {
            for (Instance i : list) a.put(i.toJson());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
        List<String> ids = new ArrayList<>();
        for (Instance i : list) ids.add(i.id);
        Logins.prune(c, ids); // a deleted app's logins go with it
    }

    /** The apps on the home row, in order. */
    static List<Instance> home(List<Instance> all) {
        List<Instance> out = new ArrayList<>();
        for (Instance i : all) if (i.home) out.add(i);
        return out;
    }

    /** Where All apps sits on the home row: a slot index, or -1 for the end (where new apps go before it). */
    static int allAppsAt(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("allAppsAt", -1);
    }

    /**
     * Saves a new home row order. slots is the row as shown, with null standing for All apps; the
     * home apps keep their places in the full list, just in the new order.
     */
    static void saveHome(Context c, List<Instance> all, List<Instance> slots) {
        List<Integer> places = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) if (all.get(i).home) places.add(i);
        int k = 0, allApps = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == null) allApps = i;
            else all.set(places.get(k++), slots.get(i));
        }
        save(c, all);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("allAppsAt", allApps == slots.size() - 1 ? -1 : allApps).apply();
    }

    static Instance find(Context c, String id) {
        for (Instance i : load(c)) if (i.id.equals(id)) return i;
        return null;
    }

    static Instance create(Catalog.Type t) {
        Instance i = new Instance();
        i.id = UUID.randomUUID().toString();
        i.type = t.id;
        i.name = t.name;
        i.url = t.url;
        i.token = "";
        i.home = t.home;
        return i;
    }

    /**
     * Turns what was typed on the remote into a URL: "192.168.1.50" becomes
     * "http://192.168.1.50:3000" for OpenHamClock. A scheme or port typed by the user is kept.
     */
    static String normalise(String typed, int defaultPort) {
        String s = typed.trim();
        if (s.isEmpty()) return "";
        boolean hadScheme = s.contains("://");
        if (!hadScheme) s = "http://" + s;
        Uri u = Uri.parse(s);
        if (!hadScheme && u.getPort() == -1 && defaultPort > 0 && u.getHost() != null) {
            s = u.buildUpon().encodedAuthority(u.getHost() + ":" + defaultPort).build().toString();
        }
        return s;
    }

    /** An address typed without a path gets the app's own start page (HamClock's /live.html). */
    static String withPath(String url, String path) {
        if (url.isEmpty() || path.isEmpty()) return url;
        String p = Uri.parse(url).getEncodedPath();
        if (p != null && !p.isEmpty() && !p.equals("/")) return url;
        return (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) + path;
    }

    /** Base URL plus an API path, without doubling the slash. */
    static String join(String base, String path) {
        Uri u = Uri.parse(base);
        String root = u.getScheme() + "://" + u.getEncodedAuthority();
        return root + path;
    }
}
