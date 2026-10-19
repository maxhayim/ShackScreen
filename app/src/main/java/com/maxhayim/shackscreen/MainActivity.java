package com.maxhayim.shackscreen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
public class MainActivity extends Activity {

    private static final long CLOCK_MS = 1000, PANELS_MS = 120_000, HEALTH_MS = 60_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newFixedThreadPool(3);
    private final SimpleDateFormat local = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat utc = new SimpleDateFormat("HH:mm", Locale.US);

    private TextView clock, updateBadge;
    private ViewGroup apps;
    private List<Store.Instance> instances;

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(local.format(now) + " local · " + utc.format(now) + " UTC");
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
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        clock = findViewById(R.id.clock);
        apps = findViewById(R.id.apps);
        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        updateBadge = findViewById(R.id.update);
        updateBadge.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        Updater.schedule(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Updater.visible++;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Updater.visible--;
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
            bg.execute(() -> {
                Updater.run(getApplicationContext(), false);
                ui.post(this::showUpdateBadge);
            });
        }
    }

    private void showUpdateBadge() {
        Updater.Release r = Updater.available(this);
        updateBadge.setVisibility(r == null ? View.GONE : View.VISIBLE);
        if (r != null) updateBadge.setText(getString(R.string.update_badge, r.version));
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

    private void buildAppRow() {
        apps.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (Store.Instance in : instances) {
            View tile = inf.inflate(R.layout.item_app, apps, false);
            tile.setTag(in.id);
            ((TextView) tile.findViewById(R.id.name)).setText(in.name);
            ((TextView) tile.findViewById(R.id.host)).setText(
                    in.hasUrl() ? Uri.parse(in.url).getHost() : getString(R.string.needs_address));
            tile.setOnClickListener(v -> open(in));
            tile.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120));
            apps.addView(tile);
        }
        View add = inf.inflate(R.layout.item_app, apps, false);
        ((TextView) add.findViewById(R.id.name)).setText(R.string.add_app);
        add.findViewById(R.id.dot).setVisibility(View.GONE);
        add.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_ADD, true)));
        add.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120));
        apps.addView(add);
        if (apps.getChildCount() > 0) apps.getChildAt(0).requestFocus();
    }

    private void open(Store.Instance in) {
        if (!in.hasUrl()) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
            return;
        }
        startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_ID, in.id));
    }

    // --- Panels ---------------------------------------------------------------------------

    private interface Source {
        String read(String type, Store.Instance in) throws IOException;
    }

    private void refreshPanels() {
        fill(R.id.space_body, R.id.space_src, "space", Feeds::space, R.string.no_source);
        fill(R.id.pota_body, R.id.pota_src, "pota", Feeds::pota, R.string.no_source);
        fill(R.id.mesh_body, R.id.mesh_src, "mesh", (type, in) -> Feeds.mesh(in), R.string.no_mesh);
    }

    /** Tries each of the user's apps that can supply this panel, in their order, until one answers. */
    private void fill(int bodyId, int srcId, String feed, Source source, int noneRes) {
        TextView body = findViewById(bodyId), src = findViewById(srcId);
        List<Store.Instance> list = instances;
        bg.execute(() -> {
            String text = null, from = null, failed = null;
            for (Store.Instance in : list) {
                Catalog.Type t = Catalog.get(this, in.type);
                if (!t.feeds.contains(feed) || !in.hasUrl()) continue;
                try {
                    text = source.read(t.id, in);
                    from = in.name;
                    break;
                } catch (IOException | RuntimeException e) {
                    if (failed == null) failed = in.name;
                }
            }
            String shown = text != null ? text
                    : failed != null ? getString(R.string.offline, failed) : getString(noneRes);
            String via = from == null ? "" : getString(R.string.via, from);
            ui.post(() -> {
                body.setText(shown);
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
                ui.post(() -> tint(dot, up ? R.color.up : R.color.down));
            });
        }
    }

    @SuppressWarnings("deprecation") // the themed getColor needs API 23; minSdk is 22
    private void tint(View dot, int colorRes) {
        GradientDrawable d = (GradientDrawable) dot.getBackground().mutate();
        d.setColor(getResources().getColor(colorRes));
    }
}
