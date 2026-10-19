package com.maxhayim.shackscreen;

import android.app.AlertDialog;
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

import java.util.List;

/** Add, edit and remove apps. Each one points at a public site or the user's own server. */
public class SettingsActivity extends BaseActivity {

    static final String EXTRA_ADD = "add", EXTRA_EDIT = "edit";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list;
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

        root.addView(section(getString(R.string.appearance)));
        Button theme = button(themeLabel());
        theme.setOnClickListener(v -> {
            Appearance.setMode(this, Appearance.next(Appearance.mode(this)));
            recreate();
        });
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

    private void pickType() {
        List<Catalog.Type> types = Catalog.all(this);
        String[] names = new String[types.size()];
        for (int i = 0; i < names.length; i++) names[i] = types.get(i).name;
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(R.string.pick_app)
                .setItems(names, (d, which) -> edit(Store.create(types.get(which)), true))
                .show();
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
        form.addView(url);
        if (type.token) form.addView(token);
        if (type.port > 0) {
            form.addView(text(type.name + " default port: " + type.port + " (added if you leave it out)", 13, R.color.dim));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(type.name)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    in.name = name.getText().toString().trim();
                    if (in.name.isEmpty()) in.name = type.name;
                    in.url = Store.normalise(url.getText().toString(), type.port);
                    in.token = token.getText().toString().trim();
                    if (isNew) instances.add(in);
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
        b.show();
    }

    // --- Small view helpers -----------------------------------------------------------------

    private String themeLabel() {
        String m = Appearance.mode(this);
        return getString(Appearance.LIGHT.equals(m) ? R.string.theme_light
                : Appearance.AUTO.equals(m) ? R.string.theme_auto : R.string.theme_dark);
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
