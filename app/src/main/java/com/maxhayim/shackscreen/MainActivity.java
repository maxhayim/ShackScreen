package com.maxhayim.shackscreen;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The dashboard: a row of widgets the user can rearrange, and the apps by category. Works with a
 * TV remote (OK picks a widget up, arrows move it) and with touch (long-press, then the arrows).
 */
public class MainActivity extends BaseActivity {

    private static final long CLOCK_MS = 1000, TICK_MS = 15_000, HEALTH_MS = 60_000;
    private static final long IDLE_MS = 60_000, ROTATE_MS = 20_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    // Enough threads that a server that hangs until its timeout can't hold up the other widgets or the dots.
    private final ExecutorService bg = Executors.newFixedThreadPool(8);
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat utc = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat day = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());

    private TextView clockLocal, clockUtc, date, updateText;
    private View updateBadge;
    private HorizontalScrollView widgetScroll;
    private ViewGroup widgetRow, apps;
    private Panels panels;
    private List<Store.Instance> instances;
    private List<String> shown;
    private final Map<String, Card> cards = new HashMap<>();
    private final Map<String, Long> fetched = new HashMap<>();
    private final List<View> tiles = new ArrayList<>();
    private String moving;
    private long lastInput = System.currentTimeMillis();
    private int page;

    /** One widget card's views. */
    private static final class Card {
        View root, left, right;
        ViewGroup body;
        TextView source;
        String sourceText = "";
    }

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clockLocal.setText(hm.format(now));
            clockUtc.setText(utc.format(now));
            date.setText(day.format(now));
            ui.postDelayed(this, CLOCK_MS);
        }
    };

    private final Runnable tickWidgets = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            for (String id : shown) {
                Widgets.Def d = Widgets.get(id);
                Long last = fetched.get(id);
                if (last == null || now - last >= d.refreshMs) fetch(d);
            }
            ui.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable tickHealth = new Runnable() {
        @Override public void run() {
            refreshHealth();
            ui.postDelayed(this, HEALTH_MS);
        }
    };

    /** On a wall display nobody presses keys: page through the widgets that don't fit. */
    private final Runnable tickRotate = new Runnable() {
        @Override public void run() {
            int cols = columns(), pages = (shown.size() + cols - 1) / cols;
            if (Widgets.rotate(MainActivity.this) && moving == null && pages > 1
                    && System.currentTimeMillis() - lastInput > IDLE_MS) {
                page = (page + 1) % pages;
                widgetScroll.smoothScrollTo(page * cols * (cardWidth() + gap()), 0);
            }
            ui.postDelayed(this, ROTATE_MS);
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
        widgetScroll = findViewById(R.id.widget_scroll);
        widgetRow = findViewById(R.id.widgets);
        updateBadge = findViewById(R.id.update);
        updateText = findViewById(R.id.update_text);
        View settings = findViewById(R.id.settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        updateBadge.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        grow(settings, 1.05f);
        grow(updateBadge, 1.05f);
        Updater.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have changed while we were away, so rebuild from the stores each time.
        instances = Store.load(this);
        List<String> now = Widgets.shown(this);
        if (!now.equals(shown)) {
            shown = now;
            buildWidgets();
        }
        fetched.clear();
        buildAppRow();
        ui.post(tickClock);
        ui.post(tickWidgets);
        ui.post(tickHealth);
        ui.postDelayed(tickRotate, ROTATE_MS);
        showUpdateBadge();
        if (Updater.due(this)) {
            // Its own thread: a download can take minutes and must not hold up the widgets.
            new Thread(() -> {
                Updater.run(getApplicationContext(), false);
                ui.post(this::showUpdateBadge);
            }, "shackscreen-update").start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (moving != null) stopMove();
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = System.currentTimeMillis();
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        lastInput = System.currentTimeMillis();
        return super.dispatchTouchEvent(e);
    }

    @Override
    @SuppressWarnings("deprecation") // onBackPressed is what API 22-32 call
    public void onBackPressed() {
        if (moving != null) stopMove();
        else super.onBackPressed();
    }

    private void showUpdateBadge() {
        Updater.Release r = Updater.available(this);
        updateBadge.setVisibility(r == null ? View.GONE : View.VISIBLE);
        if (r != null) updateText.setText(getString(R.string.update_badge, r.version));
    }

    // --- Widget cards ------------------------------------------------------------------------

    /** Three across on a TV or landscape tablet, two on a portrait tablet, one on a phone. */
    private int columns() {
        float wDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return wDp >= 900 ? 3 : wDp >= 560 ? 2 : 1;
    }

    private int gap() { return dp(16); }

    private int cardWidth() {
        int usable = getResources().getDisplayMetrics().widthPixels - dp(96);
        int cols = columns();
        return (usable - gap() * (cols - 1)) / cols;
    }

    private void buildWidgets() {
        widgetRow.removeAllViews();
        cards.clear();
        for (String id : shown) {
            Widgets.Def d = Widgets.get(id);
            Card card = card(d);
            cards.put(id, card);
            widgetRow.addView(card.root);
        }
        layoutCards();
    }

    private void layoutCards() {
        for (int i = 0; i < widgetRow.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMarginEnd(i == widgetRow.getChildCount() - 1 ? 0 : gap());
            widgetRow.getChildAt(i).setLayoutParams(lp);
        }
    }

    private Card card(Widgets.Def d) {
        Card c = new Card();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_bg);
        root.setPadding(dp(22), dp(18), dp(22), dp(16));
        root.setFocusable(true);
        root.setClickable(true);
        root.setTag(d.id);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(d.icon);
        icon.setColorFilter(Icons.color(this, R.color.accent));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
        ilp.setMarginEnd(dp(10));
        head.addView(icon, ilp);
        c.left = arrow(R.drawable.ic_left, d.id, -1);
        head.addView(c.left);
        TextView title = new TextView(this);
        title.setText(d.title);
        title.setTextColor(Icons.color(this, R.color.text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.source = new TextView(this);
        c.source.setTextColor(Icons.color(this, R.color.faint));
        c.source.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        c.source.setSingleLine(true);
        head.addView(c.source);
        c.right = arrow(R.drawable.ic_right, d.id, 1);
        head.addView(c.right);
        root.addView(head);

        FrameLayout body = new FrameLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        blp.topMargin = dp(12);
        root.addView(body, blp);
        body.addView(panels.message(getString(R.string.loading)));
        c.body = body;
        c.root = root;
        Appearance.font(root);

        // OK on the remote, or a tap, picks the widget up and puts it down again.
        root.setOnClickListener(v -> {
            if (d.id.equals(moving)) stopMove();
            else startMove(d.id);
        });
        root.setOnLongClickListener(v -> {
            if (moving == null) startMove(d.id);
            return true;
        });
        root.setOnKeyListener((v, code, e) -> {
            if (!d.id.equals(moving)) return false;
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: return true; // stay on the widget while moving it
                default: return false;
            }
        });
        return c;
    }

    /** The on-screen arrows shown while a widget is being moved, for touch screens. */
    private View arrow(int res, String id, int delta) {
        ImageView a = new ImageView(this);
        a.setImageResource(res);
        a.setColorFilter(Icons.color(this, R.color.accent2));
        a.setPadding(dp(6), dp(2), dp(6), dp(2));
        a.setVisibility(View.GONE);
        a.setOnClickListener(v -> {
            if (id.equals(moving)) moveBy(delta);
        });
        a.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(28)));
        return a;
    }

    private void startMove(String id) {
        if (moving != null) stopMove();
        moving = id;
        Card c = cards.get(id);
        c.root.setActivated(true);
        c.root.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120);
        c.left.setVisibility(View.VISIBLE);
        c.right.setVisibility(View.VISIBLE);
        c.source.setVisibility(View.GONE); // the arrows say it all, and the title needs the room
    }

    private void moveBy(int delta) {
        int from = shown.indexOf(moving), to = from + delta;
        if (from < 0 || to < 0 || to >= shown.size()) return;
        shown.remove(from);
        shown.add(to, moving);
        View v = cards.get(moving).root;
        widgetRow.removeView(v);
        widgetRow.addView(v, to);
        layoutCards();
        v.requestFocus();
        v.post(() -> {
            // Keep the moved card in view.
            int x = v.getLeft(), w = widgetScroll.getWidth();
            if (x < widgetScroll.getScrollX() || x + v.getWidth() > widgetScroll.getScrollX() + w) widgetScroll.smoothScrollTo(x, 0);
        });
    }

    private void stopMove() {
        Card c = cards.get(moving);
        moving = null;
        Widgets.setShown(this, shown);
        if (c == null) return;
        c.root.setActivated(false);
        c.root.animate().scaleX(1f).scaleY(1f).setDuration(120);
        c.left.setVisibility(View.GONE);
        c.right.setVisibility(View.GONE);
        c.source.setText(c.sourceText);
        c.source.setVisibility(View.VISIBLE);
    }

    // --- Widget data -------------------------------------------------------------------------

    /** Tries each of the user's apps that can supply this widget, in their order, until one answers. */
    private void fetch(Widgets.Def d) {
        fetched.put(d.id, System.currentTimeMillis());
        List<Store.Instance> list = instances;
        double[] here = Widgets.latLon(this);
        bg.execute(() -> {
            Object data = null;
            String from = "", why = null;
            if (d.feed.equals("location")) {
                try {
                    data = Feeds.weather(here);
                    from = "Open-Meteo";
                } catch (Feeds.NeedsLocation e) {
                    why = getString(R.string.needs_location);
                } catch (IOException | RuntimeException e) {
                    why = getString(R.string.offline, "Open-Meteo");
                }
            } else {
                String failed = null;
                boolean needsLocation = false;
                for (Store.Instance in : list) {
                    Catalog.Type t = Catalog.get(this, in.type);
                    if (!t.feeds.contains(d.feed) || !in.hasUrl()) continue;
                    try {
                        data = read(d, t, in, here);
                        from = in.name;
                        break;
                    } catch (Feeds.NeedsLocation e) {
                        needsLocation = true;
                    } catch (IOException | RuntimeException e) {
                        if (failed == null) failed = in.name;
                    }
                }
                if (data == null) {
                    why = needsLocation ? getString(R.string.needs_location)
                            : failed != null ? getString(R.string.offline, failed)
                            : getString(d.feed.equals("mesh") ? R.string.no_mesh : R.string.no_feed);
                }
            }
            Object result = data;
            String via = from, reason = why;
            ui.post(() -> {
                Card c = cards.get(d.id);
                if (isDestroyed() || c == null) return;
                View v = result != null ? render(result) : panels.message(reason, d.icon);
                Appearance.font(v);
                c.body.removeAllViews();
                c.body.addView(v);
                c.sourceText = via;
                if (!d.id.equals(moving)) c.source.setText(via);
            });
        });
    }

    private static Object read(Widgets.Def d, Catalog.Type t, Store.Instance in, double[] here) throws IOException {
        switch (d.id) {
            case "space": return Feeds.space(t.id, in);
            case "pota": return Feeds.pota(t.id, in);
            case "dx": return Feeds.dx(t.id, in);
            case "contests": return Feeds.contests(t.id, in);
            case "dxpeditions": return Feeds.dxpeditions(t.id, in);
            case "aircraft": return Feeds.aircraft(t.id, in, here);
            case "ships": return Feeds.ships(t.id, in, here);
            case "satellites": return Feeds.satellites(t.id, in, here);
            case "satnogs": return Feeds.satnogs(t.id, in);
            case "mesh_sources": return Feeds.meshSources(in);
            case "mesh_nodes": return Feeds.meshNodes(in);
            case "mesh_messages": return Feeds.meshMessages(in);
            default: throw new IOException("unknown widget " + d.id);
        }
    }

    private View render(Object data) {
        if (data instanceof Feeds.Space) return panels.space((Feeds.Space) data);
        if (data instanceof Feeds.Weather) return panels.weather((Feeds.Weather) data);
        if (data instanceof Feeds.Mesh) return panels.mesh((Feeds.Mesh) data);
        return panels.rows((Feeds.Rows) data);
    }

    // --- Apps, grouped by category -------------------------------------------------------------

    private void buildAppRow() {
        View focused = getCurrentFocus();
        Object focusedId = focused == null ? null : focused.getTag();
        apps.removeAllViews();
        tiles.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        View focusTarget = null;
        for (String cat : Catalog.categories(this)) {
            LinearLayout group = null;
            for (Store.Instance in : instances) {
                if (!Catalog.get(this, in.type).category.equals(cat)) continue;
                if (group == null) group = group(cat);
                ViewGroup row = (ViewGroup) group.getChildAt(1);
                View tile = inf.inflate(R.layout.item_app, row, false);
                tile.setTag(in.id);
                ((TextView) tile.findViewById(R.id.name)).setText(in.name);
                ((TextView) tile.findViewById(R.id.host)).setText(
                        in.hasUrl() ? Uri.parse(in.url).getHost() : getString(R.string.needs_address));
                Icons.show(this, in, tile.findViewById(R.id.icon));
                tile.setOnClickListener(v -> open(in));
                grow(tile, 1.07f);
                Appearance.font(tile);
                row.addView(tile);
                tiles.add(tile);
                if (in.id.equals(focusedId)) focusTarget = tile;
            }
        }
        LinearLayout last = group(" ");
        ViewGroup lastRow = (ViewGroup) last.getChildAt(1);
        View add = inf.inflate(R.layout.item_app, lastRow, false);
        ((TextView) add.findViewById(R.id.name)).setText(R.string.add_app);
        ImageView plus = add.findViewById(R.id.icon);
        plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(Icons.color(this, R.color.dim));
        add.findViewById(R.id.dot).setVisibility(View.GONE);
        add.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_ADD, true)));
        grow(add, 1.07f);
        Appearance.font(add);
        lastRow.addView(add);
        if (focusTarget == null && !tiles.isEmpty()) focusTarget = tiles.get(0);
        if (focusTarget != null && moving == null) focusTarget.requestFocus();
    }

    /** A category label with its tiles in a row underneath. */
    private LinearLayout group(String name) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        TextView label = new TextView(this);
        label.setText(name.toUpperCase(Locale.US));
        label.setTextColor(Icons.color(this, R.color.dim));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setLetterSpacing(0.15f);
        label.setPadding(dp(4), 0, 0, dp(6));
        g.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        g.addView(row);
        Appearance.font(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(14));
        apps.addView(g, lp);
        return g;
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

    // --- Status dots ----------------------------------------------------------------------

    private void refreshHealth() {
        for (View tile : tiles) {
            Object id = tile.getTag();
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
