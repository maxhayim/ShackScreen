package com.maxhayim.shackscreen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the contents of the dashboard cards. */
final class Panels {

    private final Context c;

    Panels(Context c) {
        this.c = c;
    }

    // --- Space weather: four big numbers and the HF bands as coloured pills ----------------

    View space(Feeds.Space s) {
        LinearLayout col = column();
        LinearLayout stats = row();
        stats.addView(stat("SFI", s.sfi, R.color.text), weight());
        stats.addView(stat("SSN", s.ssn, R.color.text), weight());
        stats.addView(stat("K", s.k, kColor(s.k)), weight());
        stats.addView(stat("A", s.a, R.color.text), weight());
        col.addView(stats);
        TextView xray = text("X-ray " + nz(s.xray), 12, R.color.dim, false);
        xray.setPadding(0, dp(4), 0, dp(10));
        col.addView(xray);

        LinearLayout head = row();
        head.addView(label(""), new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(label(c.getString(R.string.day)), weight());
        head.addView(label(c.getString(R.string.night)), weight());
        col.addView(head);
        for (Map.Entry<String, String[]> e : s.bands.entrySet()) {
            LinearLayout r = row();
            r.setPadding(0, dp(5), 0, 0);
            r.addView(text(e.getKey().replace("m-", "–"), 13, R.color.dim, false),
                    new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(condition(e.getValue()[0]), weight());
            r.addView(condition(e.getValue()[1]), weight());
            col.addView(r);
        }
        return col;
    }

    private View stat(String name, String value, int colorRes) {
        LinearLayout box = column();
        TextView v = text(nz(value), 28, colorRes, false);
        v.setIncludeFontPadding(false);
        box.addView(v);
        box.addView(label(name));
        return box;
    }

    /** K below 4 is quiet, 4 is unsettled, 5 and up is a storm. */
    private int kColor(String k) {
        double v;
        try {
            v = Double.parseDouble(k);
        } catch (RuntimeException e) {
            return R.color.text;
        }
        return v >= 5 ? R.color.poor : v >= 4 ? R.color.fair : R.color.good;
    }

    private View condition(String cond) {
        String s = cond == null || cond.isEmpty() ? "–" : cond;
        String low = s.toLowerCase(Locale.US);
        int res = low.startsWith("good") ? R.color.good : low.startsWith("fair") ? R.color.fair
                : low.startsWith("poor") ? R.color.poor : R.color.faint;
        TextView t = chip(s, res);
        LinearLayout wrap = row();
        wrap.addView(t);
        return wrap;
    }

    // --- POTA spots --------------------------------------------------------------------------

    View pota(List<Feeds.Spot> spots) {
        if (spots.isEmpty()) return message(c.getString(R.string.no_spots));
        LinearLayout col = column();
        for (Feeds.Spot s : spots) {
            LinearLayout r = row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(3), 0, dp(3));
            TextView call = text(s.call, 15, R.color.text, true);
            r.addView(call, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView freq = text(s.freq, 14, R.color.accent2, false);
            freq.setGravity(Gravity.END);
            r.addView(freq, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.setMarginStart(dp(10));
            r.addView(chip(s.mode, R.color.accent), chipLp);
            TextView ref = text(s.ref, 12, R.color.dim, false);
            ref.setGravity(Gravity.END);
            r.addView(ref, weight());
            col.addView(r);
        }
        return col;
    }

    // --- Mesh ----------------------------------------------------------------------------------

    View mesh(Feeds.Mesh m) {
        LinearLayout col = column();
        if (m.needsToken) {
            col.addView(text(c.getString(R.string.mesh_online), 28, R.color.good, false));
            TextView hint = text(c.getString(R.string.mesh_token), 13, R.color.dim, false);
            hint.setPadding(0, dp(6), 0, 0);
            col.addView(hint);
            return col;
        }
        LinearLayout big = row();
        big.setGravity(Gravity.BOTTOM);
        TextView n = text(String.valueOf(m.nodes), 40, R.color.text, false);
        n.setIncludeFontPadding(false);
        big.addView(n);
        TextView l = text(c.getString(R.string.nodes_today), 13, R.color.dim, false);
        l.setPadding(dp(8), 0, 0, dp(5));
        big.addView(l);
        col.addView(big);
        if (m.messages.isEmpty()) {
            TextView none = text(c.getString(R.string.no_messages), 13, R.color.dim, false);
            none.setPadding(0, dp(10), 0, 0);
            col.addView(none);
        }
        for (String[] msg : m.messages) {
            LinearLayout bubble = column();
            bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
            bubble.setBackground(round(R.color.icon_bg, 255, 12));
            bubble.addView(text(msg[0], 12, R.color.accent, true));
            TextView body = text(msg[1], 13, R.color.text, false);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            bubble.addView(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            col.addView(bubble, lp);
        }
        return col;
    }

    // --- Shared bits ---------------------------------------------------------------------------

    /** Centered dim text for loading, empty and error states. */
    View message(String s) {
        return message(s, 0);
    }

    /** The same, under a large faint icon when there's one to show. */
    View message(String s, int iconRes) {
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (iconRes != 0) {
            android.widget.ImageView icon = new android.widget.ImageView(c);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Icons.color(c, R.color.faint));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
            lp.bottomMargin = dp(14);
            col.addView(icon, lp);
        }
        TextView t = text(s, 14, R.color.dim, false);
        t.setSingleLine(false);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        return col;
    }

    private TextView chip(String s, int colorRes) {
        TextView t = text(s, 11, colorRes, true);
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        t.setBackground(round(colorRes, 0x2E, 999));
        return t;
    }

    private GradientDrawable round(int colorRes, int alpha, int radiusDp) {
        int col = Icons.color(c, colorRes);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(alpha * Color.alpha(col) / 255, Color.red(col), Color.green(col), Color.blue(col)));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.US), 10, R.color.faint, false);
        t.setLetterSpacing(0.15f);
        return t;
    }

    private TextView text(String s, int sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Icons.color(c, colorRes));
        t.setSingleLine(sp > 13);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private static String nz(String s) {
        return s == null || s.isEmpty() ? "–" : s;
    }

    private int dp(int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
