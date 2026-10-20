package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The dashboard widgets: which exist, which are shown and in what order, and the user's location. */
final class Widgets {

    static final class Def {
        final String id, feed, category;
        final int title, icon;
        final long refreshMs;

        Def(String id, int title, int icon, String category, String feed, long refreshMs) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.category = category;
            this.feed = feed;
            this.refreshMs = refreshMs;
        }
    }

    private static final long MIN = 60_000;

    /** feed: the catalog feed an app must offer to supply it; "location" needs no app. */
    static final List<Def> ALL = Arrays.asList(
            new Def("space", R.string.w_space, R.drawable.ic_sun, "Ham radio", "space", 2 * MIN),
            new Def("pota", R.string.w_pota, R.drawable.ic_park, "Ham radio", "pota", 2 * MIN),
            new Def("dx", R.string.w_dx, R.drawable.ic_waves, "Ham radio", "dx", MIN),
            new Def("contests", R.string.w_contests, R.drawable.ic_trophy, "Ham radio", "contests", 30 * MIN),
            new Def("dxpeditions", R.string.w_dxpeditions, R.drawable.ic_globe, "Ham radio", "dxpeditions", 60 * MIN),
            new Def("satellites", R.string.w_satellites, R.drawable.ic_satellite, "Satellites", "satellites", 10 * MIN),
            new Def("satnogs", R.string.w_satnogs, R.drawable.ic_satellite, "Satellites", "satnogs", 5 * MIN),
            new Def("mesh_sources", R.string.w_mesh_sources, R.drawable.ic_mesh, "MeshMonitor", "mesh", MIN),
            new Def("mesh_nodes", R.string.w_mesh_nodes, R.drawable.ic_mesh, "MeshMonitor", "mesh", MIN),
            new Def("mesh_messages", R.string.w_mesh_messages, R.drawable.ic_chat, "MeshMonitor", "mesh", MIN),
            new Def("aircraft", R.string.w_aircraft, R.drawable.ic_plane, "Aviation & marine", "aircraft", MIN / 2),
            new Def("ships", R.string.w_ships, R.drawable.ic_ship, "Aviation & marine", "ships", MIN),
            new Def("weather", R.string.w_weather, R.drawable.ic_cloud, "Local", "location", 15 * MIN));

    private static final List<String> DEFAULT_SHOWN =
            Arrays.asList("space", "pota", "aircraft", "weather", "dx", "mesh_messages");

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("widgets", Context.MODE_PRIVATE);
    }

    static Def get(String id) {
        for (Def d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    /** The shown widgets, in the user's order. */
    static List<String> shown(Context c) {
        String raw = prefs(c).getString("shown", null);
        if (raw == null) return new ArrayList<>(DEFAULT_SHOWN);
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) if (get(a.optString(i)) != null) out.add(a.optString(i));
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_SHOWN);
        }
        return out;
    }

    static void setShown(Context c, List<String> ids) {
        prefs(c).edit().putString("shown", new JSONArray(ids).toString()).apply();
    }

    static boolean rotate(Context c) { return prefs(c).getBoolean("rotate", true); }

    static void setRotate(Context c, boolean on) { prefs(c).edit().putBoolean("rotate", on).apply(); }

    // --- Location ----------------------------------------------------------------------------

    static String location(Context c) { return prefs(c).getString("location", ""); }

    static void setLocation(Context c, String s) { prefs(c).edit().putString("location", s.trim()).apply(); }

    /** {lat, lon} from the setting, or null if it isn't set or can't be read. */
    static double[] latLon(Context c) {
        return parseLocation(location(c));
    }

    /** Reads a Maidenhead grid square ("FN31pr") or "lat, lon" ("40.7, -74.0"). */
    static double[] parseLocation(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split("[,\\s]+");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) return new double[]{lat, lon};
            } catch (NumberFormatException ignored) {
                // Not numbers; try it as a grid square.
            }
        }
        return grid(s);
    }

    /** The centre of a 4- or 6-character Maidenhead locator. */
    static double[] grid(String g) {
        g = g.toUpperCase(Locale.US);
        if (!g.matches("[A-R]{2}[0-9]{2}([A-X]{2})?")) return null;
        double lon = (g.charAt(0) - 'A') * 20 - 180 + (g.charAt(2) - '0') * 2;
        double lat = (g.charAt(1) - 'A') * 10 - 90 + (g.charAt(3) - '0');
        if (g.length() == 6) {
            lon += (g.charAt(4) - 'A') * (2.0 / 24) + 1.0 / 24;
            lat += (g.charAt(5) - 'A') * (1.0 / 24) + 0.5 / 24;
        } else {
            lon += 1;
            lat += 0.5;
        }
        return new double[]{lat, lon};
    }

    /** Great-circle distance in nautical miles. */
    static double nm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 3440.065 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Fahrenheit and mph where people expect them. */
    static boolean imperial() {
        String cc = Locale.getDefault().getCountry();
        return cc.equals("US") || cc.equals("LR") || cc.equals("MM");
    }
}
