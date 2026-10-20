package com.maxhayim.shackscreen;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Apps (each pointing at a public site or the user's own server), widgets, location, theme and
 * updates. Works with a remote and with touch.
 */
public class SettingsActivity extends BaseActivity {

    static final String EXTRA_ADD = "add", EXTRA_EDIT = "edit";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list, widgetList;
    private List<Store.Instance> instances;
    private View modeButton, installButton;
    private TextView updateStatus;

    // The app whose icon is being picked from the device's files, while the picker is open.
    private static final int PICK_ICON = 7;
    private Store.Instance iconFor;
    private boolean iconIsNew;
    private Button iconButton;

    // The updater works on its own thread and reports through its saved status; show it as it changes.
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            showUpdates();
            ui.postDelayed(this, 1000);
        }
    };

    // The sections down the side: key, title, symbol.
    private static final String APPS = "apps", WIDGETS = "widgets", CALLS = "calls", LOCATION = "location",
            LOOK = "look", LAUNCHER = "launcher", UPDATES = "updates";
    private static final Object[][] SECTIONS = {
            {APPS, R.string.applications, R.drawable.ic_grid},
            {WIDGETS, R.string.widgets, R.drawable.ic_waves},
            {CALLS, R.string.profiles, R.drawable.ic_radio},
            {LOCATION, R.string.location, R.drawable.ic_globe},
            {LOOK, R.string.appearance, R.drawable.ic_theme},
            {LAUNCHER, R.string.launcher, R.drawable.ic_grid},
            {UPDATES, R.string.updates, R.drawable.ic_update}};

    private String section = APPS;
    private boolean building;
    private LinearLayout side, pane;
    private TextView paneTitle, paneHint;
    private ScrollView paneScroll;
    private final List<View> sideItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) section = state.getString("section", APPS);
        LinearLayout root = new LinearLayout(this);

        // Sections down the left, like All applications.
        ScrollView sideScroll = new ScrollView(this);
        sideScroll.setBackgroundColor(color(R.color.card));
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(16), dp(24), dp(16), dp(24));
        sideScroll.addView(side);
        root.addView(sideScroll, new LinearLayout.LayoutParams(dp(236), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(6), 0, 0, dp(18));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.ic_settings);
        logo.setColorFilter(color(R.color.text)); // as on the dashboard's Settings button
        brand.addView(logo, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView name = text(getString(R.string.settings), 21, R.color.text);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setPadding(dp(12), 0, 0, 0);
        brand.addView(name);
        side.addView(brand);
        for (Object[] sec : SECTIONS) side.addView(sideItem((String) sec[0], getString((Integer) sec[1]), (Integer) sec[2]));

        // The chosen section's settings on the right.
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(32), dp(24), dp(40), 0);
        paneTitle = text("", 28, R.color.text);
        paneTitle.setTypeface(paneTitle.getTypeface(), android.graphics.Typeface.BOLD);
        main.addView(paneTitle);
        paneHint = text("", 13, R.color.dim);
        paneHint.setPadding(0, dp(4), 0, dp(14));
        main.addView(paneHint);
        paneScroll = new ScrollView(this);
        paneScroll.setClipToPadding(false);
        paneScroll.setPadding(0, 0, 0, dp(24));
        pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        paneScroll.addView(pane);
        main.addView(paneScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(main, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        setContentView(root);

        instances = Store.load(this);
        buildPane();
        for (View v : sideItems) if (section.equals(v.getTag())) v.requestFocus();
        if (getIntent().getBooleanExtra(EXTRA_ADD, false)) pickType();
        String editId = getIntent().getStringExtra(EXTRA_EDIT);
        if (editId != null) {
            for (Store.Instance in : instances) if (in.id.equals(editId)) edit(in, false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("section", section); // a theme change recreates the screen
    }

    @Override
    protected void onStart() {
        super.onStart();
        ui.post(poll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the map, or from Android's home app setting.
        if ((LOCATION.equals(section) || LAUNCHER.equals(section)) && pane != null) buildPane();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(poll);
    }

    // --- Sections -----------------------------------------------------------------------------

    private View sideItem(String key, String label, int symbol) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(key);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(symbol);
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(label, 16, R.color.text);
        t.setPadding(dp(14), 0, 0, 0);
        t.setSingleLine(true);
        row.addView(t);
        // Moving through the list shows each section, as in All applications.
        row.setOnFocusChangeListener((v, f) -> {
            if (f) select(key);
        });
        row.setOnClickListener(v -> select(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        sideItems.add(row);
        return row;
    }

    private void select(String key) {
        if (building) return; // focus moving while the pane is redrawn, not the user
        if (key.equals(section) && pane.getChildCount() > 0) return;
        section = key;
        buildPane();
    }

    /** The chosen section is a solid pill; the focused one also gets the usual amber outline. */
    private void styleSide() {
        int text = color(R.color.text), dim = color(R.color.dim), ink = color(R.color.bg_bottom);
        for (View v : sideItems) {
            boolean on = section.equals(v.getTag());
            android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable focused = pill(on ? text : color(R.color.tile_focus));
            focused.setStroke(dp(2), color(R.color.accent));
            bg.addState(new int[]{android.R.attr.state_focused}, focused);
            bg.addState(new int[]{}, pill(on ? text : 0));
            v.setBackground(bg);
            LinearLayout row = (LinearLayout) v;
            ((android.widget.ImageView) row.getChildAt(0)).setColorFilter(on ? ink : dim);
            TextView t = (TextView) row.getChildAt(1);
            t.setTextColor(on ? ink : text);
            t.setTypeface(android.graphics.Typeface.create(t.getTypeface(), on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        }
    }

    private android.graphics.drawable.GradientDrawable pill(int c) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(14));
        return d;
    }

    private void buildPane() {
        // Redrawing removes the focused row, and Android would hand focus to the first section.
        building = true;
        View had = getCurrentFocus();
        boolean inPane = had != null && isIn(had, pane);
        styleSide();
        pane.removeAllViews();
        modeButton = null;
        installButton = null;
        updateStatus = null;
        list = null;
        widgetList = null;
        switch (section) {
            case APPS: appsPane(); break;
            case WIDGETS: widgetsPane(); break;
            case CALLS: callsPane(); break;
            case LOCATION: locationPane(); break;
            case LOOK: lookPane(); break;
            case LAUNCHER: launcherPane(); break;
            default: updatesPane();
        }
        Appearance.font(pane);
        Appearance.font(side);
        paneScroll.scrollTo(0, 0);
        View keep = null;
        if (inPane) {
            keep = firstFocusable(pane);
        } else {
            for (View v : sideItems) if (section.equals(v.getTag())) keep = v;
        }
        if (keep != null) keep.requestFocus();
        ui.post(() -> building = false);
    }

    private static boolean isIn(View v, View parent) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == parent) return true;
        return false;
    }

    private static View firstFocusable(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.isFocusable() && c.getVisibility() == View.VISIBLE) return c;
            if (c instanceof android.view.ViewGroup) {
                View f = firstFocusable((android.view.ViewGroup) c);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void head(int title, String hint) {
        paneTitle.setText(title);
        paneHint.setText(hint);
        paneHint.setVisibility(hint.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void appsPane() {
        head(R.string.applications, getString(R.string.settings_hint));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        pane.addView(list);
        rebuild();
        View add = row(R.drawable.ic_plus, getString(R.string.add_application_long), "");
        add.setOnClickListener(v -> pickType());
        pane.addView(add);
    }

    private void widgetsPane() {
        head(R.string.widgets, getString(R.string.widgets_hint));
        widgetList = new LinearLayout(this);
        widgetList.setOrientation(LinearLayout.VERTICAL);
        pane.addView(widgetList);
        rebuildWidgets();
        View rotate = row(0, getString(R.string.rotate), getString(Widgets.rotate(this) ? R.string.on : R.string.off));
        rotate.setOnClickListener(v -> {
            Widgets.setRotate(this, !Widgets.rotate(this));
            setValue(rotate, getString(Widgets.rotate(this) ? R.string.on : R.string.off));
        });
        pane.addView(rotate);
    }

    /** Profiles: each holds call signs for the clock. Demo comes with every install and can't be changed. */
    private void callsPane() {
        head(R.string.profiles, getString(R.string.profiles_hint));
        String active = CallSigns.active(this).id;
        for (CallSigns.Profile p : CallSigns.profiles(this)) {
            boolean on = p.id.equals(active);
            View r = row(on ? R.drawable.ic_radio : 0, p.name + (on ? "   ·   " + getString(R.string.profile_in_use) : ""), CallSigns.summary(p));
            r.setOnClickListener(x -> profileMenu(p));
            pane.addView(r);
        }
        View add = row(0, "+ " + getString(R.string.profile_add), "");
        add.setOnClickListener(x -> CallSigns.edit(this, null, this::buildPane));
        pane.addView(add);
    }

    private void profileMenu(CallSigns.Profile p) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.profile_use));
        if (!p.demo()) {
            items.add(getString(R.string.profile_edit));
            items.add(getString(R.string.profile_delete));
        }
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(p.name)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) CallSigns.use(this, p);
                    else if (which == 1) {
                        CallSigns.edit(this, p, this::buildPane);
                        return;
                    } else CallSigns.delete(this, p);
                    buildPane();
                })
                .show();
    }

    private void locationPane() {
        head(R.string.location, getString(R.string.location_why));
        View map = row(R.drawable.ic_globe, getString(R.string.pick_on_map), locationValue());
        map.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        pane.addView(map);
        View typed = row(0, getString(R.string.type_location), "");
        typed.setOnClickListener(v -> editLocation(this::buildPane));
        pane.addView(typed);
        View style = row(0, getString(R.string.map_style), Maps.current(this).name);
        style.setOnClickListener(v -> MapActivity.pickStyle(this, this::buildPane));
        pane.addView(style);
        // The user's own maps: select one to remove it.
        for (Maps.Style m : Maps.custom(this)) {
            View r = row(0, m.name, Maps.describe(m));
            r.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Dialog)
                    .setTitle(getString(R.string.remove_map, m.name))
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        Maps.removeCustom(this, m);
                        buildPane();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
            pane.addView(r);
        }
        View keyed = row(R.drawable.ic_plus, getString(R.string.add_keyed_map), "");
        keyed.setOnClickListener(v -> addKeyedMap());
        pane.addView(keyed);
        View add = row(R.drawable.ic_plus, getString(R.string.add_map), "");
        add.setOnClickListener(v -> addMap());
        pane.addView(add);
    }

    /** A provider, then one of its styles, then the user's own key for it. */
    private void addKeyedMap() {
        String[] names = new String[Maps.PROVIDERS.length];
        for (int i = 0; i < names.length; i++) names[i] = Maps.PROVIDERS[i].name;
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.add_keyed_map)
                .setItems(names, (d, which) -> {
                    Maps.Provider p = Maps.PROVIDERS[which];
                    String[] styles = new String[p.styles.length];
                    for (int i = 0; i < styles.length; i++) styles[i] = p.styles[i][0];
                    new AlertDialog.Builder(this, R.style.Dialog)
                            .setTitle(p.name)
                            .setItems(styles, (d2, w2) -> askKey(p, p.styles[w2]))
                            .show();
                })
                .show();
    }

    private void askKey(Maps.Provider p, String[] style) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        form.addView(text(getString(R.string.map_key_note), 13, R.color.dim));
        EditText key = field(getString(R.string.map_key, p.name), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        form.addView(key);
        Appearance.font(form);
        quiet(new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(p.name + " " + style[0])
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String k = key.getText().toString().trim();
                    if (k.isEmpty()) return;
                    if (!Maps.addKeyed(this, p, style, k)) {
                        android.widget.Toast.makeText(this, R.string.logins_unsupported, android.widget.Toast.LENGTH_LONG).show();
                    }
                    buildPane();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** A map from any tile server, by its {z}/{x}/{y} address. */
    private void addMap() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = field(getString(R.string.map_name), "", InputType.TYPE_CLASS_TEXT);
        EditText url = field(getString(R.string.map_url), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(name);
        form.addView(url);
        quiet(new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.add_map)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (!Maps.valid(u)) {
                        android.widget.Toast.makeText(this, R.string.map_bad_url, android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = name.getText().toString().trim();
                    Maps.addCustom(this, n.isEmpty() ? Uri.parse(u).getHost() : n, u);
                    buildPane();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void lookPane() {
        head(R.string.appearance, "");
        View r = row(R.drawable.ic_theme, getString(R.string.pick_theme), Appearance.label(this, Appearance.mode(this)));
        r.setOnClickListener(v -> pickTheme());
        pane.addView(r);
        View icons = row(R.drawable.ic_grid, getString(R.string.icon_style), iconStyleLabel(Appearance.iconSetting(this)));
        icons.setOnClickListener(v -> {
            String[] ids = Appearance.ICON_STYLES;
            String[] names = new String[ids.length];
            String suggested = Appearance.suggestedIcons(this);
            for (int i = 0; i < ids.length; i++) {
                names[i] = iconStyleLabel(ids[i]) + (ids[i].equals(suggested) ? "   ·   " + getString(R.string.icons_suggested) : "");
            }
            int current = java.util.Arrays.asList(ids).indexOf(Appearance.iconSetting(this));
            new AlertDialog.Builder(this, R.style.Dialog)
                    .setTitle(R.string.icon_style)
                    .setSingleChoiceItems(names, current, (d, which) -> {
                        d.dismiss();
                        Appearance.setIconSetting(this, ids[which]);
                        setValue(icons, iconStyleLabel(ids[which]));
                    })
                    .show();
        });
        pane.addView(icons);
    }

    /** Launcher mode: off by default; on, ShackScreen can be the home screen and lists the device's apps. */
    private void launcherPane() {
        head(R.string.launcher, getString(R.string.launcher_hint));
        boolean on = Launcher.enabled(this);
        View mode = row(R.drawable.ic_grid, getString(R.string.launcher_mode), getString(on ? R.string.on : R.string.off));
        mode.setOnClickListener(v -> {
            if (Launcher.enabled(this)) {
                turnLauncherOff();
                return;
            }
            Launcher.setEnabled(this, true);
            buildPane();
            Launcher.chooseHome(this);
        });
        pane.addView(mode);
        if (on) {
            View home = row(0, getString(R.string.launcher_home), getString(Launcher.isHome(this) ? R.string.launcher_is_home : R.string.launcher_not_home));
            home.setOnClickListener(v -> Launcher.chooseHome(this));
            pane.addView(home);
            View settings = row(R.drawable.ic_settings, getString(R.string.android_settings), "");
            settings.setOnClickListener(v -> Launcher.openSettings(this));
            pane.addView(settings);
        }
    }

    private String iconStyleLabel(String id) {
        switch (id) {
            case Appearance.ICONS_DARK: return getString(R.string.icons_dark);
            case Appearance.ICONS_LIGHT: return getString(R.string.icons_light);
            case Appearance.ICONS_AUTO: return getString(R.string.icons_auto);
            default: return getString(R.string.icons_color);
        }
    }

    /**
     * Switching the home entry off makes Android close ShackScreen and show the TV's own home
     * screen, so say so first.
     */
    private void turnLauncherOff() {
        android.widget.Toast.makeText(getApplicationContext(), R.string.launcher_off_done, android.widget.Toast.LENGTH_LONG).show();
        Launcher.setEnabled(this, false);
        buildPane();
    }

    private void updatesPane() {
        head(R.string.updates, getString(R.string.version, BuildConfig.VERSION_NAME));
        View mode = row(R.drawable.ic_update, getString(R.string.updates), "");
        mode.setOnClickListener(v -> {
            String m = Updater.mode(this);
            Updater.setMode(this, Updater.OFF.equals(m) ? Updater.NOTIFY : Updater.NOTIFY.equals(m) ? Updater.AUTO : Updater.OFF);
            showUpdates();
        });
        pane.addView(mode);
        modeButton = mode;
        View check = row(0, getString(R.string.check_now), "");
        check.setOnClickListener(v -> {
            Updater.setStatus(this, getString(R.string.checking));
            new Thread(() -> Updater.run(getApplicationContext(), true)).start();
        });
        pane.addView(check);
        installButton = row(0, "", "");
        installButton.setOnClickListener(v -> {
            Updater.Release r = Updater.available(this);
            if (r != null) new Thread(() -> Updater.install(getApplicationContext(), r)).start();
        });
        pane.addView(installButton);
        updateStatus = text("", 15, R.color.dim);
        updateStatus.setPadding(dp(4), dp(8), 0, 0);
        pane.addView(updateStatus);
        showUpdates();
    }

    private void showUpdates() {
        if (modeButton == null) return;
        String m = Updater.mode(this);
        setValue(modeButton, getString(Updater.AUTO.equals(m) ? R.string.mode_auto
                : Updater.NOTIFY.equals(m) ? R.string.mode_notify : R.string.mode_off));
        Updater.Release r = Updater.available(this);
        installButton.setVisibility(r == null ? View.GONE : View.VISIBLE);
        if (r != null) ((TextView) installButton.getTag(R.id.row_title)).setText(getString(R.string.install_version, r.version));
        updateStatus.setText(Updater.status(this));
    }

    /**
     * A settings row: optional symbol, the setting's name, and its current value on the right.
     * Its value is changed with setValue.
     */
    private LinearLayout row(int symbol, String title, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(14), dp(20), dp(14));
        r.setBackgroundResource(R.drawable.tile_bg);
        r.setFocusable(true);
        r.setClickable(true);
        if (symbol != 0) {
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(symbol);
            icon.setColorFilter(color(R.color.dim));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
            ip.setMarginEnd(dp(16));
            r.addView(icon, ip);
        }
        TextView t = text(title, 17, R.color.text);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        r.setTag(R.id.row_title, t);
        TextView v = text(value, 15, R.color.dim);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        v.setGravity(android.view.Gravity.END);
        v.setPadding(dp(16), 0, 0, 0);
        r.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        r.setTag(R.id.row_value, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private static void setValue(View row, String value) {
        ((TextView) row.getTag(R.id.row_value)).setText(value);
    }

    /** The user's apps, each with its icon and address. Called again after one is added, changed or deleted. */
    private void rebuild() {
        if (list == null) return;
        list.removeAllViews();
        for (Store.Instance in : instances) {
            if (Launcher.is(in)) continue; // Android apps are managed in All applications
            String where = in.hasUrl() ? in.url : getString(R.string.needs_address);
            LinearLayout r = row(0, in.name, where);
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            android.graphics.drawable.GradientDrawable shape = pill(color(R.color.icon_bg));
            shape.setCornerRadius(dp(9)); // a rounded square, like the icons everywhere else
            frame.setBackground(shape);
            frame.setClipToOutline(true);
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            frame.addView(icon, new android.widget.FrameLayout.LayoutParams(dp(34), dp(34)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34));
            ip.setMarginEnd(dp(16));
            r.addView(frame, 0, ip);
            Icons.show(this, in, icon);
            r.setPadding(dp(12), dp(10), dp(20), dp(10));
            r.setOnClickListener(v -> edit(in, false));
            list.addView(r);
        }
        Appearance.font(list);
    }

    /**
     * First a category, then an app in it. "Request an app…" and "Other web page" come last,
     * so a missing app can be asked for right where it would have been.
     */
    private void pickType() {
        List<String> cats = new ArrayList<>(Catalog.categories(this));
        cats.remove("Other");
        List<String> items = new ArrayList<>(cats);
        items.add(getString(R.string.request_app));
        items.add(Catalog.get(this, "custom").name);
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.pick_category)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < cats.size()) pickInCategory(cats.get(which));
                    else if (which == cats.size()) Requests.show(this, "", "");
                    else edit(Store.create(Catalog.get(this, "custom")), true);
                })
                .show();
    }

    private void pickInCategory(String cat) {
        List<Catalog.Type> types = new ArrayList<>();
        for (Catalog.Type t : Catalog.all(this)) if (t.category.equals(cat)) types.add(t);
        String[] names = new String[types.size()];
        for (int i = 0; i < names.length; i++) names[i] = types.get(i).name;
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(cat)
                .setItems(names, (d, which) -> edit(Store.create(types.get(which)), true))
                .show();
    }

    // --- Widgets ---------------------------------------------------------------------------

    /** Shown widgets first, in dashboard order, then the hidden ones. Selecting one shows or hides it. */
    private void rebuildWidgets() {
        if (widgetList == null) return;
        widgetList.removeAllViews();
        List<String> shown = Widgets.shown(this);
        List<Widgets.Def> ordered = new ArrayList<>();
        for (String id : shown) ordered.add(Widgets.get(id));
        for (Widgets.Def d : Widgets.ALL) if (!shown.contains(d.id)) ordered.add(d);
        for (Widgets.Def d : ordered) {
            boolean on = shown.contains(d.id);
            LinearLayout b = row(d.icon, getString(d.title), getString(on ? R.string.shown : R.string.hidden));
            ((TextView) b.getTag(R.id.row_value)).setTextColor(color(on ? R.color.good : R.color.dim));
            if (!on) ((TextView) b.getTag(R.id.row_title)).setTextColor(color(R.color.dim));
            b.setOnClickListener(v -> {
                List<String> now = Widgets.shown(this);
                if (now.contains(d.id)) now.remove(d.id);
                else now.add(d.id);
                Widgets.setShown(this, now);
                rebuildWidgets();
                // Keep the remote's focus on the row that was just toggled.
                for (int i = 0; i < widgetList.getChildCount(); i++) {
                    if (d.id.equals(widgetList.getChildAt(i).getTag())) widgetList.getChildAt(i).requestFocus();
                }
            });
            b.setTag(d.id);
            widgetList.addView(b);
        }
        Appearance.font(widgetList);
    }

    private String locationValue() {
        String loc = Widgets.location(this);
        double[] at = Widgets.parseLocation(loc);
        if (at == null) return getString(R.string.not_set);
        return loc + "   ·   " + Widgets.toGrid(at[0], at[1]);
    }

    private void editLocation(Runnable done) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText field = field(getString(R.string.location_hint), Widgets.location(this), InputType.TYPE_CLASS_TEXT);
        form.addView(field);
        quiet(new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.location)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String s = field.getText().toString();
                    if (!s.trim().isEmpty() && Widgets.parseLocation(s) == null) {
                        android.widget.Toast.makeText(this, R.string.location_hint, android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    Widgets.setLocation(this, s);
                    done.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void edit(Store.Instance in, boolean isNew) {
        Catalog.Type type = Catalog.get(this, in.type);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        EditText name = field(getString(R.string.name), in.name, InputType.TYPE_CLASS_TEXT);
        EditText url = field(getString(R.string.address), in.url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText token = field(getString(R.string.token), in.token, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(name);
        // Apps with a public site that people also run themselves: pick which one this is.
        boolean both = !type.url.isEmpty() && type.port > 0;
        if (both) {
            android.widget.RadioGroup where = new android.widget.RadioGroup(this);
            android.widget.RadioButton pub = new android.widget.RadioButton(this);
            pub.setText(getString(R.string.use_public, Uri.parse(type.url).getHost()));
            pub.setId(View.generateViewId());
            android.widget.RadioButton own = new android.widget.RadioButton(this);
            own.setText(R.string.use_own);
            own.setId(View.generateViewId());
            where.addView(pub);
            where.addView(own);
            boolean isPublic = in.url.isEmpty() || Uri.parse(in.url).getHost() != null
                    && Uri.parse(in.url).getHost().equals(Uri.parse(type.url).getHost());
            where.check(isPublic ? pub.getId() : own.getId());
            url.setEnabled(!isPublic);
            if (isPublic) url.setText(type.url);
            where.setOnCheckedChangeListener((g, id) -> {
                boolean p = id == pub.getId();
                url.setEnabled(!p);
                url.setText(p ? type.url : "");
                if (!p) url.requestFocus();
            });
            form.addView(where);
        }
        form.addView(url);
        if (type.token) form.addView(token);
        if (type.port > 0) {
            form.addView(text(getString(R.string.port_hint, type.port), 13, R.color.dim));
        }
        // Extra pages: more hub URLs, a second dashboard, anything to switch to from inside the app.
        Button pagesButton = button(pagesLabel(in));
        pagesButton.setOnClickListener(v -> editPages(in, isNew, pagesButton));
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) pagesButton.getLayoutParams();
        plp.topMargin = dp(10);
        form.addView(pagesButton);
        Button zoomBtn = button(getString(R.string.zoom_value, ViewerActivity.zoom(this, in)));
        zoomBtn.setOnClickListener(v -> ViewerActivity.pickZoom(this, in, z -> {
            in.zoom = z;
            if (!isNew) Store.save(this, instances);
            zoomBtn.setText(getString(R.string.zoom_value, z));
        }));
        form.addView(zoomBtn);
        Button iconBtn = button(iconLabel(in));
        iconBtn.setOnClickListener(v -> editIcon(in, isNew, iconBtn));
        form.addView(iconBtn);
        Button loginsBtn = button(loginsLabel(in));
        loginsBtn.setEnabled(Logins.supported());
        loginsBtn.setOnClickListener(v -> editLogins(in, loginsBtn));
        form.addView(loginsBtn);
        if (type.id.equals("custom")) {
            // A page someone adds by hand may be worth having in everyone's menu.
            Button ask = button(getString(R.string.request_it));
            ask.setOnClickListener(v -> Requests.show(this, name.getText().toString().trim(), url.getText().toString().trim()));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ask.getLayoutParams();
            lp.topMargin = dp(10);
            form.addView(ask);
        }
        Appearance.font(form);

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(type.name)
                .setView(scrolling(form))
                .setPositiveButton(R.string.save, (d, w) -> {
                    in.name = name.getText().toString().trim();
                    if (in.name.isEmpty()) in.name = type.name;
                    in.url = Store.normalise(url.getText().toString(), type.port);
                    in.token = token.getText().toString().trim();
                    if (isNew) {
                        in.home = true; // something you just added belongs where you'll see it
                        instances.add(in);
                    }
                    Store.save(this, instances);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null);
        if (!isNew) {
            b.setNeutralButton(R.string.delete, (d, w) -> {
                instances.remove(in);
                Store.save(this, instances);
                rebuild();
            });
        }
        quiet(b.show());
    }

    private String pagesLabel(Store.Instance in) {
        return getString(R.string.extra_pages, in.pages.size());
    }

    /** The user's own pages for an app: select one to remove it, or add a new one. */
    private void editPages(Store.Instance in, boolean isNew, Button label) {
        String[] items = new String[in.pages.size() + 1];
        for (int i = 0; i < in.pages.size(); i++) items[i] = in.pages.get(i)[0] + "   ·   " + in.pages.get(i)[1];
        items[in.pages.size()] = getString(R.string.add_page);
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.pages)
                .setItems(items, (d, which) -> {
                    if (which < in.pages.size()) {
                        String[] page = in.pages.get(which);
                        new AlertDialog.Builder(this, R.style.Dialog)
                                .setTitle(page[0])
                                .setMessage(page[1])
                                .setPositiveButton(R.string.delete, (d2, w2) -> {
                                    in.pages.remove(page);
                                    savePages(in, isNew, label);
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                        return;
                    }
                    LinearLayout form = new LinearLayout(this);
                    form.setOrientation(LinearLayout.VERTICAL);
                    form.setPadding(dp(24), dp(8), dp(24), 0);
                    EditText name = field(getString(R.string.page_name), "", InputType.TYPE_CLASS_TEXT);
                    EditText url = field(getString(R.string.page_url), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                    form.addView(name);
                    form.addView(url);
                    quiet(new AlertDialog.Builder(this, R.style.Dialog)
                            .setTitle(R.string.add_page)
                            .setView(form)
                            .setPositiveButton(R.string.save, (d2, w2) -> {
                                String u = Store.normalise(url.getText().toString(), 0);
                                if (u.isEmpty()) return;
                                String n = name.getText().toString().trim();
                                in.pages.add(new String[]{n.isEmpty() ? Uri.parse(u).getHost() : n, u});
                                savePages(in, isNew, label);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show());
                })
                .show();
    }

    /** A saved app's pages are stored right away; a new app's go with it when it's saved. */
    private void savePages(Store.Instance in, boolean isNew, Button label) {
        if (!isNew) Store.save(this, instances);
        label.setText(pagesLabel(in));
    }

    // --- Icon ----------------------------------------------------------------------------------

    private String iconLabel(Store.Instance in) {
        int v = in.icon.isEmpty() ? R.string.icon_auto : in.icon.equals("file") ? R.string.icon_custom : R.string.icon_symbol;
        return getString(R.string.icon_value, getString(v));
    }

    /** The site's own icon, a symbol, the user's own image, or a request for a better one. */
    private void editIcon(Store.Instance in, boolean isNew, Button label) {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.icon_use_auto));
        actions.add(() -> setIcon(in, isNew, label, ""));
        items.add(getString(R.string.icon_pick_symbol));
        actions.add(() -> pickSymbol(in, isNew, label));
        items.add(getString(R.string.icon_from_web));
        actions.add(() -> iconFromWeb(in, isNew, label));
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        if (pick.resolveActivity(getPackageManager()) != null) {
            // Most TVs have no file picker; tablets do.
            items.add(getString(R.string.icon_from_device));
            actions.add(() -> {
                iconFor = in;
                iconIsNew = isNew;
                iconButton = label;
                startActivityForResult(pick, PICK_ICON);
            });
        }
        items.add(getString(R.string.icon_request_item));
        actions.add(() -> Requests.showIcon(this, in.name, in.url));
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.icon)
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void setIcon(Store.Instance in, boolean isNew, Button label, String icon) {
        in.icon = icon;
        if (!isNew) Store.save(this, instances);
        label.setText(iconLabel(in));
    }

    /** The built-in symbols as tiles in the app's category colours. */
    private void pickSymbol(Store.Instance in, boolean isNew, Button label) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(20), dp(12), dp(20), 0);
        AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout row = null;
        for (int i = 0; i < Icons.SYMBOLS.length; i++) {
            if (i % 7 == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            ImageView v = new ImageView(this);
            v.setImageBitmap(Icons.symbolFor(this, in, Icons.SYMBOLS[i]));
            v.setBackgroundResource(R.drawable.icon_focus);
            v.setPadding(dp(6), dp(6), dp(6), dp(6));
            v.setFocusable(true);
            v.setClickable(true);
            v.setContentDescription(Icons.SYMBOL_NAMES[i]);
            String name = Icons.SYMBOL_NAMES[i];
            v.setOnClickListener(x -> {
                setIcon(in, isNew, label, "symbol:" + name);
                dialog[0].dismiss();
            });
            row.addView(v, new LinearLayout.LayoutParams(dp(64), dp(64)));
        }
        dialog[0] = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.icon_pick_symbol)
                .setView(grid)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void iconFromWeb(Store.Instance in, boolean isNew, Button label) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText url = field(getString(R.string.icon_web_hint), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(url);
        quiet(new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.icon_from_web)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String u = Store.normalise(url.getText().toString(), 0);
                    if (u.isEmpty()) return;
                    Icons.fetchCustom(this, in, u, ok -> customDone(in, isNew, label, ok));
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void customDone(Store.Instance in, boolean isNew, Button label, boolean ok) {
        if (ok) setIcon(in, isNew, label, "file");
        android.widget.Toast.makeText(this, ok ? R.string.icon_saved : R.string.icon_failed, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ICON || result != RESULT_OK || data == null || data.getData() == null || iconFor == null) return;
        Store.Instance in = iconFor;
        boolean isNew = iconIsNew;
        Button label = iconButton;
        Uri uri = data.getData();
        new Thread(() -> {
            boolean ok;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                for (int n; is != null && (n = is.read(buf)) > 0 && out.size() < 8 * 1024 * 1024; ) out.write(buf, 0, n);
                ok = Icons.saveCustom(this, in, out.toByteArray());
            } catch (Exception e) {
                ok = false;
            }
            boolean done = ok;
            ui.post(() -> customDone(in, isNew, label, done));
        }).start();
    }

    // --- Saved logins ------------------------------------------------------------------------

    private String loginsLabel(Store.Instance in) {
        if (!Logins.supported()) return getString(R.string.logins_unsupported);
        return getString(R.string.saved_logins, Logins.list(this, in.id).size());
    }

    /** The app's saved logins: select one to change or delete it, or add one by hand. */
    private void editLogins(Store.Instance in, Button label) {
        List<Logins.Login> logins = Logins.list(this, in.id);
        String[] items = new String[logins.size() + 1];
        for (int i = 0; i < logins.size(); i++) {
            Logins.Login l = logins.get(i);
            items[i] = l.host + (l.user.isEmpty() ? "" : "   ·   " + l.user);
        }
        items[logins.size()] = getString(R.string.add_login);
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.logins)
                .setItems(items, (d, which) -> loginForm(in, which < logins.size() ? logins.get(which) : null, label))
                .show();
    }

    private void loginForm(Store.Instance in, Logins.Login old, Button label) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        String host = old != null ? old.host : in.hasUrl() ? Uri.parse(in.url).getHost() : "";
        EditText site = field(getString(R.string.login_site), host == null ? "" : host, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field(getString(R.string.login_user), old == null ? "" : old.user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = field(getString(R.string.login_pass), old == null ? "" : old.pass, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox auto = new CheckBox(this);
        auto.setText(R.string.login_auto);
        auto.setTextColor(color(R.color.text));
        auto.setChecked(old == null || old.auto);
        form.addView(text(getString(R.string.login_note), 13, R.color.dim));
        form.addView(site);
        form.addView(user);
        form.addView(pass);
        form.addView(auto);
        Appearance.font(form);
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(old == null ? R.string.add_login : R.string.edit_login)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    // Typed as an address or a bare name: only the site's name is kept.
                    String typed = site.getText().toString().trim();
                    String h = Uri.parse(typed.contains("://") ? typed : "https://" + typed).getHost();
                    if (h == null || h.isEmpty() || pass.getText().length() == 0) return;
                    Logins.Login l = new Logins.Login();
                    l.host = h.toLowerCase(java.util.Locale.US);
                    l.user = user.getText().toString().trim();
                    l.pass = pass.getText().toString();
                    l.auto = auto.isChecked();
                    if (old != null && !old.host.equals(l.host)) Logins.delete(this, in.id, old.host);
                    if (!Logins.save(this, in.id, l)) {
                        android.widget.Toast.makeText(this, R.string.login_failed, android.widget.Toast.LENGTH_LONG).show();
                    }
                    label.setText(loginsLabel(in));
                })
                .setNegativeButton(R.string.cancel, null);
        if (old != null) {
            b.setNeutralButton(R.string.delete, (d, w) -> {
                Logins.delete(this, in.id, old.host);
                label.setText(loginsLabel(in));
            });
        }
        quiet(b.show());
    }

    /** A long form scrolls instead of squashing its last rows on a TV screen. */
    private ScrollView scrolling(View form) {
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        return sv;
    }

    /** On a TV the keyboard covers the form; it opens when a text field is selected instead. */
    private static void quiet(AlertDialog d) {
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    // --- Small view helpers -----------------------------------------------------------------

    private void pickTheme() {
        int n = Appearance.THEMES.length;
        String[] names = new String[n + 1];
        String[] ids = new String[n + 1];
        for (int i = 0; i < n; i++) {
            names[i] = Appearance.THEMES[i].name;
            ids[i] = Appearance.THEMES[i].id;
        }
        names[n] = Appearance.label(this, Appearance.AUTO);
        ids[n] = Appearance.AUTO;
        int current = java.util.Arrays.asList(ids).indexOf(Appearance.mode(this));
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.pick_theme)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    d.dismiss();
                    Appearance.setMode(this, ids[which]);
                    recreate();
                })
                .show();
    }

    private TextView text(String s, int sp, int colorRes) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(colorRes));
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        b.setTextColor(color(R.color.text));
        b.setBackgroundResource(R.drawable.tile_bg);
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText field(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(inputType);
        return e;
    }

    @SuppressWarnings("deprecation") // the themed getColor needs API 23; minSdk is 22
    private int color(int res) {
        return getResources().getColor(res);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

}
