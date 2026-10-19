package com.maxhayim.shackscreen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

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
 * App tile icons, fetched from each app's own server (so a self-hosted one shows its own icon)
 * and cached. Until one is found, or if the site has none, a built-in symbol is shown.
 */
final class Icons {

    private static final ExecutorService bg = Executors.newFixedThreadPool(2);
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HTML = 256 * 1024, MAX_ICON = 1024 * 1024, MIN_SIZE = 32;

    static void show(Context c, Store.Instance in, ImageView view) {
        Catalog.Type t = Catalog.get(c, in.type);
        view.setTag(in.id);
        symbol(c, t, view);
        if (!in.hasUrl()) return;
        File cached = file(c, in);
        if (cached.exists()) {
            Bitmap b = BitmapFactory.decodeFile(cached.getPath());
            if (b != null) {
                photo(view, b);
                return;
            }
        }
        // A site with no usable icon is remembered for a day rather than asked on every visit.
        File miss = new File(cached.getPath() + ".none");
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < 24 * 60 * 60 * 1000L) return;
        Context app = c.getApplicationContext();
        bg.execute(() -> {
            Bitmap b = fetch(t, in);
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
            if (b != null) {
                ui.post(() -> {
                    if (in.id.equals(view.getTag())) photo(view, b);
                });
            }
        });
    }

    private static void symbol(Context c, Catalog.Type t, ImageView v) {
        int res = t.id.equals("meshmonitor") ? R.drawable.ic_mesh
                : t.id.equals("websdr") ? R.drawable.ic_waves
                : t.id.equals("openhamclock") || t.id.equals("shackboard") ? R.drawable.ic_antenna
                : R.drawable.ic_globe;
        v.setImageResource(res);
        int pad = Math.round(13 * c.getResources().getDisplayMetrics().density);
        v.setPadding(pad, pad, pad, pad);
        v.setColorFilter(color(c, R.color.accent));
    }

    private static void photo(ImageView v, Bitmap b) {
        v.clearColorFilter();
        int pad = Math.round(8 * v.getResources().getDisplayMetrics().density);
        v.setPadding(pad, pad, pad, pad);
        v.setImageBitmap(b);
    }

    /** Named after the instance and its address, so pointing it elsewhere fetches a fresh icon. */
    private static File file(Context c, Store.Instance in) {
        File dir = new File(c.getFilesDir(), "icons");
        dir.mkdirs();
        Uri u = Uri.parse(in.url);
        return new File(dir, in.id + "-" + Integer.toHexString((u.getScheme() + u.getEncodedAuthority()).hashCode()) + ".png");
    }

    // --- Finding the icon ------------------------------------------------------------------

    private static Bitmap fetch(Catalog.Type t, Store.Instance in) {
        for (String url : candidates(t, in)) {
            try {
                byte[] data = get(url, MAX_ICON);
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (b != null && b.getWidth() >= MIN_SIZE && b.getHeight() >= MIN_SIZE) return b;
            } catch (IOException ignored) {
                // Try the next one.
            }
        }
        return null;
    }

    /**
     * The page's own icon links first (apple-touch-icon, then the largest PNG icon), then the
     * usual fixed paths. SVG icons are skipped because Android can't decode them.
     */
    private static List<String> candidates(Catalog.Type t, Store.Instance in) {
        List<String> out = new ArrayList<>();
        String root = Store.join(in.url, "/");
        if (!t.icon.isEmpty()) out.add(resolve(root, t.icon));
        try {
            String html = new String(get(in.url, MAX_HTML), "UTF-8");
            String touch = null, best = null;
            int bestSize = -1;
            Matcher m = LINK.matcher(html);
            while (m.find()) {
                String tag = m.group();
                String rel = attr(tag, "rel"), href = attr(tag, "href");
                if (rel == null || href == null || !rel.toLowerCase().contains("icon")) continue;
                if (href.toLowerCase().contains(".svg") || "image/svg+xml".equalsIgnoreCase(attr(tag, "type"))) continue;
                if (rel.toLowerCase().contains("apple-touch-icon")) {
                    if (touch == null) touch = href;
                    continue;
                }
                int size = size(attr(tag, "sizes"));
                if (size > bestSize) {
                    bestSize = size;
                    best = href;
                }
            }
            if (touch != null) out.add(resolve(in.url, touch));
            if (best != null) out.add(resolve(in.url, best));
        } catch (IOException ignored) {
            // No page to read; the fixed paths may still work.
        }
        out.add(resolve(root, "/apple-touch-icon.png"));
        out.add(resolve(root, "/favicon.ico"));
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
        c.setRequestProperty("User-Agent", "Mozilla/5.0 ShackScreen/" + BuildConfig.VERSION_NAME);
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
