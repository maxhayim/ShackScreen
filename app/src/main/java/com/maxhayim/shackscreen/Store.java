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

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("type", type).put("name", name)
                    .put("url", url).put("token", token == null ? "" : token);
        }

        static Instance fromJson(JSONObject o) {
            Instance i = new Instance();
            i.id = o.optString("id");
            i.type = o.optString("type");
            i.name = o.optString("name");
            i.url = o.optString("url");
            i.token = o.optString("token");
            return i;
        }

        boolean hasUrl() { return url != null && !url.isEmpty(); }
    }

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
