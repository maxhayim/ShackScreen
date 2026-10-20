package com.maxhayim.shackscreen;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All apps, by category, like a phone's app library. Press and hold one to put it on the home row
 * or take it off, edit it or delete it.
 */
public class AppsActivity extends BaseActivity {

    private LinearLayout root;
    private List<Store.Instance> instances;
    private String focusId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(27), dp(48), dp(27));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        instances = Store.load(this);
        build();
    }

    private void build() {
        root.removeAllViews();
        TextView title = text(getString(R.string.all_apps), 26, R.color.accent);
        root.addView(title);
        TextView hint = text(getString(R.string.all_apps_hint), 14, R.color.dim);
        hint.setPadding(0, dp(2), 0, dp(8));
        root.addView(hint);

        int cols = Math.max(3, (getResources().getDisplayMetrics().widthPixels - dp(96)) / dp(116));
        View focus = null;
        for (String cat : Catalog.categories(this)) {
            List<View> tiles = new ArrayList<>();
            for (Store.Instance in : instances) {
                if (!Catalog.get(this, in.type).category.equals(cat)) continue;
                View t = Icons.tile(this, root, in);
                t.findViewById(R.id.dot).setVisibility(View.GONE); // status is checked on the home row only
                t.setOnClickListener(v -> open(in));
                t.setOnLongClickListener(v -> {
                    menu(in);
                    return true;
                });
                tiles.add(t);
                if (in.id.equals(focusId)) focus = t;
            }
            if (tiles.isEmpty()) continue;
            section(cat, tiles, cols);
        }
        List<View> more = new ArrayList<>();
        View add = Icons.button(this, root, getString(R.string.add_app).replace("+ ", ""), R.drawable.ic_plus, 0xFF6B7280, 0xFF374151);
        add.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_ADD, true)));
        more.add(add);
        View ask = Icons.button(this, root, getString(R.string.request_app), R.drawable.ic_chat, 0xFFF472B6, 0xFFBE185D);
        ask.setOnClickListener(v -> Requests.show(this, "", ""));
        more.add(ask);
        section(" ", more, cols);
        Appearance.font(root);
        if (focus == null && root.getChildCount() > 3) focus = firstTile();
        if (focus != null) focus.requestFocus();
    }

    private View firstTile() {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0) {
                View row = ((LinearLayout) v).getChildAt(0);
                if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 0) return ((LinearLayout) row).getChildAt(0);
            }
        }
        return null;
    }

    /** A category label and its icons, wrapped into rows of cols. */
    private void section(String name, List<View> tiles, int cols) {
        TextView label = text(name.toUpperCase(Locale.US), 12, R.color.dim);
        label.setLetterSpacing(0.15f);
        label.setPadding(dp(4), dp(18), 0, dp(4));
        root.addView(label);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                grid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(108), LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            row.addView(tiles.get(i), lp);
        }
        root.addView(grid);
    }

    private void menu(Store.Instance in) {
        String[] items = {
                getString(in.home ? R.string.remove_home : R.string.add_home),
                getString(R.string.edit),
                getString(R.string.delete)};
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(in.name)
                .setItems(items, (d, which) -> {
                    focusId = in.id;
                    if (which == 0) {
                        in.home = !in.home;
                        Store.save(this, instances);
                        build();
                    } else if (which == 1) {
                        startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
                    } else {
                        instances.remove(in);
                        Store.save(this, instances);
                        focusId = null;
                        build();
                    }
                })
                .show();
    }

    private void open(Store.Instance in) {
        if (!in.hasUrl()) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
            return;
        }
        startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_ID, in.id));
    }

    private TextView text(String s, int sp, int colorRes) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Icons.color(this, colorRes));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
