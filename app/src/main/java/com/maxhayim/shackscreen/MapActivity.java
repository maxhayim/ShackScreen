package com.maxhayim.shackscreen;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Picks the location on a map. With a remote: the arrows move the map under the crosshair, OK
 * sets the location, and Menu zooms, searches for a place or changes the map style. On a touch
 * screen: drag, pinch and tap, then Save.
 */
public class MapActivity extends BaseActivity {

    private WebView web;
    private TextView where;
    private double lat, lon;
    private int zoom = 10;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        double[] at = Widgets.latLon(this);
        if (at != null) {
            lat = at[0];
            lon = at[1];
        } else {
            lat = 30;
            lon = -40;
            zoom = 3;
        }
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        // Only reports where the map is; the page never gets anything back from it.
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void moved(double la, double lo, int z) {
                runOnUiThread(() -> {
                    lat = la;
                    lon = lo;
                    zoom = z;
                    showWhere();
                });
            }
        }, "Picker");

        FrameLayout root = new FrameLayout(this);
        root.addView(web);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xE60B0F14);
        int pad = dp(16);
        bar.setPadding(dp(24), pad / 2, dp(24), pad / 2);
        where = new TextView(this);
        where.setTextSize(20);
        where.setTextColor(0xFFFFFFFF);
        bar.addView(where);
        TextView hint = new TextView(this);
        hint.setText(R.string.map_hint);
        hint.setTextSize(13);
        hint.setTextColor(0xFFB4BCCB);
        bar.addView(hint);
        root.addView(bar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Buttons for touch screens; a remote uses OK and Menu instead.
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(pad, pad, pad, pad);
        buttons.addView(button(R.string.map_options, this::options));
        buttons.addView(button(R.string.save, this::save));
        root.addView(buttons, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END));
        setContentView(root);
        Appearance.font(bar);
        showWhere();
        load();
        web.requestFocus();
    }

    private TextView button(int label, Runnable r) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundResource(R.drawable.pages_button);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        b.setOnClickListener(v -> r.run());
        b.setFocusable(false); // the remote's arrows move the map
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    /** The map page with the current place and style filled in. */
    private void load() {
        Maps.Style s = Maps.current(this);
        if (!"google".equals(s.provider)) {
            show(s, Maps.tiles(s));
            return;
        }
        // Google's tiles need a session opened with the key first.
        new Thread(() -> {
            String url = googleTiles(s.url, s.key);
            runOnUiThread(() -> {
                if (url == null) Toast.makeText(this, R.string.map_google_failed, Toast.LENGTH_LONG).show();
                show(s, url == null ? Maps.BUILT_IN[0].url : url);
            });
        }).start();
    }

    private void show(Maps.Style s, String tiles) {
        String html = asset("map.html")
                .replace("__LAT__", String.valueOf(lat))
                .replace("__LON__", String.valueOf(lon))
                .replace("__ZOOM__", String.valueOf(zoom))
                .replace("__TILES__", JSONObject.quote(tiles))
                .replace("__ATTRIBUTION__", JSONObject.quote(s.attribution))
                .replace("__LABELS__", JSONObject.quote(s.labels));
        // Tile servers ask apps to identify themselves; this is sent as the page's address.
        web.loadDataWithBaseURL("https://github.com/maxhayim/ShackScreen", html, "text/html", "utf-8", null);
    }

    /** Opens a Google Map Tiles API session for a map type; null if the key isn't accepted. */
    private static String googleTiles(String mapType, String key) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                    "https://tile.googleapis.com/v1/createSession?key=" + Uri.encode(key)).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Content-Type", "application/json");
            JSONObject body = new JSONObject().put("mapType", mapType).put("language", "en-US").put("region", "US");
            if (mapType.equals("terrain")) body.put("layerTypes", new JSONArray().put("layerRoadmap"));
            try (java.io.OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes("UTF-8"));
            }
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            }
            String session = new JSONObject(buf.toString("UTF-8")).getString("session");
            return "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session=" + Uri.encode(session) + "&key=" + Uri.encode(key);
        } catch (Exception e) {
            return null;
        }
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void showWhere() {
        where.setText(String.format(Locale.US, "%.4f, %.4f   ·   %s", lat, lon, Widgets.toGrid(lat, lon)));
    }

    private void js(String code) {
        web.evaluateJavascript(code, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: js("pan(-1,0)"); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: js("pan(1,0)"); return true;
            case KeyEvent.KEYCODE_DPAD_UP: js("pan(0,-1)"); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: js("pan(0,1)"); return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_ZOOM_IN: js("zoom(1)"); return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_ZOOM_OUT: js("zoom(-1)"); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: save(); return true;
            case KeyEvent.KEYCODE_MENU: options(); return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }

    private void options() {
        String[] items = {getString(R.string.map_zoom_in), getString(R.string.map_zoom_out), getString(R.string.map_search),
                getString(R.string.map_style_value, Maps.current(this).name), getString(R.string.save)};
        new AlertDialog.Builder(this, R.style.Dialog)
                .setItems(items, (d, which) -> {
                    if (which == 0) js("zoom(1)");
                    else if (which == 1) js("zoom(-1)");
                    else if (which == 2) search();
                    else if (which == 3) pickStyle(this, this::load);
                    else save();
                })
                .show();
    }

    /** Map styles, used here and in Settings. done runs after one is chosen. */
    static void pickStyle(android.app.Activity a, Runnable done) {
        java.util.List<Maps.Style> styles = Maps.all(a);
        String[] names = new String[styles.size()];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = styles.get(i).name;
            if (styles.get(i).id.equals(Maps.current(a).id)) checked = i;
        }
        new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(R.string.map_style)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    Maps.choose(a, styles.get(which));
                    done.run();
                })
                .show();
    }

    /** Finds a place by name with OpenStreetMap's Nominatim and moves the map there. */
    private void search() {
        EditText field = new EditText(this);
        field.setHint(R.string.map_search_hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        form.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.map_search)
                .setView(form)
                .setPositiveButton(R.string.map_find, (d, w) -> {
                    String q = field.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        double[] found = geocode(q);
                        runOnUiThread(() -> {
                            if (found == null) Toast.makeText(this, R.string.map_not_found, Toast.LENGTH_LONG).show();
                            else js("goTo(" + found[0] + "," + found[1] + ",12)");
                        });
                    }).start();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static double[] geocode(String q) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + Uri.encode(q);
            Object r = Net.json(url, null);
            if (!(r instanceof JSONArray) || ((JSONArray) r).length() == 0) return null;
            JSONArray a = (JSONArray) r;
            JSONObject o = a.getJSONObject(0);
            return new double[]{Double.parseDouble(o.getString("lat")), Double.parseDouble(o.getString("lon"))};
        } catch (Exception e) {
            return null;
        }
    }

    private void save() {
        Widgets.setLocation(this, String.format(Locale.US, "%.4f,%.4f", lat, lon));
        Toast.makeText(this, getString(R.string.map_saved, Widgets.toGrid(lat, lon)), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
