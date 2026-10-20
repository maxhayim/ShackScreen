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
        boolean home; // on the home row; otherwise only in All apps

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("type", type).put("name", name)
                    .put("url", url).put("token", token == null ? "" : token).put("home", home);
        }

        static Instance fromJson(JSONObject o) {
            Instance i = new Instance();
            i.id = o.optString("id");
            i.type = o.optString("type");
            i.name = o.optString("name");
            i.url = o.optString("url");
            i.token = o.optString("token");
            // Lists saved before the home row existed: start with the usual favourites there.
            i.home = o.has("home") ? o.optBoolean("home") : DEFAULT_HOME.contains(i.type);
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
    }

    /** The apps on the home row, in order. */
    static List<Instance> home(List<Instance> all) {
        List<Instance> out = new ArrayList<>();
        for (Instance i : all) if (i.home) out.add(i);
        return out;
    }

    /** Swaps an app with its neighbour on the home row (delta -1 or 1); the full list keeps the rest in place. */
    static void moveOnHome(Context c, List<Instance> all, Instance in, int delta) {
        List<Instance> home = home(all);
        int at = home.indexOf(in), to = at + delta;
        if (at < 0 || to < 0 || to >= home.size()) return;
        int a = all.indexOf(in), b = all.indexOf(home.get(to));
        all.set(a, home.get(to));
        all.set(b, in);
        save(c, all);
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

    /** Base URL plus an API path, without doubling the slash. */
    static String join(String base, String path) {
        Uri u = Uri.parse(base);
        String root = u.getScheme() + "://" + u.getEncodedAuthority();
        return root + path;
    }
}
