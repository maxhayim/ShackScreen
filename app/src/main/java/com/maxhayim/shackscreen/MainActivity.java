package com.maxhayim.shackscreen;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The dashboard: live panels from the apps' APIs, and a row of apps to open full-screen. */
public class MainActivity extends BaseActivity {

    private static final long CLOCK_MS = 1000, PANELS_MS = 120_000, HEALTH_MS = 60_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    // Enough threads that a server that hangs until its timeout can't hold up the other panels or the dots.
    private final ExecutorService bg = Executors.newFixedThreadPool(8);
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat utc = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat day = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());

    private TextView clockLocal, clockUtc, date, updateText;
    private View updateBadge;
    private ViewGroup apps;
    private Panels panels;
    private List<Store.Instance> instances;

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clockLocal.setText(hm.format(now));
            clockUtc.setText(utc.format(now));
            date.setText(day.format(now));
            ui.postDelayed(this, CLOCK_MS);
        }
    };

    private final Runnable tickPanels = new Runnable() {
        @Override public void run() {
            refreshPanels();
            ui.postDelayed(this, PANELS_MS);
        }
    };

    private final Runnable tickHealth = new Runnable() {
        @Override public void run() {
            refreshHealth();
            ui.postDelayed(this, HEALTH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        Appearance.font(findViewById(android.R.id.content));
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        panels = new Panels(this);
        clockLocal = findViewById(R.id.clock_local);
        clockUtc = findViewById(R.id.clock_utc);
        date = findViewById(R.id.date);
        apps = findViewById(R.id.apps);
        updateBadge = findViewById(R.id.update);
        updateText = findViewById(R.id.update_text);
        View settings = findViewById(R.id.settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        updateBadge.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        grow(settings, 1.05f);
        grow(updateBadge, 1.05f);
        for (int id : new int[]{R.id.space_body, R.id.pota_body, R.id.mesh_body}) {
            ((ViewGroup) findViewById(id)).addView(panels.message(getString(R.string.loading)));
        }
        Updater.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed while we were away, so rebuild from the store each time.
        instances = Store.load(this);
        buildAppRow();
        ui.post(tickClock);
        ui.post(tickPanels);
        ui.post(tickHealth);
        showUpdateBadge();
        if (Updater.due(this)) {
            // Its own thread: a download can take minutes and must not hold up the panels.
            new Thread(() -> {
                Updater.run(getApplicationContext(), false);
                ui.post(this::showUpdateBadge);
            }, "shackscreen-update").start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    private void showUpdateBadge() {
        Updater.Release r = Updater.available(this);
        updateBadge.setVisibility(r == null ? View.GONE : View.VISIBLE);
        if (r != null) updateText.setText(getString(R.string.update_badge, r.version));
    }

    private void buildAppRow() {
        View focused = getCurrentFocus();
        Object focusedId = focused == null ? null : focused.getTag();
        apps.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        View focusTarget = null;
        for (Store.Instance in : instances) {
            View tile = inf.inflate(R.layout.item_app, apps, false);
            tile.setTag(in.id);
            ((TextView) tile.findViewById(R.id.name)).setText(in.name);
            ((TextView) tile.findViewById(R.id.host)).setText(
                    in.hasUrl() ? Uri.parse(in.url).getHost() : getString(R.string.needs_address));
            Icons.show(this, in, tile.findViewById(R.id.icon));
            tile.setOnClickListener(v -> open(in));
            grow(tile, 1.07f);
            Appearance.font(tile);
            apps.addView(tile);
            if (in.id.equals(focusedId)) focusTarget = tile;
        }
        View add = inf.inflate(R.layout.item_app, apps, false);
        ((TextView) add.findViewById(R.id.name)).setText(R.string.add_app);
        ImageView plus = add.findViewById(R.id.icon);
        plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(Icons.color(this, R.color.dim));
        add.findViewById(R.id.dot).setVisibility(View.GONE);
        add.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_ADD, true)));
        grow(add, 1.07f);
        Appearance.font(add);
        apps.addView(add);
        if (focusTarget == null && apps.getChildCount() > 0) focusTarget = apps.getChildAt(0);
        if (focusTarget != null) focusTarget.requestFocus();
    }

    /** The focused tile or pill grows a little, so it's easy to spot from the couch. */
    private static void grow(View v, float scale) {
        v.setOnFocusChangeListener((view, f) ->
                view.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f).setDuration(140));
    }

    private void open(Store.Instance in) {
        if (!in.hasUrl()) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
            return;
        }
        startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_ID, in.id));
    }

    // --- Panels ---------------------------------------------------------------------------

    private interface Source<T> {
        T read(String type, Store.Instance in) throws IOException;
    }

    private interface Render<T> {
        View build(T data);
    }

    private void refreshPanels() {
        fill(R.id.space_body, R.id.space_src, "space", Feeds::space, panels::space, R.string.no_source, R.drawable.ic_sun);
        fill(R.id.pota_body, R.id.pota_src, "pota", Feeds::pota, panels::pota, R.string.no_source, R.drawable.ic_park);
        fill(R.id.mesh_body, R.id.mesh_src, "mesh", (type, in) -> Feeds.mesh(in), panels::mesh, R.string.no_mesh, R.drawable.ic_mesh);
    }

    /** Tries each of the user's apps that can supply this panel, in their order, until one answers. */
    private <T> void fill(int bodyId, int srcId, String feed, Source<T> source, Render<T> render, int noneRes, int iconRes) {
        ViewGroup body = findViewById(bodyId);
        TextView src = findViewById(srcId);
        List<Store.Instance> list = instances;
        bg.execute(() -> {
            T data = null;
            String from = null, failed = null;
            for (Store.Instance in : list) {
                Catalog.Type t = Catalog.get(this, in.type);
                if (!t.feeds.contains(feed) || !in.hasUrl()) continue;
                try {
                    data = source.read(t.id, in);
                    from = in.name;
                    break;
                } catch (IOException | RuntimeException e) {
                    if (failed == null) failed = in.name;
                }
            }
            T result = data;
            String via = from == null ? "" : from;
            String why = failed != null ? getString(R.string.offline, failed) : getString(noneRes);
            ui.post(() -> {
                if (isDestroyed()) return;
                View v = result != null ? render.build(result) : panels.message(why, iconRes);
                Appearance.font(v);
                body.removeAllViews();
                body.addView(v);
                src.setText(via);
            });
        });
    }

    // --- Status dots ----------------------------------------------------------------------

    private void refreshHealth() {
        for (int i = 0; i < apps.getChildCount(); i++) {
            View tile = apps.getChildAt(i);
            Object id = tile.getTag();
            if (id == null) continue;
            Store.Instance in = null;
            for (Store.Instance x : instances) if (x.id.equals(id)) in = x;
            if (in == null || !in.hasUrl()) continue;
            Catalog.Type t = Catalog.get(this, in.type);
            String url = t.health.isEmpty() ? in.url : Store.join(in.url, t.health);
            View dot = tile.findViewById(R.id.dot);
            bg.execute(() -> {
                boolean up = Net.up(url);
                ui.post(() -> {
                    GradientDrawable d = (GradientDrawable) dot.getBackground().mutate();
                    d.setColor(Icons.color(this, up ? R.color.up : R.color.down));
                });
            });
        }
    }
}
