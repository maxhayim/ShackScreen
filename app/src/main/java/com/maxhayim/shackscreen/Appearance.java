package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** The theme setting (dark, light, or follow the TV) and the app's font. */
final class Appearance {

    static final String DARK = "dark", LIGHT = "light", AUTO = "auto";

    static String mode(Context c) {
        return c.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme", DARK);
    }

    static void setMode(Context c, String mode) {
        c.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", mode).apply();
    }

    static String next(String mode) {
        return DARK.equals(mode) ? LIGHT : LIGHT.equals(mode) ? AUTO : DARK;
    }

    /** The uiMode night bits to force, or 0 to follow the TV. */
    static int night(Context c) {
        String m = mode(c);
        return DARK.equals(m) ? Configuration.UI_MODE_NIGHT_YES
                : LIGHT.equals(m) ? Configuration.UI_MODE_NIGHT_NO : 0;
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
