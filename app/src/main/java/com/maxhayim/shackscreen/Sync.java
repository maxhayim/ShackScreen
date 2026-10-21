package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps several TVs in step through the backup service. Every TV that has the same service and
 * passphrase, with sync on, shares one encrypted file of items: profiles, saved logins, applications
 * (with their tokens), keyed maps and the location. Each TV sends what changed on it and takes what
 * changed elsewhere, item by item; when the same item changed on two TVs the later change wins, and a
 * deletion on one TV deletes it everywhere. The theme, icon style, widgets, launcher mode and the
 * profile in use stay each TV's own.
 */
final class Sync {

    static final String FILE_NAME = "shackscreen-sync.ssb";
    private static final String PREFS = "sync";
    private static final long MIN_GAP = 60 * 1000L;
    private static final Object LOCK = new Object();

    private Sync() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    /** Turning it off forgets what was last seen, so turning it on again starts like a new TV joining. */
    static void setEnabled(Context c, boolean on) {
        SharedPreferences.Editor ed = prefs(c).edit().putBoolean("on", on);
        if (!on) ed.remove("base").remove("pending").remove("last").remove("error");
        ed.apply();
    }

    static long last(Context c) {
        return prefs(c).getLong("last", 0);
    }

    static String lastError(Context c) {
        return prefs(c).getString("error", "");
    }

    // --- The items -----------------------------------------------------------------------------

    /** Everything that syncs, as key → JSON value, read from this TV's settings. */
    private static Map<String, JSONObject> snapshot(Context c) throws Exception {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (CallSigns.Profile p : CallSigns.profiles(c)) {
            if (!p.demo()) {
                JSONObject o = new JSONObject().put("name", p.name);
                for (int f = 0; f < CallSigns.FIELDS.length; f++) o.put(CallSigns.FIELDS[f][0], p.values[f]);
                out.put("p|" + p.id, o);
            }
            for (Object[] e : Logins.all(c, p.id)) {
                Logins.Login l = (Logins.Login) e[1];
                out.put("l|" + p.id + "|" + e[0] + "|" + l.host,
                        new JSONObject().put("user", l.user).put("pass", l.pass).put("auto", l.auto));
            }
        }
        // The order is one item of its own, so moving one app doesn't count as changing all the others.
        List<Store.Instance> apps = Store.load(c);
        JSONArray order = new JSONArray();
        for (Store.Instance in : apps) {
            out.put("a|" + in.id, in.toJson());
            order.put(in.id);
        }
        out.put("order", new JSONObject().put("ids", order));
        for (Maps.Style s : Maps.custom(c)) {
            out.put("m|" + s.url, new JSONObject().put("name", s.name).put("url", s.url).put("provider", s.provider).put("key", s.key));
        }
        String loc = Widgets.location(c);
        if (!loc.isEmpty()) out.put("loc", new JSONObject().put("v", loc));
        return out;
    }

    /** A fingerprint of a value, so what was last synced is remembered without keeping secrets in the clear. */
    static String hash(JSONObject v) {
        if (v == null) return "";
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(v.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format(java.util.Locale.US, "%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return v.toString();
        }
    }

    // --- Noting local changes ------------------------------------------------------------------

    /**
     * Notes the time of anything changed on this TV since the last sync, so a later change on
     * another TV can be told apart from an earlier one. Cheap: no network. Call after settings change.
     */
    static void touch(Context c) {
        if (!enabled(c)) return;
        synchronized (LOCK) {
            try {
                touchLocked(c, snapshot(c));
            } catch (Exception ignored) {
                // Picked up by the next sync.
            }
        }
    }

    private static JSONObject touchLocked(Context c, Map<String, JSONObject> local) throws Exception {
        JSONObject base = new JSONObject(prefs(c).getString("base", "{}"));
        JSONObject pending = new JSONObject(prefs(c).getString("pending", "{}"));
        // A TV joining for the first time counts its own settings as older than everyone else's.
        long now = base.length() == 0 && last(c) == 0 ? 1 : System.currentTimeMillis();
        Set<String> keys = new LinkedHashSet<>(local.keySet());
        for (Iterator<String> it = base.keys(); it.hasNext(); ) keys.add(it.next());
        for (String k : keys) {
            JSONObject b = base.optJSONObject(k);
            String was = b == null ? "" : b.optString("h");
            String is = hash(local.get(k));
            if (was.equals(is)) pending.remove(k);
            else if (!pending.has(k)) pending.put(k, now);
        }
        prefs(c).edit().putString("pending", pending.toString()).apply();
        return pending;
    }

    // --- Syncing -------------------------------------------------------------------------------

    /** Syncs if it's on, set up and not done in the last minute; true if anything arrived from another TV. */
    static boolean ifDue(Context c) {
        if (!enabled(c) || !Backup.ready(c) || System.currentTimeMillis() - last(c) < MIN_GAP) return false;
        try {
            return now(c) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Syncs now; returns how many items arrived from other TVs. Blocking. */
    static int now(Context c) throws Exception {
        synchronized (LOCK) {
            try {
                int n = run(c);
                prefs(c).edit().putLong("last", System.currentTimeMillis()).remove("error").apply();
                return n;
            } catch (Exception e) {
                prefs(c).edit().putString("error", e instanceof Backup.BadPassphrase ? "wrong passphrase" : String.valueOf(e.getMessage())).apply();
                throw e;
            }
        }
    }

    private static int run(Context c) throws Exception {
        String passphrase = Backup.passphrase(c);
        boolean first = prefs(c).getString("base", "{}").equals("{}") && last(c) == 0;
        byte[] file = Cloud.download(c, FILE_NAME);
        JSONObject remote = file == null ? new JSONObject() : Backup.open(file, passphrase).optJSONObject("items");
        if (remote == null) remote = new JSONObject();

        if (first) adoptSameApps(c, remote);
        Map<String, JSONObject> local = snapshot(c);
        JSONObject pending = touchLocked(c, local);
        JSONObject base = new JSONObject(prefs(c).getString("base", "{}"));

        long now = System.currentTimeMillis();
        Merge m = merge(local, base, pending, remote, file == null, now, Build.MODEL);
        Map<String, JSONObject> incoming = m.incoming;
        Set<String> deleted = m.deleted;
        boolean upload = m.upload;
        apply(c, incoming, deleted);
        // What arrived is remembered as this TV now stores it (an app's place in the list can come
        // out differently here), so it isn't mistaken for a change of ours and sent straight back.
        if (!incoming.isEmpty() || !deleted.isEmpty()) {
            Map<String, JSONObject> after = snapshot(c);
            Set<String> applied = new LinkedHashSet<>(incoming.keySet());
            applied.addAll(deleted);
            for (String k : applied) {
                JSONObject b = base.optJSONObject(k);
                if (b != null) b.put("h", hash(after.get(k)));
            }
        }
        if (upload) {
            JSONObject root = new JSONObject().put("items", remote).put("created", now).put("device", Build.MODEL);
            Cloud.upload(c, FILE_NAME, Backup.seal(root, passphrase));
        }
        prefs(c).edit().putString("base", base.toString()).putString("pending", "{}").apply();
        return incoming.size() + deleted.size();
    }

    /** What a merge decided: what to take from other TVs, and whether the shared file needs sending. */
    static final class Merge {
        final Map<String, JSONObject> incoming = new HashMap<>(); // key → value from another TV
        final Set<String> deleted = new LinkedHashSet<>();        // keys another TV deleted
        boolean upload;
    }

    /**
     * The merge, item by item. remote and base are updated in place. A change made only here is
     * sent; a change made only elsewhere is taken; when both changed, the later one wins; a
     * deletion is kept as a marker so it reaches every TV.
     */
    static Merge merge(Map<String, JSONObject> local, JSONObject base, JSONObject pending, JSONObject remote,
                       boolean noFile, long now, String device) throws Exception {
        Merge m = new Merge();
        m.upload = noFile;
        Set<String> keys = new LinkedHashSet<>(local.keySet());
        for (Iterator<String> it = remote.keys(); it.hasNext(); ) keys.add(it.next());
        for (Iterator<String> it = base.keys(); it.hasNext(); ) keys.add(it.next());
        for (String k : keys) {
            JSONObject r = remote.optJSONObject(k);
            JSONObject b = base.optJSONObject(k);
            long rt = r == null ? 0 : r.optLong("t");
            boolean localChange = pending.has(k);
            boolean remoteChange = r != null && (b == null || rt > b.optLong("t"));
            boolean takeLocal = localChange && (!remoteChange || pending.optLong(k) > rt);
            JSONObject finalValue;
            long t;
            if (takeLocal) {
                JSONObject v = local.get(k);
                t = Math.max(now, rt + 1);
                JSONObject item = new JSONObject().put("t", t).put("by", device);
                if (v == null) item.put("d", true);
                else item.put("v", v);
                remote.put(k, item);
                m.upload = true;
                finalValue = v;
            } else if (remoteChange) {
                t = rt;
                finalValue = r.optBoolean("d") ? null : r.optJSONObject("v");
                if (!hash(finalValue).equals(hash(local.get(k)))) {
                    if (finalValue == null) m.deleted.add(k);
                    else m.incoming.put(k, finalValue);
                }
            } else {
                t = b == null ? rt : b.optLong("t");
                finalValue = local.get(k);
            }
            if (finalValue == null && r == null) base.remove(k);
            else base.put(k, new JSONObject().put("h", hash(finalValue)).put("t", t));
        }
        return m;
    }

    /**
     * A TV joining for the first time already has the default apps, under ids of its own. Where
     * another TV has the same app (same kind and address), this TV takes that id, so it isn't doubled.
     */
    private static void adoptSameApps(Context c, JSONObject remote) {
        Map<String, String> byWhat = new HashMap<>();
        for (Iterator<String> it = remote.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject r = remote.optJSONObject(k);
            if (!k.startsWith("a|") || r == null || r.optBoolean("d")) continue;
            JSONObject v = r.optJSONObject("v");
            if (v != null) byWhat.put(v.optString("type") + " " + v.optString("url"), v.optString("id"));
        }
        List<Store.Instance> apps = Store.load(c);
        boolean changed = false;
        Set<String> taken = new LinkedHashSet<>();
        for (Store.Instance in : apps) taken.add(in.id);
        for (Store.Instance in : apps) {
            String other = byWhat.get(in.type + " " + in.url);
            if (other == null || other.equals(in.id) || taken.contains(other)) continue;
            Logins.renameApp(c, in.id, other);
            File dir = new File(c.getFilesDir(), "icons");
            File icon = new File(dir, in.id + "-custom.png");
            if (icon.exists()) //noinspection ResultOfMethodCallIgnored
                icon.renameTo(new File(dir, other + "-custom.png"));
            taken.remove(in.id);
            taken.add(other);
            in.id = other;
            changed = true;
        }
        if (changed) Store.save(c, apps);
    }

    /** Writes what came from other TVs into this TV's settings. */
    private static void apply(Context c, Map<String, JSONObject> in, Set<String> gone) throws Exception {
        if (in.isEmpty() && gone.isEmpty()) return;

        // Profiles.
        List<CallSigns.Profile> profiles = new ArrayList<>();
        for (CallSigns.Profile p : CallSigns.profiles(c)) if (!p.demo()) profiles.add(p);
        boolean profilesChanged = false;
        for (String k : gone) {
            if (!k.startsWith("p|")) continue;
            String id = k.substring(2);
            for (int i = profiles.size() - 1; i >= 0; i--) if (profiles.get(i).id.equals(id)) profiles.remove(i);
            Logins.deleteProfile(c, id);
            profilesChanged = true;
        }
        for (Map.Entry<String, JSONObject> e : in.entrySet()) {
            if (!e.getKey().startsWith("p|")) continue;
            String id = e.getKey().substring(2);
            CallSigns.Profile p = null;
            for (CallSigns.Profile q : profiles) if (q.id.equals(id)) p = q;
            if (p == null) {
                p = new CallSigns.Profile();
                p.id = id;
                profiles.add(p);
            }
            p.name = e.getValue().optString("name");
            for (int f = 0; f < CallSigns.FIELDS.length; f++) p.values[f] = e.getValue().optString(CallSigns.FIELDS[f][0]);
            profilesChanged = true;
        }
        if (profilesChanged) {
            CallSigns.save(c, profiles);
            boolean activeKept = false;
            String active = CallSigns.active(c).id;
            for (CallSigns.Profile p : CallSigns.profiles(c)) activeKept |= p.id.equals(active);
            if (!activeKept) CallSigns.use(c, CallSigns.profiles(c).get(0));
        }

        // Applications.
        List<Store.Instance> apps = Store.load(c);
        boolean appsChanged = false;
        for (String k : gone) {
            if (!k.startsWith("a|")) continue;
            String id = k.substring(2);
            for (int i = apps.size() - 1; i >= 0; i--) if (apps.get(i).id.equals(id)) apps.remove(i);
            appsChanged = true;
        }
        for (Map.Entry<String, JSONObject> e : in.entrySet()) {
            if (!e.getKey().startsWith("a|")) continue;
            Store.Instance got = Store.Instance.fromJson(e.getValue());
            boolean replaced = false;
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).id.equals(got.id)) {
                    apps.set(i, got);
                    replaced = true;
                }
            }
            if (!replaced) apps.add(got);
            appsChanged = true;
        }
        // In the other TV's order; apps it doesn't list keep their place after the listed ones.
        JSONObject order = in.get("order");
        if (order != null && order.optJSONArray("ids") != null) {
            Map<String, Integer> pos = new HashMap<>();
            JSONArray ids = order.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) pos.put(ids.getString(i), i);
            List<Store.Instance> before = new ArrayList<>(apps);
            java.util.Collections.sort(apps, (a, b) -> Integer.compare(
                    pos.containsKey(a.id) ? pos.get(a.id) : ids.length() + before.indexOf(a),
                    pos.containsKey(b.id) ? pos.get(b.id) : ids.length() + before.indexOf(b)));
            appsChanged = true;
        }
        if (appsChanged) Store.save(c, apps);

        // Saved logins (after the apps, whose deletion would otherwise prune them).
        for (String k : gone) {
            if (!k.startsWith("l|")) continue;
            String[] part = k.split("\\|", 4);
            if (part.length == 4) Logins.deleteFor(c, part[1], part[2], part[3]);
        }
        for (Map.Entry<String, JSONObject> e : in.entrySet()) {
            if (!e.getKey().startsWith("l|")) continue;
            String[] part = e.getKey().split("\\|", 4);
            if (part.length != 4) continue;
            Logins.Login l = new Logins.Login();
            l.host = part[3];
            l.user = e.getValue().optString("user");
            l.pass = e.getValue().optString("pass");
            l.auto = e.getValue().optBoolean("auto", true);
            Logins.saveFor(c, part[1], part[2], l);
        }

        // Keyed and custom maps.
        List<Maps.Style> maps = Maps.custom(c);
        boolean mapsChanged = false;
        for (String k : gone) {
            if (!k.startsWith("m|")) continue;
            for (int i = maps.size() - 1; i >= 0; i--) if (maps.get(i).url.equals(k.substring(2))) maps.remove(i);
            mapsChanged = true;
        }
        for (Map.Entry<String, JSONObject> e : in.entrySet()) {
            if (!e.getKey().startsWith("m|")) continue;
            JSONObject v = e.getValue();
            Maps.Style st = new Maps.Style("", v.optString("name"), v.optString("url"), v.optString("name"));
            st.provider = v.optString("provider");
            st.key = v.optString("key");
            boolean replaced = false;
            for (int i = 0; i < maps.size(); i++) {
                if (maps.get(i).url.equals(st.url)) {
                    maps.set(i, st);
                    replaced = true;
                }
            }
            if (!replaced) maps.add(st);
            mapsChanged = true;
        }
        if (mapsChanged) Maps.saveCustom(c, maps);

        // Location.
        if (in.containsKey("loc")) Widgets.setLocation(c, in.get("loc").optString("v"));
        else if (gone.contains("loc")) Widgets.setLocation(c, "");
    }
}
