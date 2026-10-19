package com.maxhayim.shackscreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns each app's API into the text of a dashboard panel. Every method blocks and throws
 * IOException when the server can't be read, so the caller can try the next app.
 */
final class Feeds {

    private static final String[] BANDS = {"80m-40m", "30m-20m", "17m-15m", "12m-10m"};
    private static final int MAX_SPOTS = 6, MAX_MESSAGES = 3;

    static String space(String type, Store.Instance in) throws IOException {
        String sfi, ssn, k, a, xray;
        Map<String, String[]> bands = new HashMap<>(); // band -> {day, night}
        if (type.equals("openhamclock")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/n0nbh"), null));
            JSONObject s = o.optJSONObject("solarData");
            if (s == null) throw new IOException("no solarData");
            sfi = s.optString("solarFlux"); ssn = s.optString("sunspots");
            k = s.optString("kIndex"); a = s.optString("aIndex"); xray = s.optString("xray");
            JSONArray bc = o.optJSONArray("bandConditions");
            for (int i = 0; bc != null && i < bc.length(); i++) {
                JSONObject b = bc.optJSONObject(i);
                if (b == null) continue;
                // get/put rather than computeIfAbsent, which needs API 24.
                String[] row = bands.get(b.optString("name"));
                if (row == null) bands.put(b.optString("name"), row = new String[2]);
                row["night".equals(b.optString("time")) ? 1 : 0] = b.optString("condition");
            }
        } else if (type.equals("shackboard")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/space-weather"), null));
            sfi = o.optString("sfi"); ssn = o.optString("sunspots");
            k = trim(o.optDouble("kIndex")); a = o.optString("aIndex"); xray = o.optString("xrayFlux");
            JSONObject hf = o.optJSONObject("hfConditions");
            for (String band : BANDS) {
                JSONObject b = hf == null ? null : hf.optJSONObject(band);
                if (b != null) bands.put(band, new String[]{b.optString("day"), b.optString("night")});
            }
        } else {
            throw new IOException(type + " has no space weather");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "SFI %-5s SSN %s%n", sfi, ssn));
        sb.append(String.format(Locale.US, "K %-3s A %-3s X-ray %s%n%n", k, a, xray));
        sb.append(String.format(Locale.US, "%-9s%-6s%s%n", "Band", "Day", "Night"));
        for (String band : BANDS) {
            String[] row = bands.get(band);
            if (row == null) continue;
            sb.append(String.format(Locale.US, "%-9s%-6s%s%n", band.replace("m-", "-"),
                    nz(row[0]), nz(row[1])));
        }
        return sb.toString().trim();
    }

    static String pota(String type, Store.Instance in) throws IOException {
        JSONArray spots;
        if (type.equals("openhamclock")) {
            spots = arr(Net.json(Store.join(in.url, "/api/pota/spots"), null));
        } else if (type.equals("shackboard")) {
            JSONObject o = obj(Net.json(Store.join(in.url, "/api/spots?limit=100"), null));
            spots = o.optJSONArray("spots");
            if (spots == null) throw new IOException("no spots");
        } else {
            throw new IOException(type + " has no POTA spots");
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < spots.length() && shown < MAX_SPOTS; i++) {
            JSONObject s = spots.optJSONObject(i);
            if (s == null) continue;
            // ShackBoard mixes in DX spots, which it simulates when its cluster feed is down; keep POTA only.
            if (s.has("source") && type.equals("shackboard") && !"POTA".equals(s.optString("source"))) continue;
            double khz = s.optDouble("frequency", Double.NaN);
            String freq = Double.isNaN(khz) ? s.optString("frequency") : String.format(Locale.US, "%.3f", khz / 1000);
            sb.append(String.format(Locale.US, "%-8s %7s %-4s %s%n",
                    s.optString("activator"), freq, s.optString("mode"), s.optString("reference")));
            shown++;
        }
        return shown == 0 ? "No spots right now" : sb.toString().trim();
    }

    static String mesh(Store.Instance in) throws IOException {
        if (in.token == null || in.token.isEmpty()) {
            if (!Net.up(Store.join(in.url, "/api/health"))) throw new IOException("MeshMonitor is offline");
            return "Online\n\nAdd an API token in ⚙\nto see nodes and messages";
        }
        String base = Store.join(in.url, "/api/v1/sources/default");
        JSONObject nodes = obj(Net.json(base + "/nodes?active=true&sinceDays=1", in.token));
        JSONArray list = nodes.optJSONArray("data");
        Map<String, String> names = new HashMap<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject n = list.optJSONObject(i);
            if (n != null) names.put(n.optString("nodeId"), n.optString("shortName", n.optString("nodeId")));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(nodes.optInt("count", names.size())).append(" nodes heard today\n\n");
        JSONObject msgs = obj(Net.json(base + "/messages?limit=" + MAX_MESSAGES, in.token));
        JSONArray m = msgs.optJSONArray("data");
        if (m == null || m.length() == 0) sb.append("No messages");
        for (int i = 0; m != null && i < m.length(); i++) {
            JSONObject msg = m.optJSONObject(i);
            if (msg == null) continue;
            String from = msg.optString("fromNodeId");
            String name = names.containsKey(from) ? names.get(from) : from;
            sb.append(name).append(": ").append(msg.optString("text")).append('\n');
        }
        return sb.toString().trim();
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

    private static String nz(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }
}
