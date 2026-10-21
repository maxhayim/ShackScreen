package com.maxhayim.shackscreen;

import android.content.Context;
import android.content.Intent;
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
import java.util.Collections;
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

    private static final long CLOCK_MS = 1000, TICK_MS = 15_000;
    private static final long IDLE_MS = 60_000, ROTATE_MS = 20_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    // Enough threads that a server that hangs until its timeout can't hold up the other widgets or the dots.
    private final ExecutorService bg = Executors.newFixedThreadPool(8);
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat utc = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat day = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());

    private TextView clockLocal, clockUtc, date, updateText;
    private LinearLayout callSigns;
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
        Store.Instance from; // the app whose data it shows, opened when the widget is selected
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
        callSigns = findViewById(R.id.callsigns);
        callSigns.setOnClickListener(v -> CallSigns.choose(this, () -> CallSigns.show(this, callSigns)));
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
        new Thread(() -> Backup.ifDue(getApplicationContext()), "shackscreen-backup").start();
        ui.post(tickSync);
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
        ui.postDelayed(tickRotate, ROTATE_MS);
        showUpdateBadge();
        CallSigns.show(this, callSigns);
        if (Updater.due(this)) {
            // Its own thread: a download can take minutes and must not hold up the widgets.
            new Thread(() -> {
                Updater.run(getApplicationContext(), false);
                ui.post(this::showUpdateBadge);
            }, "shackscreen-update").start();
        }
    }

    /** Picks up changes from the other TVs on opening and every 15 minutes, and shows them. */
    private final Runnable tickSync = new Runnable() {
        @Override
        public void run() {
            Context app = getApplicationContext();
            new Thread(() -> {
                if (Sync.ifDue(app)) ui.post(MainActivity.this::reloadStores);
            }, "shackscreen-sync").start();
            ui.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    /** Redraws what comes from the settings stores, after another TV changed them. */
    private void reloadStores() {
        if (isFinishing()) return;
        instances = Store.load(this);
        fetched.clear();
        buildAppRow();
        CallSigns.show(this, callSigns);
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
        else if (!Launcher.isHome(this)) super.onBackPressed(); // as the home screen there's nowhere to go back to
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
            View v = widgetRow.getChildAt(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            v.setNextFocusLeftId(View.NO_ID);
            v.setNextFocusRightId(View.NO_ID);
        }
        stopAtEnds(widgetRow);
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

        // OK or a tap opens the app behind the widget; holding it offers Move and Remove.
        // While moving, OK or a tap puts it down.
        root.setOnClickListener(v -> {
            if (d.id.equals(moving)) {
                stopMove();
                return;
            }
            if (moving != null) return;
            Store.Instance target = chosenApp(d);
            if (target == null) target = c.from;
            if (target != null) open(target);
            else widgetOpens(d); // nothing to open yet (Weather): ask which app
        });
        root.setOnLongClickListener(v -> {
            if (moving == null) widgetMenu(d);
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

    /** The app the user chose for this widget to open, if it still exists. */
    private Store.Instance chosenApp(Widgets.Def d) {
        String id = Widgets.opens(this, d.id);
        if (id.isEmpty()) return null;
        for (Store.Instance in : instances) if (in.id.equals(id)) return in;
        return null;
    }

    /** Choose the app a widget opens: the one its data comes from, or any of the user's apps. */
    private void widgetOpens(Widgets.Def d) {
        List<Store.Instance> apps = new ArrayList<>();
        for (Store.Instance in : instances) if (in.hasUrl()) apps.add(in);
        Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
        String[] items = new String[apps.size() + 1];
        items[0] = getString(R.string.opens_source);
        int checked = 0;
        String current = Widgets.opens(this, d.id);
        for (int i = 0; i < apps.size(); i++) {
            items[i + 1] = apps.get(i).name;
            if (apps.get(i).id.equals(current)) checked = i + 1;
        }
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(getString(R.string.opens_title, getString(d.title)))
                .setSingleChoiceItems(items, checked, (dlg, which) -> {
                    dlg.dismiss();
                    Widgets.setOpens(this, d.id, which == 0 ? "" : apps.get(which - 1).id);
                })
                // Back on the widget, not wherever Android puts focus after a dialog.
                .setOnDismissListener(x -> {
                    Card c = cards.get(d.id);
                    if (c != null) c.root.requestFocus();
                })
                .show();
    }

    private String opensLabel(Widgets.Def d) {
        Store.Instance in = chosenApp(d);
        Card c = cards.get(d.id);
        String name = in != null ? in.name : c != null && c.from != null ? c.from.name : getString(R.string.opens_nothing);
        return getString(R.string.opens_value, name);
    }

    private void widgetMenu(Widgets.Def d) {
        String[] items = {getString(R.string.move), opensLabel(d), getString(R.string.remove_home)};
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(d.title)
                .setItems(items, (dlg, which) -> {
                    if (which == 0) {
                        startMove(d.id);
                    } else if (which == 1) {
                        widgetOpens(d);
                    } else {
                        shown.remove(d.id);
                        Widgets.setShown(this, shown);
                        buildWidgets();
                        for (String id : shown) fetch(Widgets.get(id));
                    }
                })
                .show();
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
            Store.Instance source = null;
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
                        source = in;
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
            Store.Instance supplier = source;
            ui.post(() -> {
                Card c = cards.get(d.id);
                if (isDestroyed() || c == null) return;
                View v = result != null ? render(result) : panels.message(reason, d.icon);
                Appearance.font(v);
                c.body.removeAllViews();
                c.body.addView(v);
                c.sourceText = via;
                c.from = supplier;
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

    // --- Home row: the user's favourite apps and the All apps button ---------------------------

    /** The row as shown: home apps with null standing for All apps (at the end unless moved). */
    private List<Store.Instance> slots() {
        List<Store.Instance> slots = new ArrayList<>(Store.home(instances));
        int at = Store.allAppsAt(this);
        slots.add(at < 0 || at > slots.size() ? slots.size() : at, null);
        return slots;
    }

    private void buildAppRow() {
        View focused = getCurrentFocus();
        Object focusedId = focused == null ? null : focused.getTag();
        apps.removeAllViews();
        tiles.clear();
        View focusTarget = null;
        for (Store.Instance in : slots()) {
            View tile;
            if (in == null) {
                tile = Icons.button(this, apps, getString(R.string.all_apps), R.drawable.ic_grid, 0xFF4B5563, 0xFF111827);
                tile.setTag("all");
                tile.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
            } else {
                tile = Icons.tile(this, apps, in);
                tile.setOnClickListener(v -> open(in));
                tiles.add(tile);
            }
            tile.setOnLongClickListener(v -> {
                dockMenu(in);
                return true;
            });
            apps.addView(tile);
            if (tile.getTag().equals(focusedId)) focusTarget = tile;
        }
        stopAtEnds(apps);
        if (focusTarget == null) focusTarget = apps.getChildAt(0);
        if (moving == null && focusTarget != null) focusTarget.requestFocus();
    }

    /** Left on the first item and Right on the last stay put, instead of jumping to another row. */
    private static void stopAtEnds(ViewGroup row) {
        int n = row.getChildCount();
        if (n == 0) return;
        View first = row.getChildAt(0), last = row.getChildAt(n - 1);
        if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
        if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    /** Move left and right for any icon (All apps included); remove and edit for apps. */
    private void dockMenu(Store.Instance in) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.move_left));
        items.add(getString(R.string.move_right));
        if (in != null) {
            items.add(getString(R.string.remove_home));
            if (!Launcher.is(in)) items.add(getString(R.string.edit)); // an Android app has nothing to edit
        }
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(in == null ? getString(R.string.all_apps) : in.name)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which <= 1) {
                        List<Store.Instance> slots = slots();
                        int from = slots.indexOf(in), to = from + (which == 0 ? -1 : 1);
                        if (to < 0 || to >= slots.size()) return;
                        slots.set(from, slots.get(to));
                        slots.set(to, in);
                        Store.saveHome(this, instances, slots);
                    } else if (which == 2) {
                        // An Android app off the home row is just gone; it's still in Android apps.
                        if (Launcher.is(in)) instances.remove(in);
                        else in.home = false;
                        Store.save(this, instances);
                    } else {
                        startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
                        return;
                    }
                    buildAppRow();
                    String tag = in == null ? "all" : in.id;
                    for (int i = 0; i < apps.getChildCount(); i++) {
                        if (tag.equals(apps.getChildAt(i).getTag())) apps.getChildAt(i).requestFocus();
                    }
                })
                .show();
    }

    /** The focused tile or pill grows a little, so it's easy to spot from the couch. */
    private static void grow(View v, float scale) {
        v.setOnFocusChangeListener((view, f) ->
                view.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f).setDuration(140));
    }

    private void open(Store.Instance in) {
        if (Launcher.is(in)) {
            Launcher.launch(this, Launcher.pkg(in));
            return;
        }
        if (!in.hasUrl()) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
            return;
        }
        startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_ID, in.id));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
