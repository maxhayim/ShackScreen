package com.maxhayim.shackscreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads each app's API into plain data for the dashboard panels. Every method blocks and throws
 * IOException when the server can't be read, so the caller can try the next app.
 */
final class Feeds {

    static final String[] BANDS = {"80m-40m", "30m-20m", "17m-15m", "12m-10m"};
    private static final int MAX_SPOTS = 6, MAX_MESSAGES = 3;

    static final class Space {
        String sfi, ssn, k, a, xray;
        /** band -> {day, night}, in BANDS order */
        final Map<String, String[]> bands = new LinkedHashMap<>();
    }

    static final class Spot {
        String call, freq, mode, ref;
    }

    static final class Mesh {
        boolean needsToken;
        int nodes;
        final List<String[]> messages = new ArrayList<>(); // {from, text}
    }

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

    static List<Spot> pota(String type, Store.Instance in) throws IOException {
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
        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < raw.length() && spots.size() < MAX_SPOTS; i++) {
            JSONObject o = raw.optJSONObject(i);
            if (o == null) continue;
            // ShackBoard mixes in DX spots, which it simulates when its cluster feed is down; keep POTA only.
            if (type.equals("shackboard") && !"POTA".equals(o.optString("source"))) continue;
            Spot s = new Spot();
            double khz = o.optDouble("frequency", Double.NaN);
            s.freq = Double.isNaN(khz) ? o.optString("frequency") : String.format(Locale.US, "%.3f", khz / 1000);
            s.call = o.optString("activator");
            s.mode = o.optString("mode");
            s.ref = o.optString("reference");
            spots.add(s);
        }
        return spots;
    }

    static Mesh mesh(Store.Instance in) throws IOException {
        Mesh m = new Mesh();
        if (in.token == null || in.token.isEmpty()) {
            if (!Net.up(Store.join(in.url, "/api/health"))) throw new IOException("MeshMonitor is offline");
            m.needsToken = true;
            return m;
        }
        String base = Store.join(in.url, "/api/v1/sources/default");
        JSONObject nodes = obj(Net.json(base + "/nodes?active=true&sinceDays=1", in.token));
        JSONArray list = nodes.optJSONArray("data");
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject n = list.optJSONObject(i);
            if (n != null) names.put(n.optString("nodeId"), n.optString("shortName", n.optString("nodeId")));
        }
        m.nodes = nodes.optInt("count", names.size());
        JSONObject msgs = obj(Net.json(base + "/messages?limit=" + MAX_MESSAGES, in.token));
        JSONArray arr = msgs.optJSONArray("data");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject msg = arr.optJSONObject(i);
            if (msg == null) continue;
            String from = msg.optString("fromNodeId");
            m.messages.add(new String[]{names.containsKey(from) ? names.get(from) : from, msg.optString("text")});
        }
        return m;
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
