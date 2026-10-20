package com.maxhayim.shackscreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Reads each app's API into plain data for the dashboard widgets. Every method blocks and throws
 * IOException when the server can't be read, so the caller can try the next app.
 */
final class Feeds {

    static final String[] BANDS = {"80m-40m", "30m-20m", "17m-15m", "12m-10m"};
    static final int RADIUS_NM = 25;
    private static final int MAX_ROWS = 6, MAX_MESSAGES = 3;

    /** Chip colours. */
    static final int ACCENT = 0, GOOD = 1, FAIR = 2, POOR = 3, DIM = 4;

    /** One line of a list widget: a bold name, a blue number, a coloured chip, and dim text on the right. */
    static final class Row {
        String main, num, chip, trail;
        int tone = ACCENT;

        Row(String main, String num, String chip, String trail) {
            this.main = main;
            this.num = num;
            this.chip = chip;
            this.trail = trail;
        }
    }

    static final class Rows {
        String header; // optional line above the rows
        final List<Row> rows = new ArrayList<>();
        String empty;  // shown when there are no rows
    }

    static final class Space {
        String sfi, ssn, k, a, xray;
        /** band -> {day, night}, in BANDS order */
        final Map<String, String[]> bands = new LinkedHashMap<>();
    }

    static final class Weather {
        String temp, feels, sky, wind, humidity, pressure, high, low, uv;
        int code;
        boolean day = true;
        String sunrise, sunset; // "HH:mm", local to the place
        final List<Hour> hours = new ArrayList<>();
    }

    /** One of the next hours: its time ("HH:mm"), weather code, temperature and chance of rain. */
    static final class Hour {
        String time, temp;
        int code, rain;
        boolean day;
    }

    static final class Mesh {
        boolean needsToken;
        int nodes;
        final List<String[]> messages = new ArrayList<>(); // {from, text, source type}
    }

    // --- Ham radio ---------------------------------------------------------------------------

    static Space space(String type, Store.Instance in) throws IOException {
        Space s = new Space();
        Map<String, String[]> found = new LinkedHashMap<>();
        if (type.equals("openhamclock")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/n0nbh"), null));
            JSONObject d = o.optJSONObject("solarData");
            if (d == null) throw new IOException("no solarData");
            s.sfi = d.optString("solarFlux"); s.ssn = d.optString("sunspots");
            s.k = d.optString("kIndex"); s.a = d.optString("aIndex"); s.xray = d.optString("xray");
            JSONArray bc = o.optJSONArray("bandConditions");
            for (int i = 0; bc != null && i < bc.length(); i++) {
                JSONObject b = bc.optJSONObject(i);
                if (b == null) continue;
                // get/put rather than computeIfAbsent, which needs API 24.
                String[] row = found.get(b.optString("name"));
                if (row == null) found.put(b.optString("name"), row = new String[2]);
                row["night".equals(b.optString("time")) ? 1 : 0] = b.optString("condition");
            }
        } else if (type.equals("shackboard")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/space-weather"), null));
            s.sfi = o.optString("sfi"); s.ssn = o.optString("sunspots");
            s.k = trim(o.optDouble("kIndex")); s.a = o.optString("aIndex"); s.xray = o.optString("xrayFlux");
            JSONObject hf = o.optJSONObject("hfConditions");
            for (String band : BANDS) {
                JSONObject b = hf == null ? null : hf.optJSONObject(band);
                if (b != null) found.put(band, new String[]{b.optString("day"), b.optString("night")});
            }
        } else {
            throw new IOException(type + " has no space weather");
        }
        for (String band : BANDS) if (found.containsKey(band)) s.bands.put(band, found.get(band));
        return s;
    }

    static Rows pota(String type, Store.Instance in) throws IOException {
        JSONArray raw;
        if (type.equals("openhamclock")) {
            raw = arr(Net.json(Store.join(in.url, "/api/pota/spots"), null));
        } else if (type.equals("shackboard")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/spots?limit=100"), null));
            raw = o.optJSONArray("spots");
            if (raw == null) throw new IOException("no spots");
        } else {
            throw new IOException(type + " has no POTA spots");
        }
        Rows r = new Rows();
        r.empty = "No spots right now";
        for (int i = 0; i < raw.length() && r.rows.size() < MAX_ROWS; i++) {
            JSONObject o = raw.optJSONObject(i);
            if (o == null) continue;
            // ShackBoard mixes in DX spots, which it simulates when its cluster feed is down; keep POTA only.
            if (type.equals("shackboard") && !"POTA".equals(o.optString("source"))) continue;
            r.rows.add(new Row(o.optString("activator"), mhz(o.opt("frequency")), o.optString("mode"), o.optString("reference")));
        }
        return r;
    }

    static Rows dx(String type, Store.Instance in) throws IOException {
        JSONArray raw = arr(Net.json(Store.join(in.url, "/api/dxcluster/spots"), null));
        Rows r = new Rows();
        r.empty = "No spots right now";
        for (int i = 0; i < raw.length() && r.rows.size() < MAX_ROWS; i++) {
            JSONObject o = raw.optJSONObject(i);
            if (o == null) continue;
            r.rows.add(new Row(o.optString("call"), o.optString("freq"), o.optString("mode"), o.optString("band")));
        }
        return r;
    }

    static Rows contests(String type, Store.Instance in) throws IOException {
        JSONArray raw = obj(Net.json(Store.join(in.url, "/api/contests"), null)).optJSONArray("contests");
        if (raw == null) throw new IOException("no contests");
        Rows r = new Rows();
        r.empty = "No contests listed";
        for (int i = 0; i < raw.length() && r.rows.size() < 5; i++) {
            JSONObject o = raw.optJSONObject(i);
            if (o == null) continue;
            boolean active = "active".equals(o.optString("status"));
            Row row = new Row(o.optString("name"), null, active ? "On now" : "Soon",
                    active ? "ends " + utc(o.optString("end")) : utc(o.optString("start")));
            row.tone = active ? GOOD : DIM;
            r.rows.add(row);
        }
        return r;
    }

    static Rows dxpeditions(String type, Store.Instance in) throws IOException {
        JSONArray raw = obj(Net.json(Store.join(in.url, "/api/dxpeditions"), null)).optJSONArray("dxpeditions");
        if (raw == null) throw new IOException("no dxpeditions");
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) if (raw.optJSONObject(i) != null) list.add(raw.optJSONObject(i));
        // On the air now first.
        Collections.sort(list, (a, b) -> Boolean.compare(b.optBoolean("isActive"), a.optBoolean("isActive")));
        Rows r = new Rows();
        r.empty = "None listed";
        for (JSONObject o : list) {
            if (r.rows.size() >= MAX_ROWS) break;
            boolean active = o.optBoolean("isActive");
            Row row = new Row(o.optString("callsign"), null, active ? "QRV" : "Soon", o.optString("entity"));
            row.tone = active ? GOOD : DIM;
            r.rows.add(row);
        }
        return r;
    }

    // --- Aviation and marine -----------------------------------------------------------------

    /**
     * Aircraft near the user from adsb.lol or adsb.fi (by location), or from the user's own
     * readsb / tar1090 / dump1090 receiver (its aircraft.json).
     */
    static Rows aircraft(String type, Store.Instance in, double[] here) throws IOException {
        JSONArray ac;
        if (type.equals("adsblol") || type.equals("adsbfi")) {
            if (here == null) throw new NeedsLocation();
            String url = type.equals("adsblol")
                    ? String.format(Locale.US, "https://api.adsb.lol/v2/lat/%.4f/lon/%.4f/dist/%d", here[0], here[1], RADIUS_NM)
                    : String.format(Locale.US, "https://opendata.adsb.fi/api/v2/lat/%.4f/lon/%.4f/dist/%d", here[0], here[1], RADIUS_NM);
            JSONObject o = obj(Net.json(url, null));
            ac = o.has("ac") ? o.optJSONArray("ac") : o.optJSONArray("aircraft");
        } else {
            JSONObject o = receiverJson(in.url, "data/aircraft.json");
            ac = o.optJSONArray("aircraft");
        }
        if (ac == null) throw new IOException("no aircraft");
        List<double[]> order = new ArrayList<>(); // {distance, index}
        for (int i = 0; i < ac.length(); i++) {
            JSONObject a = ac.optJSONObject(i);
            if (a == null) continue;
            double d = a.optDouble("dst", Double.NaN);
            if (Double.isNaN(d) && here != null && a.has("lat")) d = Widgets.nm(here[0], here[1], a.optDouble("lat"), a.optDouble("lon"));
            // A receiver hears far beyond the radius; keep the dashboard about what's overhead.
            if (!Double.isNaN(d) && d > RADIUS_NM) continue;
            order.add(new double[]{Double.isNaN(d) ? Double.MAX_VALUE : d, i});
        }
        Collections.sort(order, (x, y) -> Double.compare(x[0], y[0]));
        Rows r = new Rows();
        r.header = order.size() + " within " + RADIUS_NM + " nm";
        r.empty = "No aircraft within " + RADIUS_NM + " nm";
        for (double[] e : order) {
            if (r.rows.size() >= MAX_ROWS) break;
            JSONObject a = ac.optJSONObject((int) e[1]);
            String call = a.optString("flight").trim();
            if (call.isEmpty()) call = a.optString("r", a.optString("hex").toUpperCase(Locale.US));
            Object alt = a.opt("alt_baro");
            String altText = alt instanceof Number ? flightLevel(((Number) alt).intValue()) : "GND";
            r.rows.add(new Row(call, altText, a.optString("t", ""), e[0] == Double.MAX_VALUE ? "" : String.format(Locale.US, "%.0f nm", e[0])));
        }
        return r;
    }

    /** Ships from the user's AIS-catcher, nearest first when it knows the distance. */
    static Rows ships(String type, Store.Instance in, double[] here) throws IOException {
        Object o = Net.json(Store.join(in.url, "/api/ships.json"), null);
        JSONArray list = o instanceof JSONArray ? (JSONArray) o
                : ((JSONObject) o).has("ships") ? ((JSONObject) o).optJSONArray("ships") : ((JSONObject) o).optJSONArray("values");
        if (list == null) throw new IOException("no ships");
        List<double[]> order = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            if (s == null) continue;
            double d = s.optDouble("distance", Double.NaN);
            if (Double.isNaN(d) && here != null && s.has("lat") && !s.isNull("lat")) d = Widgets.nm(here[0], here[1], s.optDouble("lat"), s.optDouble("lon"));
            order.add(new double[]{Double.isNaN(d) ? Double.MAX_VALUE : d, i});
        }
        Collections.sort(order, (x, y) -> Double.compare(x[0], y[0]));
        Rows r = new Rows();
        r.header = order.size() + " ships tracked";
        r.empty = "No ships right now";
        for (double[] e : order) {
            if (r.rows.size() >= MAX_ROWS) break;
            JSONObject s = list.optJSONObject((int) e[1]);
            String name = s.optString("shipname").trim();
            if (name.isEmpty()) name = String.valueOf(s.optLong("mmsi"));
            double kn = s.optDouble("speed", Double.NaN);
            Row row = new Row(name, Double.isNaN(kn) ? null : String.format(Locale.US, "%.1f kn", kn), null, s.optString("destination").trim());
            r.rows.add(row);
        }
        return r;
    }

    // --- Satellites --------------------------------------------------------------------------

    /** The next passes over the user of the amateur and weather satellites OpenHamClock tracks. */
    static Rows satellites(String type, Store.Instance in, double[] here) throws IOException {
        if (here == null) throw new NeedsLocation();
        JSONObject data = obj(Net.json(Store.join(in.url, "/api/satellites/data"), null)).optJSONObject("data");
        if (data == null) throw new IOException("no satellites");
        long now = System.currentTimeMillis();
        List<Object[]> passes = new ArrayList<>(); // {rise, Row}
        for (java.util.Iterator<String> it = data.keys(); it.hasNext(); ) {
            JSONObject sat = data.optJSONObject(it.next());
            JSONObject omm = sat == null ? null : sat.optJSONObject("omm");
            if (omm == null) continue;
            Orbit orbit;
            try {
                orbit = new Orbit(omm);
            } catch (Exception e) {
                continue;
            }
            if (!orbit.passes()) continue;
            String name = sat.optString("name").replaceAll("\\s*\\(.*\\)", "");
            String info = sat.isNull("downlink") ? sat.optString("mode", "") : sat.optString("downlink");
            if (info.equals("null")) info = "";
            for (Orbit.Pass p : orbit.passes(here[0], here[1], now, 24, 10)) {
                Row row = new Row(name, p.rise <= now ? "now" : hhmm(p.rise), Math.round(p.maxEl) + "°", info);
                row.tone = p.maxEl >= 45 ? GOOD : p.maxEl >= 20 ? FAIR : DIM;
                passes.add(new Object[]{p.rise, row});
            }
        }
        Collections.sort(passes, (x, y) -> Long.compare((Long) x[0], (Long) y[0]));
        Rows r = new Rows();
        r.empty = "No passes in the next day";
        for (Object[] e : passes) {
            if (r.rows.size() >= MAX_ROWS) break;
            r.rows.add((Row) e[1]);
        }
        return r;
    }

    /** Satellites the SatNOGS ground-station network just heard: frequency, mode and station. */
    static Rows satnogs(String type, Store.Instance in) throws IOException {
        JSONArray obs = arr(Net.json("https://network.satnogs.org/api/observations/?status=good&format=json", null));
        Rows r = new Rows();
        r.empty = "Nothing heard yet";
        for (int i = 0; i < obs.length() && r.rows.size() < MAX_ROWS; i++) {
            JSONObject o = obs.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("tle0").replaceFirst("^0 ", "").trim();
            if (name.isEmpty()) name = "NORAD " + o.optInt("norad_cat_id");
            long hz = o.optLong("observation_frequency", o.optLong("transmitter_downlink_low"));
            r.rows.add(new Row(name, hz > 0 ? String.format(Locale.US, "%.3f", hz / 1e6) : null,
                    o.optString("transmitter_mode"), o.optString("station_name")));
        }
        return r;
    }

    private static String hhmm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    // --- Local -------------------------------------------------------------------------------

    /** Current weather from Open-Meteo (free, no key) at the user's location. */
    static Weather weather(double[] here) throws IOException {
        if (here == null) throw new NeedsLocation();
        boolean imp = Widgets.imperial();
        String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_direction_10m,relative_humidity_2m,pressure_msl"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=6"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max&forecast_days=2&timezone=auto%s",
                here[0], here[1], imp ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
        JSONObject o = obj(Net.json(url, null));
        JSONObject cur = o.optJSONObject("current");
        if (cur == null) throw new IOException("no current weather");
        JSONObject daily = o.optJSONObject("daily");
        Weather w = new Weather();
        w.temp = Math.round(cur.optDouble("temperature_2m")) + "°";
        w.feels = Math.round(cur.optDouble("apparent_temperature")) + "°";
        w.code = cur.optInt("weather_code");
        w.day = cur.optInt("is_day", 1) == 1;
        w.sky = sky(w.code);
        w.wind = Math.round(cur.optDouble("wind_speed_10m")) + (imp ? " mph " : " km/h ") + compass(cur.optDouble("wind_direction_10m"));
        w.humidity = Math.round(cur.optDouble("relative_humidity_2m")) + "%";
        w.pressure = Math.round(cur.optDouble("pressure_msl")) + " hPa";
        if (daily != null) {
            w.high = Math.round(daily.optJSONArray("temperature_2m_max").optDouble(0)) + "°";
            w.low = Math.round(daily.optJSONArray("temperature_2m_min").optDouble(0)) + "°";
            JSONArray uv = daily.optJSONArray("uv_index_max");
            if (uv != null && !uv.isNull(0)) w.uv = String.valueOf(Math.round(uv.optDouble(0)));
            w.sunrise = clock(daily.optJSONArray("sunrise"));
            w.sunset = clock(daily.optJSONArray("sunset"));
        }
        JSONObject hourly = o.optJSONObject("hourly");
        JSONArray times = hourly == null ? null : hourly.optJSONArray("time");
        // The first hour is the current one, already shown above.
        for (int i = 1; times != null && i < times.length(); i++) {
            Hour h = new Hour();
            String t = times.optString(i);
            h.time = t.length() >= 16 ? t.substring(11, 16) : t;
            h.temp = Math.round(hourly.optJSONArray("temperature_2m").optDouble(i)) + "°";
            h.code = hourly.optJSONArray("weather_code").optInt(i);
            JSONArray rain = hourly.optJSONArray("precipitation_probability");
            h.rain = rain == null ? 0 : rain.optInt(i);
            JSONArray day = hourly.optJSONArray("is_day");
            h.day = day == null || day.optInt(i, 1) == 1;
            w.hours.add(h);
        }
        return w;
    }

    // --- MeshMonitor (Meshtastic, MeshCore and MQTT sources) ---------------------------------

    /** Each mesh source: its name, kind, whether it's connected, and nodes active in the last week. */
    static Rows meshSources(Store.Instance in) throws IOException {
        Rows r = new Rows();
        if (noToken(in, r)) return r;
        r.empty = "No sources";
        for (JSONObject src : sources(in)) {
            String id = src.optString("id");
            String base = Store.join(in.url, "/api/v1/sources/" + id);
            boolean connected = false;
            String nodes = "";
            try {
                JSONObject st = obj(Net.json(base + "/status", in.token)).optJSONObject("data");
                connected = st != null && st.optBoolean("connected");
            } catch (IOException ignored) {
                // Without info:read the status is hidden; show the source anyway.
            }
            try {
                JSONObject net = obj(Net.json(base + "/network", in.token)).optJSONObject("data");
                if (net != null) nodes = net.optInt("activeNodes") + "/" + net.optInt("totalNodes") + " nodes";
            } catch (IOException ignored) {
                // Without nodes:read there are no counts.
            }
            Row row = new Row(src.optString("name"), null, kind(src.optString("type")), nodes);
            row.tone = connected ? GOOD : POOR;
            r.rows.add(row);
        }
        return r;
    }

    /** Nodes heard today across all sources, most recent first. */
    static Rows meshNodes(Store.Instance in) throws IOException {
        Rows r = new Rows();
        if (noToken(in, r)) return r;
        List<Object[]> all = new ArrayList<>(); // {lastHeard, Row}
        for (JSONObject src : sources(in)) {
            JSONArray list = nodesOf(in, src.optString("id"));
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject n = list.optJSONObject(i);
                if (n == null) continue;
                String name = n.optString("longName", n.optString("shortName", n.optString("nodeId")));
                double snr = n.optDouble("snr", Double.NaN);
                int batt = n.optInt("batteryLevel", -1);
                Row row = new Row(name, Double.isNaN(snr) ? null : String.format(Locale.US, "%+.0f dB", snr),
                        kind(src.optString("type")), ago(n.optLong("lastHeard")) + (batt >= 0 && batt <= 100 ? " · " + batt + "%" : ""));
                row.tone = DIM;
                all.add(new Object[]{n.optLong("lastHeard"), row});
            }
        }
        Collections.sort(all, (a, b) -> Long.compare((Long) b[0], (Long) a[0]));
        r.header = all.size() + " heard today";
        r.empty = "No nodes heard today";
        for (Object[] e : all) {
            if (r.rows.size() >= MAX_ROWS) break;
            r.rows.add((Row) e[1]);
        }
        return r;
    }

    /** Node count and the latest messages across all sources. */
    static Mesh meshMessages(Store.Instance in) throws IOException {
        Mesh m = new Mesh();
        if (in.token == null || in.token.isEmpty()) {
            if (!healthy(in)) throw new IOException("MeshMonitor is offline");
            m.needsToken = true;
            return m;
        }
        List<Object[]> all = new ArrayList<>(); // {time, {from, text, kind}}
        for (JSONObject src : sources(in)) {
            String id = src.optString("id");
            Map<String, String> names = new LinkedHashMap<>();
            JSONArray nodes = nodesOf(in, id);
            for (int i = 0; nodes != null && i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n == null) continue;
                // optString turns a JSON null into "null"; a node without a short name keeps its id.
                String shortName = n.isNull("shortName") ? "" : n.optString("shortName").trim();
                names.put(n.optString("nodeId"), shortName.isEmpty() ? n.optString("nodeId") : shortName);
            }
            m.nodes += names.size();
            JSONArray msgs = obj(Net.json(Store.join(in.url, "/api/v1/sources/" + id + "/messages?limit=" + MAX_MESSAGES), in.token)).optJSONArray("data");
            for (int i = 0; msgs != null && i < msgs.length(); i++) {
                JSONObject msg = msgs.optJSONObject(i);
                if (msg == null) continue;
                String from = msg.isNull("fromNodeId") ? "" : msg.optString("fromNodeId");
                long t = msg.optLong("timestamp", msg.optLong("rxTime", 0));
                all.add(new Object[]{t, new String[]{names.containsKey(from) ? names.get(from) : from.isEmpty() ? "?" : from, msg.optString("text"), kind(src.optString("type"))}});
            }
        }
        Collections.sort(all, (a, b) -> Long.compare((Long) b[0], (Long) a[0]));
        for (Object[] e : all) {
            if (m.messages.size() >= MAX_MESSAGES) break;
            m.messages.add((String[]) e[1]);
        }
        return m;
    }

    /** One missed answer (a dropped packet, a busy server) isn't enough to call it offline. */
    private static boolean healthy(Store.Instance in) {
        String url = Store.join(in.url, "/api/health");
        return Net.up(url) || Net.up(url);
    }

    private static boolean noToken(Store.Instance in, Rows r) throws IOException {
        if (in.token != null && !in.token.isEmpty()) return false;
        if (!healthy(in)) throw new IOException("MeshMonitor is offline");
        r.header = "Online";
        r.empty = "Add an API token in Settings to see this";
        return true;
    }

    private static List<JSONObject> sources(Store.Instance in) throws IOException {
        JSONArray a = obj(Net.json(Store.join(in.url, "/api/v1/sources"), in.token)).optJSONArray("data");
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && s.optBoolean("enabled", true)) out.add(s);
        }
        return out;
    }

    private static JSONArray nodesOf(Store.Instance in, String sourceId) {
        try {
            return obj(Net.json(Store.join(in.url, "/api/v1/sources/" + sourceId + "/nodes?active=true&sinceDays=1"), in.token)).optJSONArray("data");
        } catch (IOException e) {
            return null; // the token may not have nodes:read on this source
        }
    }

    /** MeshMonitor's source types, as people say them. */
    static String kind(String type) {
        switch (type) {
            case "meshtastic_tcp": return "Meshtastic";
            case "meshcore": return "MeshCore";
            case "meshcore_mqtt": return "MeshCore MQTT";
            case "mqtt_bridge": case "mqtt_broker": return "MQTT";
            case "reticulum": return "Reticulum";
            default: return type.isEmpty() ? "Mesh" : type;
        }
    }

    // --- Helpers -----------------------------------------------------------------------------

    /** Thrown when a widget needs the user's location and it isn't set. */
    static final class NeedsLocation extends IOException {
        NeedsLocation() { super("location not set"); }
    }

    /**
     * A receiver's JSON at a path relative to the address the user gave (tar1090 often lives at
     * /tar1090/), falling back to the server root (dump1090-fa, readsb).
     */
    private static JSONObject receiverJson(String base, String path) throws IOException {
        String b = base.endsWith("/") ? base : base + "/";
        try {
            return obj(Net.json(b + path, null));
        } catch (IOException e) {
            return obj(Net.json(Store.join(base, "/" + path), null));
        }
    }

    private static String mhz(Object f) {
        if (f instanceof Number) return String.format(Locale.US, "%.3f", ((Number) f).doubleValue() / 1000);
        try {
            return String.format(Locale.US, "%.3f", Double.parseDouble(String.valueOf(f)) / 1000);
        } catch (NumberFormatException e) {
            return String.valueOf(f);
        }
    }

    private static String flightLevel(int ft) {
        return ft >= 18000 ? String.format(Locale.US, "FL%03d", ft / 100) : String.format(Locale.US, "%,d ft", ft);
    }

    private static String utc(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso);
            SimpleDateFormat out = new SimpleDateFormat("EEE HH:mm'Z'", Locale.US);
            out.setTimeZone(TimeZone.getTimeZone("UTC"));
            return out.format(d);
        } catch (Exception e) {
            return "";
        }
    }

    private static String ago(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        long s = System.currentTimeMillis() / 1000 - epochSeconds;
        if (s < 90) return "now";
        if (s < 3600) return (s / 60) + "m ago";
        return (s / 3600) + "h ago";
    }

    private static String compass(double deg) {
        if (Double.isNaN(deg)) return "";
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return d[(int) Math.round(((deg % 360) + 360) % 360 / 45) % 8];
    }

    /** WMO weather codes, as used by Open-Meteo. */
    /** "HH:mm" from the first of Open-Meteo's local times ("2026-10-20T19:01"). */
    private static String clock(JSONArray a) {
        String t = a == null ? "" : a.optString(0);
        return t.length() >= 16 ? t.substring(11, 16) : null;
    }

    private static String sky(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    private static JSONObject obj(Object o) throws IOException {
        if (o instanceof JSONObject) return (JSONObject) o;
        throw new IOException("expected a JSON object");
    }

    private static JSONArray arr(Object o) throws IOException {
        if (o instanceof JSONArray) return (JSONArray) o;
        throw new IOException("expected a JSON array");
    }

    private static String trim(double d) {
        if (Double.isNaN(d)) return "?";
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }
}
