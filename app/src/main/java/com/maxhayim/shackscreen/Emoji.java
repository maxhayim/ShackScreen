package com.maxhayim.shackscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/**
 * Emoji in widget text (a mesh user's name, a message) drawn in greys when the icons are Dark or
 * Light, so they sit with the black and white icons instead of standing out in colour.
 */
final class Emoji {

    private Emoji() {}

    static CharSequence forWidgets(Context c, String s) {
        if (s == null || Appearance.ICONS_COLOR.equals(Appearance.iconStyle(c))) return s;
        return mono(s);
    }

    /** The text with each emoji (and its modifiers and joined parts) drawn without colour. */
    static CharSequence mono(String s) {
        SpannableString out = null;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!isEmoji(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int start = i;
            i += Character.charCount(cp);
            // Keep variation selectors, skin tones, keycaps, tags and joined emoji with it.
            while (i < s.length()) {
                int next = s.codePointAt(i);
                if (next == 0x200D && i + 1 < s.length()) {
                    i += 1;
                    int joined = s.codePointAt(i);
                    i += Character.charCount(joined);
                } else if (next == 0xFE0F || next == 0x20E3 || (next >= 0x1F3FB && next <= 0x1F3FF)
                        || (next >= 0xE0020 && next <= 0xE007F) || (next >= 0x1F1E6 && next <= 0x1F1FF)) {
                    i += Character.charCount(next);
                } else {
                    break;
                }
            }
            if (out == null) out = new SpannableString(s);
            out.setSpan(new Grey(), start, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out == null ? s : out;
    }

    private static boolean isEmoji(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x2300 && cp <= 0x23FF) || (cp >= 0x2B00 && cp <= 0x2BFF);
    }

    private static final class Grey extends ReplacementSpan {
        private static final ColorMatrixColorFilter FILTER;

        static {
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(0);
            FILTER = new ColorMatrixColorFilter(m);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) paint.getFontMetricsInt(fm);
            return Math.round(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Paint p = new Paint(paint);
            p.setColorFilter(FILTER);
            canvas.drawText(text, start, end, x, y, p);
        }
    }
}
