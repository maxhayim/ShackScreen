package com.maxhayim.shackscreen;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private Button modeButton, installButton;
    private TextView updateStatus;

    // The updater works on its own thread and reports through its saved status; show it as it changes.
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            showUpdates();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(27), dp(48), dp(27));
        scroll.addView(root);

        root.addView(text(getString(R.string.settings_title), 26, R.color.accent));
        root.addView(text(getString(R.string.settings_hint), 15, R.color.dim));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(16), 0, dp(16));
        root.addView(list);
        Button add = button(getString(R.string.add_app));
        add.setOnClickListener(v -> pickType());
        root.addView(add);

        root.addView(section(getString(R.string.widgets)));
        TextView hint = text(getString(R.string.widgets_hint), 13, R.color.dim);
        hint.setPadding(0, 0, 0, dp(10));
        root.addView(hint);
        widgetList = new LinearLayout(this);
        widgetList.setOrientation(LinearLayout.VERTICAL);
        root.addView(widgetList);
        Button rotate = button("");
        rotate.setText(Widgets.rotate(this) ? R.string.rotate_on : R.string.rotate_off);
        rotate.setOnClickListener(v -> {
            Widgets.setRotate(this, !Widgets.rotate(this));
            rotate.setText(Widgets.rotate(this) ? R.string.rotate_on : R.string.rotate_off);
        });
        root.addView(rotate);

        root.addView(section(getString(R.string.location)));
        Button location = button(locationLabel());
        location.setOnClickListener(v -> editLocation(location));
        root.addView(location);

        root.addView(section(getString(R.string.appearance)));
        Button theme = button(themeLabel());
        theme.setOnClickListener(v -> pickTheme());
        root.addView(theme);

        TextView updates = section(getString(R.string.updates));
        root.addView(updates);
        modeButton = button("");
        modeButton.setOnClickListener(v -> {
            String m = Updater.mode(this);
            Updater.setMode(this, Updater.OFF.equals(m) ? Updater.NOTIFY : Updater.NOTIFY.equals(m) ? Updater.AUTO : Updater.OFF);
            showUpdates();
        });
        root.addView(modeButton);
        Button check = button(getString(R.string.check_now));
        check.setOnClickListener(v -> {
            Updater.setStatus(this, getString(R.string.checking));
            new Thread(() -> Updater.run(getApplicationContext(), true)).start();
        });
        root.addView(check);
        installButton = button("");
        installButton.setOnClickListener(v -> {
            Updater.Release r = Updater.available(this);
            if (r != null) new Thread(() -> Updater.install(getApplicationContext(), r)).start();
        });
        root.addView(installButton);
        updateStatus = text("", 15, R.color.text);
        root.addView(updateStatus);
        root.addView(text(getString(R.string.version, BuildConfig.VERSION_NAME), 13, R.color.dim));
        setContentView(scroll);
        Appearance.font(scroll);

        instances = Store.load(this);
        rebuild();
        rebuildWidgets();
        if (getIntent().getBooleanExtra(EXTRA_ADD, false)) pickType();
        String editId = getIntent().getStringExtra(EXTRA_EDIT);
        if (editId != null) {
            for (Store.Instance in : instances) if (in.id.equals(editId)) edit(in, false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ui.post(poll);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(poll);
    }

    private void showUpdates() {
        String m = Updater.mode(this);
        modeButton.setText(getString(Updater.AUTO.equals(m) ? R.string.mode_auto
                : Updater.NOTIFY.equals(m) ? R.string.mode_notify : R.string.mode_off));
        Updater.Release r = Updater.available(this);
        installButton.setVisibility(r == null ? View.GONE : View.VISIBLE);
        if (r != null) installButton.setText(getString(R.string.install_version, r.version));
        updateStatus.setText(Updater.status(this));
    }

    private void rebuild() {
        list.removeAllViews();
        for (Store.Instance in : instances) {
            String where = in.hasUrl() ? in.url : getString(R.string.needs_address);
            Button b = button(in.name + "   ·   " + where);
            b.setOnClickListener(v -> edit(in, false));
            list.addView(b);
        }
        Appearance.font(list);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
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
        widgetList.removeAllViews();
        List<String> shown = Widgets.shown(this);
        List<Widgets.Def> ordered = new ArrayList<>();
        for (String id : shown) ordered.add(Widgets.get(id));
        for (Widgets.Def d : Widgets.ALL) if (!shown.contains(d.id)) ordered.add(d);
        for (Widgets.Def d : ordered) {
            boolean on = shown.contains(d.id);
            Button b = button((on ? "✓   " : "+   ") + getString(d.title) + "   ·   " + d.category);
            if (!on) b.setTextColor(color(R.color.dim));
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

    private String locationLabel() {
        String loc = Widgets.location(this);
        return loc.isEmpty() ? getString(R.string.location_unset) : getString(R.string.location_value, loc);
    }

    private void editLocation(Button label) {
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
                    label.setText(locationLabel());
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
                .setView(form)
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

    /** On a TV the keyboard covers the form; it opens when a text field is selected instead. */
    private static void quiet(AlertDialog d) {
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    // --- Small view helpers -----------------------------------------------------------------

    private String themeLabel() {
        return getString(R.string.theme_value, Appearance.label(this, Appearance.mode(this)));
    }

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

    private TextView section(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.US), 12, R.color.accent);
        t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(24), 0, dp(10));
        return t;
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
