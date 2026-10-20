package com.maxhayim.shackscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The operator's ham call sign, GMRS call sign and CB handle, shown next to the clock, kept in
 * profiles. Every install comes with the Demo profile (made-up call signs, for showing ShackScreen
 * off and for screenshots); people add their own. Kept on the device only.
 */
final class CallSigns {

    /** Pref keys with their label; call signs are upper case, a CB handle is kept as typed. */
    static final String[][] FIELDS = {{"ham", "HAM"}, {"gmrs", "GMRS"}, {"cb", "CB"}};

    static final String DEMO = "demo";

    static final class Profile {
        String id, name;
        final String[] values = {"", "", ""}; // in FIELDS order

        boolean demo() {
            return DEMO.equals(id);
        }
    }

    private static final String PREFS = "callsigns";

    private CallSigns() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Profile demo() {
        Profile p = new Profile();
        p.id = DEMO;
        p.name = "Demo";
        p.values[0] = "N0CALL";
        p.values[1] = "WRXX000";
        p.values[2] = "Night Owl";
        return p;
    }

    /** Demo first, then the user's profiles in the order they were added. */
    static List<Profile> profiles(Context c) {
        migrate(c);
        List<Profile> out = new ArrayList<>();
        out.add(demo());
        try {
            JSONArray a = new JSONArray(prefs(c).getString("profiles", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.getString("id");
                p.name = o.optString("name");
                for (int f = 0; f < FIELDS.length; f++) p.values[f] = o.optString(FIELDS[f][0]);
                out.add(p);
            }
        } catch (Exception ignored) {
            // A broken list is treated as empty and replaced by the next save.
        }
        return out;
    }

    private static void save(Context c, List<Profile> list) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : list) {
                if (p.demo()) continue;
                JSONObject o = new JSONObject().put("id", p.id).put("name", p.name);
                for (int f = 0; f < FIELDS.length; f++) o.put(FIELDS[f][0], p.values[f]);
                a.put(o);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        prefs(c).edit().putString("profiles", a.toString()).apply();
    }

    /**
     * Before profiles, call signs were stored on their own. Real ones become "My profile" and stay in
     * use; none (or the demo's own) leaves the Demo profile in use.
     */
    private static void migrate(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains("profiles")) return;
        Profile old = new Profile();
        old.id = "p1";
        old.name = "My profile";
        boolean any = false, demo = true;
        Profile d = demo();
        for (int f = 0; f < FIELDS.length; f++) {
            old.values[f] = p.getString(FIELDS[f][0], "");
            any |= !old.values[f].isEmpty();
            demo &= old.values[f].equals(d.values[f]);
        }
        SharedPreferences.Editor ed = p.edit();
        for (String[] f : FIELDS) ed.remove(f[0]);
        ed.putString("profiles", "[]").putString("active", DEMO).apply();
        if (any && !demo) {
            List<Profile> list = new ArrayList<>();
            list.add(old);
            save(c, list);
            p.edit().putString("active", old.id).apply();
        }
    }

    static Profile active(Context c) {
        String id = activeId(c);
        for (Profile p : profiles(c)) if (p.id.equals(id)) return p;
        return demo();
    }

    private static String activeId(Context c) {
        migrate(c);
        return prefs(c).getString("active", DEMO);
    }

    static void use(Context c, Profile p) {
        prefs(c).edit().putString("active", p.id).apply();
    }

    static void delete(Context c, Profile p) {
        if (p.demo()) return;
        List<Profile> list = profiles(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(p.id)) list.remove(i);
        save(c, list);
        if (p.id.equals(activeId(c))) use(c, demo());
    }

    static String get(Context c, String key) {
        Profile p = active(c);
        for (int f = 0; f < FIELDS.length; f++) if (FIELDS[f][0].equals(key)) return p.values[f];
        return "";
    }

    static boolean any(Context c) {
        for (String[] f : FIELDS) if (!get(c, f[0]).isEmpty()) return true;
        return false;
    }

    /** Fills box with a row per call sign that is set, or an invitation to add them. */
    static void show(Context c, LinearLayout box) {
        box.removeAllViews();
        if (!any(c)) {
            box.addView(row(c, "", "+ " + c.getString(R.string.callsigns_add), false));
            return;
        }
        for (String[] f : FIELDS) {
            String v = get(c, f[0]);
            if (!v.isEmpty()) {
                // Rows sit close together so three of them stay within the clock's height.
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                // Pulled up under the row above, never the last row down, or "Night Owl" loses its g.
                if (box.getChildCount() > 0) lp.topMargin = -Math.round(3 * c.getResources().getDisplayMetrics().density);
                box.addView(row(c, f[1], v, true), lp);
            }
        }
    }

    /** Like the clock: the value large, its label small after it, as LOCAL and UTC are. */
    private static LinearLayout row(Context c, String label, String value, boolean big) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout r = new LinearLayout(c);
        r.setBaselineAligned(true);
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 29 : 17);
        v.setTextColor(Icons.color(c, big ? R.color.text : R.color.dim));
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        r.addView(v);
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setLetterSpacing(0.15f);
        l.setAllCaps(true);
        l.setTextColor(Icons.color(c, R.color.dim));
        l.setPadding(Math.round(8 * d), 0, 0, 0);
        r.addView(l);
        return r;
    }

    /** The dashboard's call signs, pressed: pick the profile in use, edit it, or add one. */
    static void choose(Activity a, Runnable done) {
        List<Profile> list = profiles(a);
        String[] names = new String[list.size()];
        int current = 0;
        String id = activeId(a);
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).name + "   ·   " + summary(list.get(i));
            if (list.get(i).id.equals(id)) current = i;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(R.string.profiles)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    d.dismiss();
                    use(a, list.get(which));
                    done.run();
                })
                .setNeutralButton(R.string.profile_add, (d, w) -> edit(a, null, done));
        Profile now = active(a);
        if (!now.demo()) b.setPositiveButton(R.string.profile_edit, (d, w) -> edit(a, now, done));
        b.show();
    }

    /** A profile's name and three fields; null adds a new one and puts it in use. done runs after a save. */
    static void edit(Activity a, Profile existing, Runnable done) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), 0);
        EditText name = new EditText(a);
        name.setHint(R.string.profile_name);
        name.setText(existing == null ? "" : existing.name);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);
        int[] hints = {R.string.callsign_ham, R.string.callsign_gmrs, R.string.cb_handle};
        EditText[] fields = new EditText[FIELDS.length];
        for (int i = 0; i < FIELDS.length; i++) {
            EditText e = new EditText(a);
            e.setHint(hints[i]);
            e.setText(existing == null ? "" : existing.values[i]);
            e.setSingleLine(true);
            e.setInputType(i < 2 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            fields[i] = e;
            form.addView(e);
        }
        Appearance.font(form);
        AlertDialog dialog = new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(existing == null ? R.string.profile_add : R.string.profile_edit)
                .setView(form)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    List<Profile> list = profiles(a);
                    Profile p = null;
                    if (existing != null) for (Profile q : list) if (q.id.equals(existing.id)) p = q;
                    if (p == null) {
                        p = new Profile();
                        p.id = "p" + System.currentTimeMillis();
                        list.add(p);
                    }
                    String n = name.getText().toString().trim();
                    p.name = n.isEmpty() ? a.getString(R.string.profile_default_name) : n;
                    for (int i = 0; i < FIELDS.length; i++) {
                        String v = fields[i].getText().toString().trim();
                        p.values[i] = i < 2 ? v.toUpperCase(Locale.US) : v;
                    }
                    save(a, list);
                    if (existing == null) use(a, p);
                    done.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        // On a TV the keyboard would cover the form; it opens when a field is selected.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    /** "K1ABC · WRXX123 · Night Owl", or "not set". */
    static String summary(Profile p) {
        StringBuilder sb = new StringBuilder();
        for (String v : p.values) {
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(v);
        }
        return sb.length() == 0 ? "–" : sb.toString();
    }
}
