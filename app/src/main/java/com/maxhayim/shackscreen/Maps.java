package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Map styles for the location picker: free tile servers that allow apps like this one, maps from
 * providers that need the user's own API key (CARTO, Google Maps, Mapbox and others), and any
 * tile address ({z}/{x}/{y}), such as a map from their own server. Keys are encrypted and never
 * leave the device.
 */
final class Maps {

    static final class Style {
        final String id, name, url, attribution;
        String labels = ""; // place names drawn over a base map that has none
        String provider = ""; // a keyed provider's id (see PROVIDERS); "" for a plain tile address
        String key = "";      // that provider's API key, decrypted; stays on this device

        Style(String id, String name, String url, String attribution) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.attribution = attribution;
        }

        Style labels(String url) {
            labels = url;
            return this;
        }
    }

    // All free to use without a key. (CARTO's basemaps now need one, so they're not here.)
    static final Style[] BUILT_IN = {
            new Style("dark", "Dark (Esri)", "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}", "© Esri, HERE, Garmin, © OpenStreetMap contributors")
                    .labels("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}"),
            new Style("osm", "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
            new Style("street", "Streets (Esri)", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}", "© Esri, HERE, Garmin, © OpenStreetMap contributors"),
            new Style("topo", "OpenTopoMap", "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors, SRTM © OpenTopoMap"),
            new Style("hot", "Humanitarian (OSM)", "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", "© OpenStreetMap contributors, HOT"),
            new Style("satellite", "Satellite (Esri)", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "© Esri, Maxar, Earthstar Geographics")
                    .labels("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}")};

    /** A provider that needs the user's key: its styles as {name, path}, and its tile address with {style} and {key}. */
    static final class Provider {
        final String id, name, url, attribution;
        final String[][] styles;

        Provider(String id, String name, String url, String attribution, String[][] styles) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.attribution = attribution;
            this.styles = styles;
        }
    }

    static final Provider[] PROVIDERS = {
            new Provider("carto", "CARTO", "https://{s}.basemaps.cartocdn.com/{style}/{z}/{x}/{y}.png?key={key}", "© OpenStreetMap contributors © CARTO",
                    new String[][]{{"Dark", "dark_all"}, {"Light", "light_all"}, {"Voyager", "rastertiles/voyager"}}),
            // Google's Map Tiles API: the style is the map type, and a session is opened when the map loads.
            new Provider("google", "Google Maps", "", "© Google",
                    new String[][]{{"Roadmap", "roadmap"}, {"Satellite", "satellite"}, {"Terrain", "terrain"}}),
            new Provider("mapbox", "Mapbox", "https://api.mapbox.com/styles/v1/mapbox/{style}/tiles/256/{z}/{x}/{y}?access_token={key}", "© Mapbox © OpenStreetMap contributors",
                    new String[][]{{"Dark", "dark-v11"}, {"Streets", "streets-v12"}, {"Satellite Streets", "satellite-streets-v12"}}),
            new Provider("maptiler", "MapTiler", "https://api.maptiler.com/maps/{style}/256/{z}/{x}/{y}.png?key={key}", "© MapTiler © OpenStreetMap contributors",
                    new String[][]{{"Streets", "streets-v2"}, {"Dark", "dataviz-dark"}, {"Topo", "topo-v2"}}),
            new Provider("thunderforest", "Thunderforest", "https://{s}.tile.thunderforest.com/{style}/{z}/{x}/{y}.png?apikey={key}", "© Thunderforest © OpenStreetMap contributors",
                    new String[][]{{"Outdoors", "outdoors"}, {"Transport", "transport"}, {"Landscape", "landscape"}}),
            new Provider("stadia", "Stadia Maps", "https://tiles.stadiamaps.com/tiles/{style}/{z}/{x}/{y}.png?api_key={key}", "© Stadia Maps © OpenStreetMap contributors",
                    new String[][]{{"Alidade Smooth Dark", "alidade_smooth_dark"}, {"Alidade Smooth", "alidade_smooth"}, {"OSM Bright", "osm_bright"}})};

    static Provider provider(String id) {
        for (Provider p : PROVIDERS) if (p.id.equals(id)) return p;
        return null;
    }

    private static final String PREFS = "maps";

    private Maps() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Built-in styles, then the user's own. */
    static List<Style> all(Context c) {
        List<Style> out = new ArrayList<>(java.util.Arrays.asList(BUILT_IN));
        out.addAll(custom(c));
        return out;
    }

    static List<Style> custom(Context c) {
        List<Style> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("custom", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Provider p = provider(o.optString("provider"));
                Style st = new Style("custom:" + i, o.optString("name"), o.optString("url"), p != null ? p.attribution : o.optString("name"));
                st.provider = o.optString("provider");
                st.key = Logins.unseal(o.optString("key"));
                out.add(st);
            }
        } catch (Exception ignored) {
            // A broken list is treated as empty and replaced by the next save.
        }
        return out;
    }

    static void addCustom(Context c, String name, String url) {
        List<Style> list = custom(c);
        list.add(new Style("", name, url, name));
        saveCustom(c, list);
    }

    /**
     * Adds a map from a keyed provider and makes it the one in use. False if the key couldn't be
     * encrypted on this device (Android 6 or later is needed).
     */
    static boolean addKeyed(Context c, Provider p, String[] style, String key) {
        if (Logins.seal(key) == null) return false;
        List<Style> list = custom(c);
        String url = p.id.equals("google") ? style[1] : p.url.replace("{style}", style[1]);
        Style st = new Style("", p.name + " " + style[0], url, p.attribution);
        st.provider = p.id;
        st.key = key;
        list.add(st);
        saveCustom(c, list);
        choose(c, custom(c).get(list.size() - 1));
        return true;
    }

    static void removeCustom(Context c, Style s) {
        List<Style> list = custom(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(s.id)) list.remove(i);
        }
        saveCustom(c, list);
        if (s.id.equals(prefs(c).getString("style", ""))) prefs(c).edit().remove("style").apply();
    }

    static void saveCustom(Context c, List<Style> list) {
        JSONArray a = new JSONArray();
        try {
            for (Style s : list) {
                JSONObject o = new JSONObject().put("name", s.name).put("url", s.url);
                if (!s.provider.isEmpty()) o.put("provider", s.provider).put("key", Logins.seal(s.key));
                a.put(o);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        prefs(c).edit().putString("custom", a.toString()).apply();
    }

    /** The chosen style: Dark unless the user picked another. */
    static Style current(Context c) {
        String id = prefs(c).getString("style", "dark");
        for (Style s : all(c)) if (s.id.equals(id)) return s;
        return BUILT_IN[0];
    }

    static void choose(Context c, Style s) {
        prefs(c).edit().putString("style", s.id).apply();
    }

    /** The tile address with the key filled in. (Google's comes from its session; see MapActivity.) */
    static String tiles(Style s) {
        return s.url.replace("{key}", android.net.Uri.encode(s.key));
    }

    /** What to show for a map in Settings: never the key. */
    static String describe(Style s) {
        Provider p = provider(s.provider);
        return p != null ? p.name + "   ·   " + (s.key.isEmpty() ? "key missing" : "your key") : android.net.Uri.parse(s.url).getHost();
    }

    /** A tile address the map can use: it must have {z}, {x} and {y}. */
    static boolean valid(String url) {
        return (url.startsWith("https://") || url.startsWith("http://"))
                && url.contains("{z}") && url.contains("{x}") && url.contains("{y}");
    }
}
