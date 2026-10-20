package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** The theme (a background colour, or light, or follow the TV) and the app's font. */
final class Appearance {

    static final String AUTO = "auto", BLACK = "black", LIGHT = "light";

    /** A background: two gradient stops and two soft glows (alpha 0 for none). */
    static final class Theme {
        final String id, name;
        final boolean light;
        final int top, bottom, glow1, glow2;

        Theme(String id, String name, boolean light, int top, int bottom, int glow1, int glow2) {
            this.id = id;
            this.name = name;
            this.light = light;
            this.top = top;
            this.bottom = bottom;
            this.glow1 = glow1;
            this.glow2 = glow2;
        }
    }

    static final Theme[] THEMES = {
            new Theme(BLACK, "Black", false, 0xFF000000, 0xFF000000, 0, 0),
            new Theme("midnight", "Midnight", false, 0xFF101830, 0xFF04060B, 0x2EF5A524, 0x2238BDF8),
            new Theme("ocean", "Ocean", false, 0xFF0B2A4A, 0xFF020812, 0x2638BDF8, 0x1A22D3EE),
            new Theme("forest", "Forest", false, 0xFF0F2C1F, 0xFF020A06, 0x2634D399, 0x1AF5A524),
            new Theme("plum", "Plum", false, 0xFF2A1238, 0xFF08030C, 0x33C084FC, 0x1A38BDF8),
            new Theme("ember", "Ember", false, 0xFF3A150C, 0xFF0A0302, 0x33F97316, 0x1AF5A524),
            new Theme("slate", "Slate", false, 0xFF2B313B, 0xFF0D0F13, 0x1AFFFFFF, 0x1A38BDF8),
            new Theme(LIGHT, "Light", true, 0xFFF7F9FC, 0xFFE4E9F2, 0x33F59E0B, 0x1F0EA5E9),
    };

    static String mode(Context c) {
        String m = c.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme", BLACK);
        return "dark".equals(m) ? BLACK : m; // 0.3's "dark" is now Black
    }

    static void setMode(Context c, String mode) {
        c.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", mode).apply();
    }

    /** Application icon styles; "auto" picks dark or light to match the theme. */
    static final String ICONS_COLOR = "color", ICONS_DARK = "dark", ICONS_LIGHT = "light", ICONS_AUTO = "auto";
    static final String[] ICON_STYLES = {ICONS_COLOR, ICONS_DARK, ICONS_LIGHT, ICONS_AUTO};

    /** The chosen setting, as stored. 0.9's black and white became Dark. */
    static String iconSetting(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        return p.getString("icons", p.getBoolean("mono_icons", false) ? ICONS_DARK : ICONS_COLOR);
    }

    static void setIconSetting(Context c, String style) {
        c.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("icons", style).remove("mono_icons").apply();
    }

    /** The icon style to show now: color, dark or light. */
    static String iconStyle(Context c) {
        String s = iconSetting(c);
        if (!ICONS_AUTO.equals(s)) return s;
        return theme(c).light ? ICONS_LIGHT : ICONS_DARK;
    }

    static String label(Context c, String mode) {
        if (AUTO.equals(mode)) return "Follow the TV (Black or Light)";
        for (Theme t : THEMES) if (t.id.equals(mode)) return t.name;
        return THEMES[0].name;
    }

    /** The theme to draw: the chosen one, or Black or Light by the TV's own night mode. */
    static Theme theme(Context c) {
        String m = mode(c);
        if (AUTO.equals(m)) {
            boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    != Configuration.UI_MODE_NIGHT_NO;
            return night ? THEMES[0] : THEMES[THEMES.length - 1];
        }
        for (Theme t : THEMES) if (t.id.equals(m)) return t;
        return THEMES[0];
    }

    /** The uiMode night bits to force, or 0 to follow the TV. */
    static int night(Context c) {
        if (AUTO.equals(mode(c))) return 0;
        return theme(c).light ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
    }

    /** The window background for the theme: the gradient with its glows on top. */
    static Drawable background(Context c) {
        Theme t = theme(c);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.top, t.bottom});
        if (t.glow1 == 0 && t.glow2 == 0) return base;
        float r = Math.max(c.getResources().getDisplayMetrics().widthPixels, c.getResources().getDisplayMetrics().heightPixels);
        GradientDrawable g1 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.glow1, 0});
        g1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g1.setGradientRadius(r * 0.7f);
        g1.setGradientCenter(0.1f, 0.05f);
        GradientDrawable g2 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.glow2, 0});
        g2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g2.setGradientRadius(r * 0.6f);
        g2.setGradientCenter(0.95f, 1f);
        return new LayerDrawable(new Drawable[]{base, g1, g2});
    }

    // --- Font: Outfit, a variable font, at two weights -------------------------------------

    private static Typeface regular, bold;
    private static boolean loaded;

    /** Gives every TextView under root the Outfit font, bold where the layout asked for bold. */
    static void font(View root) {
        load(root.getContext());
        if (regular == null) return;
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            boolean isBold = t.getTypeface() != null && t.getTypeface().isBold();
            t.setTypeface(isBold ? bold : regular);
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) font(g.getChildAt(i));
        }
    }

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        // Weights of a variable font need API 26; older TVs keep the system font.
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            regular = new Typeface.Builder(c.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' 400").build();
            bold = new Typeface.Builder(c.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' 600").build();
        } catch (RuntimeException e) {
            regular = bold = null;
        }
    }
}
