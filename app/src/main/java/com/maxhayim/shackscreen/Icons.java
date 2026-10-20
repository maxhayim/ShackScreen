package com.maxhayim.shackscreen;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App icons that look like app icons. The sharpest icon a site offers (web app manifest, touch
 * icon, favicon; for public sites also Google's favicon service) is fetched from the server the
 * app points at, so a self-hosted one shows its own, and made square: a solid icon fills the
 * tile, a transparent logo sits on a plain tile, and a site with only a tiny favicon, or none,
 * gets a coloured tile with a symbol for its category. The user can pick a symbol instead, or
 * their own image.
 */
final class Icons {

    private static final ExecutorService bg = Executors.newFixedThreadPool(2);
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HTML = 256 * 1024, MAX_ICON = 2 * 1024 * 1024;
    /** Output size, and the smallest source worth showing rather than a category tile. */
    private static final int SIZE = 192, MIN_SOURCE = 40, FULL_BLEED = 96;
    /** Bumped when the way icons are drawn changes, so cached ones are redrawn. */
    private static final int STYLE = 3;

    /** Tile colours (top-left, bottom-right) and symbol for each category. */
    private static int[] look(String category) {
        switch (category) {
            case "Ham radio": return new int[]{0xFFF5A524, 0xFFC2410C, R.drawable.ic_antenna};
            case "GMRS": return new int[]{0xFFFB923C, 0xFFB91C1C, R.drawable.ic_radio};
            case "Satellites": return new int[]{0xFF8B5CF6, 0xFF4C1D95, R.drawable.ic_satellite};
            case "Mesh": return new int[]{0xFF34D399, 0xFF047857, R.drawable.ic_mesh};
            case "SDR & scanners": return new int[]{0xFF2DD4BF, 0xFF0F766E, R.drawable.ic_waves};
            case "Aviation": return new int[]{0xFF60A5FA, 0xFF1D4ED8, R.drawable.ic_plane};
            case "Marine": return new int[]{0xFF38BDF8, 0xFF0C4A6E, R.drawable.ic_ship};
            case "Weather": return new int[]{0xFF22D3EE, 0xFF0E7490, R.drawable.ic_cloud};
            default: return new int[]{0xFF94A3B8, 0xFF334155, R.drawable.ic_globe};
        }
    }

    /** The symbol for a category, for the All apps sidebar. */
    static int categorySymbol(String category) {
        return look(category)[2];
    }

    /** The symbols a user can choose for an app, by the name stored in Store.Instance.icon. */
    static final String[] SYMBOL_NAMES = {"antenna", "radio", "waves", "satellite", "mesh", "plane", "ship",
            "cloud", "sun", "globe", "grid", "park", "trophy", "chat"};
    static final int[] SYMBOLS = {R.drawable.ic_antenna, R.drawable.ic_radio, R.drawable.ic_waves, R.drawable.ic_satellite,
            R.drawable.ic_mesh, R.drawable.ic_plane, R.drawable.ic_ship, R.drawable.ic_cloud, R.drawable.ic_sun,
            R.drawable.ic_globe, R.drawable.ic_grid, R.drawable.ic_park, R.drawable.ic_trophy, R.drawable.ic_chat};

    /** The tile for a symbol in an app's category colours. */
    static Bitmap symbolFor(Context c, Store.Instance in, int symbolRes) {
        int[] look = look(Catalog.get(c, in.type).category);
        return symbolTile(c, symbolRes, look[0], look[1]);
    }

    // --- Tiles -------------------------------------------------------------------------------

    /** An app's icon with its name underneath, for the home row and All apps. */
    static View tile(Activity a, ViewGroup parent, Store.Instance in) {
        View t = frame(a, parent, in.name);
        t.setTag(in.id);
        show(a, in, t.findViewById(R.id.icon));
        return t;
    }

    /** A button in the same style (All apps, Add app, Request an app), with its own colours. */
    static View button(Activity a, ViewGroup parent, String label, int symbolRes, int top, int bottom) {
        View t = frame(a, parent, label);
        ((ImageView) t.findViewById(R.id.icon)).setImageBitmap(symbolTile(a, symbolRes, top, bottom));
        return t;
    }

    private static View frame(Activity a, ViewGroup parent, String label) {
        View t = LayoutInflater.from(a).inflate(R.layout.item_app, parent, false);
        ((TextView) t.findViewById(R.id.name)).setText(label);
        ((View) t.findViewById(R.id.icon).getParent()).setClipToOutline(true); // rounded corners
        t.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(140));
        Appearance.font(t);
        return t;
    }

    static void show(Context c, Store.Instance in, ImageView view) {
        Catalog.Type t = Catalog.get(c, in.type);
        view.setTag(in.id);
        int[] look = look(t.category);
        int symbol = t.id.equals("custom") ? R.drawable.ic_globe : look[2];
        if (in.icon.startsWith("symbol:")) {
            for (int i = 0; i < SYMBOL_NAMES.length; i++) {
                if (in.icon.equals("symbol:" + SYMBOL_NAMES[i])) symbol = SYMBOLS[i];
            }
        }
        view.setImageBitmap(symbolTile(c, symbol, look[0], look[1]));
        if (in.icon.equals("file")) {
            Bitmap b = BitmapFactory.decodeFile(customFile(c, in).getPath());
            if (b != null) view.setImageBitmap(b);
            return;
        }
        if (!in.hasUrl() || !in.icon.isEmpty()) return;
        File cached = file(c, in);
        if (cached.exists()) {
            Bitmap b = BitmapFactory.decodeFile(cached.getPath());
            if (b != null) {
                view.setImageBitmap(b);
                return;
            }
        }
        // A site with no usable icon is remembered for a day rather than asked on every visit.
        File miss = new File(cached.getPath() + ".none");
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < 24 * 60 * 60 * 1000L) return;
        Context app = c.getApplicationContext();
        bg.execute(() -> {
            Bitmap src = fetch(t, in);
            Bitmap b = src == null ? null : compose(app, src, look);
            try {
                if (b == null) {
                    new FileOutputStream(miss).close();
                    return;
                }
                try (OutputStream out = new FileOutputStream(cached)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } catch (IOException ignored) {
                // Not cached this time; it is fetched again on the next visit.
            }
            ui.post(() -> {
                if (in.id.equals(view.getTag())) view.setImageBitmap(b);
            });
        });
    }

    // --- Drawing -----------------------------------------------------------------------------

    /** A diagonal gradient with a white symbol in the middle, like a built-in phone app. */
    static Bitmap symbolTile(Context c, int symbolRes, int top, int bottom) {
        int w = SIZE, h = SIZE;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, top, bottom, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        Drawable d = c.getResources().getDrawable(symbolRes, null).mutate();
        d.setTint(Color.WHITE);
        int s = Math.min(w, h) * 46 / 100;
        d.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        d.draw(cv);
        return b;
    }

    /**
     * A site's icon made square. Large and solid to the edges: it fills the tile. Otherwise it's a
     * logo on a tile: white behind dark logos, the category colour behind light ones.
     */
    static Bitmap compose(Context c, Bitmap src, int[] look) {
        int w = src.getWidth(), h = src.getHeight();
        if (Math.min(w, h) < MIN_SOURCE) return null;
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (Math.min(w, h) >= FULL_BLEED && solidEdges(src)) {
            int s = Math.min(w, h);
            Rect from = new Rect((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
            cv.drawBitmap(src, from, new Rect(0, 0, SIZE, SIZE), p);
            return out;
        }
        if (light(src)) {
            Paint g = new Paint();
            g.setShader(new LinearGradient(0, 0, SIZE, SIZE, look[0], look[1], Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, SIZE, SIZE, g);
        } else {
            cv.drawColor(Color.WHITE);
        }
        // Small icons are drawn smaller, so they aren't blown up into a blur.
        float box = SIZE * (Math.min(w, h) >= FULL_BLEED ? 0.70f : 0.56f);
        float scale = box / Math.max(w, h);
        float dw = w * scale, dh = h * scale;
        cv.drawBitmap(src, null, new RectF((SIZE - dw) / 2, (SIZE - dh) / 2, (SIZE + dw) / 2, (SIZE + dh) / 2), p);
        return out;
    }

    /** True when the icon is opaque along its edges, so it can fill the tile edge to edge. */
    private static boolean solidEdges(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), opaque = 0, n = 0;
        for (int i = 0; i < 16; i++) {
            int x = i * (w - 1) / 15, y = i * (h - 1) / 15;
            int[] px = {b.getPixel(x, 0), b.getPixel(x, h - 1), b.getPixel(0, y), b.getPixel(w - 1, y)};
            for (int v : px) {
                n++;
                if (Color.alpha(v) > 240) opaque++;
            }
        }
        return opaque >= n * 9 / 10;
    }

    /** True when the visible part of a transparent icon is mostly white or pale. */
    private static boolean light(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), step = Math.max(1, Math.min(w, h) / 32);
        double sum = 0;
        int n = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int v = b.getPixel(x, y);
                if (Color.alpha(v) < 128) continue;
                sum += (0.299 * Color.red(v) + 0.587 * Color.green(v) + 0.114 * Color.blue(v)) / 255;
                n++;
            }
        }
        return n > 0 && sum / n > 0.8;
    }

    /** The user's own image for an app. */
    static File customFile(Context c, Store.Instance in) {
        File dir = new File(c.getFilesDir(), "icons");
        dir.mkdirs();
        return new File(dir, in.id + "-custom.png");
    }

    /**
     * Makes image bytes (from a web address or a file on the device) into the app's custom icon,
     * drawn like a site's icon. Returns false when it isn't an image or is too small to look right.
     */
    static boolean saveCustom(Context c, Store.Instance in, byte[] data) {
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length);
        Bitmap b = src == null ? null : compose(c, src, look(Catalog.get(c, in.type).category));
        if (b == null) return false;
        try (OutputStream out = new FileOutputStream(customFile(c, in))) {
            return b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return false;
        }
    }

    interface Done { void run(boolean ok); }

    /** Downloads an image for saveCustom, off the main thread. */
    static void fetchCustom(Context c, Store.Instance in, String url, Done done) {
        Context app = c.getApplicationContext();
        bg.execute(() -> {
            boolean ok;
            try {
                ok = saveCustom(app, in, get(url, MAX_ICON));
            } catch (IOException | RuntimeException e) {
                ok = false;
            }
            boolean result = ok;
            ui.post(() -> done.run(result));
        });
    }

    /** Named after the instance, its address and the drawing style, so any change redraws it. */
    private static File file(Context c, Store.Instance in) {
        File dir = new File(c.getFilesDir(), "icons");
        dir.mkdirs();
        Uri u = Uri.parse(in.url);
        return new File(dir, in.id + "-" + Integer.toHexString((u.getScheme() + u.getEncodedAuthority()).hashCode()) + "-v" + STYLE + ".png");
    }

    // --- Finding the icon ------------------------------------------------------------------

    /**
     * The largest of the site's own icons; Google's copy only when the site offers nothing usable,
     * since for some sites it's the logo of the software they run on (RepeaterBook got Joomla's).
     */
    private static Bitmap fetch(Catalog.Type t, Store.Instance in) {
        Bitmap best = null;
        for (String url : candidates(t, in)) {
            boolean google = url.startsWith("https://t1.gstatic.com/");
            if (google && best != null && Math.min(best.getWidth(), best.getHeight()) >= MIN_SOURCE) break;
            try {
                byte[] data = get(url, MAX_ICON);
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (b == null) continue;
                if (best == null || Math.min(b.getWidth(), b.getHeight()) > Math.min(best.getWidth(), best.getHeight())) best = b;
                if (Math.min(best.getWidth(), best.getHeight()) >= 180) break; // big enough
            } catch (IOException | RuntimeException ignored) {
                // Try the next one.
            }
        }
        return best;
    }

    /**
     * In order: the catalog's own pick, the web app manifest's icons (largest first), the touch
     * icon, the largest PNG icon link, the usual fixed paths, and last, for a public site only,
     * Google's favicon service (a self-hosted address is never sent anywhere). SVG can't be decoded.
     */
    private static List<String> candidates(Catalog.Type t, Store.Instance in) {
        List<String> out = new ArrayList<>();
        String root = Store.join(in.url, "/");
        if (!t.icon.isEmpty()) out.add(resolve(root, t.icon));
        try {
            String html = new String(get(in.url, MAX_HTML), "UTF-8");
            String touch = null, best = null, manifest = null;
            int bestSize = -1;
            Matcher m = LINK.matcher(html);
            while (m.find()) {
                String tag = m.group();
                String rel = attr(tag, "rel"), href = attr(tag, "href");
                if (rel == null || href == null) continue;
                String r = rel.toLowerCase();
                if (r.contains("manifest")) {
                    manifest = href;
                    continue;
                }
                if (!r.contains("icon")) continue;
                if (href.toLowerCase().contains(".svg") || "image/svg+xml".equalsIgnoreCase(attr(tag, "type"))) continue;
                if (r.contains("apple-touch-icon")) {
                    if (touch == null) touch = href;
                    continue;
                }
                int size = size(attr(tag, "sizes"));
                if (size > bestSize) {
                    bestSize = size;
                    best = href;
                }
            }
            if (manifest != null) out.addAll(manifestIcons(resolve(in.url, manifest)));
            if (touch != null) out.add(resolve(in.url, touch));
            if (best != null) out.add(resolve(in.url, best));
        } catch (IOException ignored) {
            // No page to read; the fixed paths may still work.
        }
        out.add(resolve(root, "/apple-touch-icon.png"));
        out.add(resolve(root, "/favicon.ico"));
        Uri u = Uri.parse(in.url), pub = t.url.isEmpty() ? null : Uri.parse(t.url);
        if (pub != null && u.getHost() != null && u.getHost().equals(pub.getHost())) {
            out.add("https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&size=256&url="
                    + Uri.encode(u.getScheme() + "://" + u.getHost()));
        }
        return out;
    }

    private static List<String> manifestIcons(String url) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray icons = new JSONObject(new String(get(url, MAX_HTML), "UTF-8")).optJSONArray("icons");
            List<int[]> order = new ArrayList<>();
            for (int i = 0; icons != null && i < icons.length(); i++) {
                JSONObject ic = icons.optJSONObject(i);
                if (ic == null || ic.optString("src").toLowerCase().contains(".svg")) continue;
                order.add(new int[]{size(ic.optString("sizes")), i});
            }
            java.util.Collections.sort(order, (a, b) -> Integer.compare(b[0], a[0]));
            for (int[] o : order) out.add(resolve(url, icons.optJSONObject(o[1]).optString("src")));
        } catch (Exception ignored) {
            // No manifest, or not JSON.
        }
        return out;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
    }

    private static int size(String sizes) {
        if (sizes == null) return 0;
        Matcher m = Pattern.compile("(\\d+)x\\d+").matcher(sizes);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String resolve(String base, String href) {
        try {
            return new URL(new URL(base), href).toString();
        } catch (IOException e) {
            return href;
        }
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36 ShackScreen/"
                + BuildConfig.VERSION_NAME);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                for (int n; (n = in.read(buf)) > 0 && out.size() < max; ) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    @SuppressWarnings("deprecation") // the themed getColor needs API 23; minSdk is 22
    static int color(Context c, int res) {
        return c.getResources().getColor(res);
    }
}
