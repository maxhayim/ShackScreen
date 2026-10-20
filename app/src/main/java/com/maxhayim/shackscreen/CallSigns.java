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

import java.util.Locale;

/**
 * The operator's ham call sign, GMRS call sign and CB handle, shown next to the clock. Kept on
 * the device only.
 */
final class CallSigns {

    /** Pref keys with their label; call signs are upper case, a CB handle is kept as typed. */
    static final String[][] FIELDS = {{"ham", "HAM"}, {"gmrs", "GMRS"}, {"cb", "CB"}};

    private static final String PREFS = "callsigns";

    private CallSigns() {}

    static String get(Context c, String key) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
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

    /** The three fields; done runs after a save. */
    static void edit(Activity a, Runnable done) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), 0);
        int[] hints = {R.string.callsign_ham, R.string.callsign_gmrs, R.string.cb_handle};
        EditText[] fields = new EditText[FIELDS.length];
        for (int i = 0; i < FIELDS.length; i++) {
            EditText e = new EditText(a);
            e.setHint(hints[i]);
            e.setText(get(a, FIELDS[i][0]));
            e.setSingleLine(true);
            e.setInputType(i < 2 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            fields[i] = e;
            form.addView(e);
        }
        Appearance.font(form);
        AlertDialog dialog = new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(R.string.callsigns)
                .setView(form)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    SharedPreferences.Editor ed = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                    for (int i = 0; i < FIELDS.length; i++) {
                        String v = fields[i].getText().toString().trim();
                        if (i < 2) v = v.toUpperCase(Locale.US);
                        ed.putString(FIELDS[i][0], v);
                    }
                    ed.apply();
                    done.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        // On a TV the keyboard would cover the form; it opens when a field is selected.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    /** One line for Settings: "Call signs: K1ABC · WRXX123 · Night Owl", or "not set". */
    static String summary(Context c) {
        StringBuilder sb = new StringBuilder();
        for (String[] f : FIELDS) {
            String v = get(c, f[0]);
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(v);
        }
        return c.getString(R.string.callsigns_value, sb.length() == 0 ? c.getString(R.string.not_set) : sb.toString());
    }
}
