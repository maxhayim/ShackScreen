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
        stats.addView(stat("SFI", s.sfi, R.color.text, 28), weight());
        stats.addView(stat("SSN", s.ssn, R.color.text, 28), weight());
        stats.addView(stat("K", s.k, kColor(s.k), 28), weight());
        stats.addView(stat("A", s.a, R.color.text, 28), weight());
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

    private View stat(String name, String value, int colorRes, int sp) {
        LinearLayout box = column();
        TextView v = text(nz(value), sp, colorRes, false);
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

    // --- Lists: POTA, DX, contests, DXpeditions, aircraft, ships, mesh nodes and sources -----

    View rows(Feeds.Rows r) {
        LinearLayout col = column();
        if (r.header != null) {
            TextView h = text(r.header, 12, R.color.dim, false);
            h.setPadding(0, 0, 0, dp(6));
            col.addView(h);
        }
        if (r.rows.isEmpty()) {
            if (r.header == null) return message(r.empty);
            TextView e = text(r.empty, 13, R.color.dim, false);
            e.setSingleLine(false);
            col.addView(e);
            return col;
        }
        for (Feeds.Row row : r.rows) {
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(3), 0, dp(3));
            TextView main = text(row.main, 15, R.color.text, true);
            line.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
            if (row.num != null) {
                TextView num = text(row.num, 14, R.color.accent2, false);
                num.setGravity(Gravity.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(dp(6));
                line.addView(num, lp);
            }
            if (row.chip != null && !row.chip.isEmpty()) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(dp(8));
                // Long mode names ("BPSK PMT-A3") are shortened to their first word.
                String chip = row.chip.length() > 8 && row.chip.contains(" ") ? row.chip.substring(0, row.chip.indexOf(' ')) : row.chip;
                line.addView(chip(chip, tone(row.tone)), lp);
            }
            if (row.trail != null && !row.trail.isEmpty()) {
                TextView trail = text(row.trail, 12, R.color.dim, false);
                trail.setSingleLine(true); // a list row is one line, however long the text
                trail.setGravity(Gravity.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart(dp(8));
                line.addView(trail, lp);
            }
            col.addView(line);
        }
        return col;
    }

    private static int tone(int t) {
        switch (t) {
            case Feeds.GOOD: return R.color.good;
            case Feeds.FAIR: return R.color.fair;
            case Feeds.POOR: return R.color.poor;
            case Feeds.DIM: return R.color.dim;
            default: return R.color.accent;
        }
    }

    // --- Weather -------------------------------------------------------------------------------

    View weather(Feeds.Weather w) {
        LinearLayout col = column();
        LinearLayout big = row();
        big.setGravity(Gravity.CENTER_VERTICAL);
        big.addView(icon(skyIcon(w.code, w.day), 46, R.color.text), new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView t = text(w.temp, 44, R.color.text, false);
        t.setIncludeFontPadding(false);
        t.setPadding(dp(12), 0, 0, 0);
        big.addView(t);
        LinearLayout side = column();
        side.setPadding(dp(14), 0, 0, 0);
        side.addView(text(w.sky, 16, R.color.text, true));
        if (w.high != null) side.addView(text("H " + w.high + "   L " + w.low, 12, R.color.dim, false));
        side.addView(text("Feels like " + w.feels, 12, R.color.dim, false));
        big.addView(side);
        col.addView(big);
        // The next hours, like a phone's weather app.
        if (!w.hours.isEmpty()) {
            LinearLayout hours = row();
            hours.setPadding(0, dp(12), 0, 0);
            boolean rain = false;
            for (Feeds.Hour h : w.hours) rain |= h.rain >= 20;
            for (Feeds.Hour h : w.hours) {
                LinearLayout cell = column();
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.addView(text(hourLabel(h.time), 12, R.color.dim, false));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
                ip.topMargin = dp(4);
                ip.bottomMargin = dp(4);
                cell.addView(icon(skyIcon(h.code, h.day), 22, R.color.text), ip);
                cell.addView(text(h.temp, 15, R.color.text, false));
                // Chance of rain, only when it's worth knowing.
                if (rain) cell.addView(text(h.rain >= 20 ? h.rain + "%" : " ", 11, R.color.accent, false));
                hours.addView(cell, weight());
            }
            col.addView(hours);
        }
        // Two rows of two, so nothing is cut short.
        String sun = w.day ? w.sunset : w.sunrise;
        LinearLayout stats = row();
        stats.setPadding(0, dp(10), 0, 0);
        stats.addView(iconStat(R.drawable.ic_w_wind, w.wind), weight());
        stats.addView(iconStat(R.drawable.ic_w_drop, w.humidity + " humidity"), weight());
        col.addView(stats);
        stats = row();
        stats.setPadding(0, dp(6), 0, 0);
        if (sun != null) stats.addView(iconStat(w.day ? R.drawable.ic_w_sunset : R.drawable.ic_w_sunrise, hourLabel(sun, true)), weight());
        if (w.uv != null && w.day) stats.addView(iconStat(R.drawable.ic_sun, "UV " + w.uv), weight());
        else stats.addView(iconStat(R.drawable.ic_w_gauge, w.pressure), weight());
        col.addView(stats);
        return col;
    }

    /** Open-Meteo's WMO weather code as a line icon, with a moon at night. */
    private static int skyIcon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.ic_sun : R.drawable.ic_w_moon;
        if (code <= 2) return day ? R.drawable.ic_w_partly : R.drawable.ic_w_partly_night;
        if (code == 3) return R.drawable.ic_cloud;
        if (code <= 48) return R.drawable.ic_w_fog;
        if (code <= 57) return R.drawable.ic_w_drizzle;
        if (code <= 67 || (code >= 80 && code <= 82)) return R.drawable.ic_w_rain;
        if (code <= 77 || code == 85 || code == 86) return R.drawable.ic_w_snow;
        return R.drawable.ic_w_thunder;
    }

    private String hourLabel(String hhmm) {
        return hourLabel(hhmm, false);
    }

    /** "18:00" as the TV tells time: "18:00", or "6 PM" ("6:01 PM" with minutes). */
    private String hourLabel(String hhmm, boolean minutes) {
        if (hhmm == null || hhmm.length() < 5) return nz(hhmm);
        if (android.text.format.DateFormat.is24HourFormat(c)) return hhmm;
        int h = Integer.parseInt(hhmm.substring(0, 2));
        String m = hhmm.substring(3, 5);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (minutes ? ":" + m : "") + (h < 12 ? " AM" : " PM");
    }

    private android.widget.ImageView icon(int res, int sizeDp, int colorRes) {
        android.widget.ImageView v = new android.widget.ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(Icons.color(c, colorRes));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** A small dim icon with a value beside it. */
    private View iconStat(int res, String value) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(icon(res, 16, R.color.dim), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView v = text(nz(value), 14, R.color.text, false);
        v.setPadding(dp(6), 0, 0, 0);
        r.addView(v);
        return r;
    }

    // --- Mesh messages ---------------------------------------------------------------------------

    View mesh(Feeds.Mesh m) {
        LinearLayout col = column();
        if (m.needsToken) {
            col.addView(text(c.getString(R.string.mesh_online), 28, R.color.good, false));
            TextView hint = text(c.getString(R.string.mesh_token), 13, R.color.dim, false);
            hint.setSingleLine(false);
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
            LinearLayout head = row();
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(text(msg[0], 12, R.color.accent, true));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginStart(dp(8));
            head.addView(chip(msg[2], R.color.dim), clp);
            bubble.addView(head);
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
        t.setText(Emoji.forWidgets(c, s));
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
