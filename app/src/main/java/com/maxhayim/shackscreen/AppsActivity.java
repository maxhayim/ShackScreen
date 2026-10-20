package com.maxhayim.shackscreen;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All apps, like a TV's channel guide: categories with their counts down the left, the apps'
 * icons on the right. Press and hold one to put it on the home row or take it off, edit it
 * or delete it.
 */
public class AppsActivity extends BaseActivity {

    private static final String ALL = "";
    private static final int SIDE = 236, PAD_LEFT = 28, PAD_RIGHT = 20;

    private LinearLayout side, grid;
    private TextView heading, count;
    private ScrollView gridScroll;
    private List<Store.Instance> instances;
    private String category = ALL;
    private String focusId;
    private final List<View> sideItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setClipChildren(false);

        ScrollView sideScroll = new ScrollView(this);
        sideScroll.setBackgroundColor(Icons.color(this, R.color.card));
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(16), dp(24), dp(16), dp(24));
        sideScroll.addView(side);
        root.addView(sideScroll, new LinearLayout.LayoutParams(dp(SIDE), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(PAD_LEFT), dp(24), dp(PAD_RIGHT), 0);
        main.setClipChildren(false);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        heading = text("", 28, R.color.text);
        heading.setTypeface(heading.getTypeface(), Typeface.BOLD);
        head.addView(heading);
        count = text("", 15, R.color.dim);
        count.setPadding(dp(14), 0, 0, dp(4));
        head.addView(count);
        main.addView(head);
        TextView hint = text(getString(R.string.all_apps_hint), 12, R.color.faint);
        hint.setPadding(0, dp(2), 0, dp(6));
        main.addView(hint);
        gridScroll = new ScrollView(this);
        gridScroll.setClipChildren(false);
        gridScroll.setClipToPadding(false);
        gridScroll.setPadding(0, dp(10), 0, dp(24));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        gridScroll.addView(grid);
        main.addView(gridScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(main, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        instances = Store.load(this);
        buildSide();
        buildGrid();
    }

    // --- Categories ---------------------------------------------------------------------------

    private void buildSide() {
        side.removeAllViews();
        sideItems.clear();
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(6), 0, 0, dp(18));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView name = text(getString(R.string.applications), 21, R.color.text);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setPadding(dp(12), 0, 0, 0);
        brand.addView(name);
        side.addView(brand);

        boolean known = category.equals(ALL);
        side.addView(sideItem(ALL, getString(R.string.all), R.drawable.ic_grid, instances.size()));
        for (String cat : Catalog.categories(this)) {
            int n = inCategory(cat).size();
            if (n == 0) continue;
            known |= cat.equals(category);
            side.addView(sideItem(cat, cat, Icons.categorySymbol(cat), n));
        }
        if (!known) category = ALL; // its last app was deleted
        styleSide();
        Appearance.font(side);
    }

    private View sideItem(String cat, String label, int symbol, int n) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(cat);
        ImageView icon = new ImageView(this);
        icon.setImageResource(symbol);
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView name = text(label, 16, R.color.text);
        name.setPadding(dp(14), 0, dp(8), 0);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(String.valueOf(n), 14, R.color.dim));
        // Moving through the list with the remote shows each category, as a TV guide does.
        row.setOnFocusChangeListener((v, f) -> {
            if (f) select(cat);
        });
        row.setOnClickListener(v -> select(cat));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        sideItems.add(row);
        return row;
    }

    private void select(String cat) {
        if (cat.equals(category)) return;
        category = cat;
        styleSide();
        buildGrid();
    }

    /** The chosen category is a solid pill; the focused one also gets the usual amber outline. */
    private void styleSide() {
        int text = Icons.color(this, R.color.text), dim = Icons.color(this, R.color.dim), ink = Icons.color(this, R.color.bg_bottom);
        for (View v : sideItems) {
            boolean on = category.equals(v.getTag());
            StateListDrawable bg = new StateListDrawable();
            GradientDrawable focused = pill(on ? text : Icons.color(this, R.color.tile_focus));
            focused.setStroke(dp(2), Icons.color(this, R.color.accent));
            bg.addState(new int[]{android.R.attr.state_focused}, focused);
            bg.addState(new int[]{}, pill(on ? text : 0));
            v.setBackground(bg);
            LinearLayout row = (LinearLayout) v;
            ((ImageView) row.getChildAt(0)).setColorFilter(on ? ink : dim);
            TextView name = (TextView) row.getChildAt(1);
            name.setTextColor(on ? ink : text);
            name.setTypeface(Typeface.create(name.getTypeface(), on ? Typeface.BOLD : Typeface.NORMAL));
            ((TextView) row.getChildAt(2)).setTextColor(on ? ink : dim);
        }
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    // --- Cards --------------------------------------------------------------------------------

    private List<Store.Instance> inCategory(String cat) {
        List<Store.Instance> out = new ArrayList<>();
        for (Store.Instance in : instances) {
            if (cat.equals(ALL) || Catalog.get(this, in.type).category.equals(cat)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private void buildGrid() {
        grid.removeAllViews();
        List<Store.Instance> apps = inCategory(category);
        heading.setText(category.equals(ALL) ? getString(R.string.all) : category);
        count.setText(getResources().getQuantityString(R.plurals.application_count, apps.size(), apps.size()));

        int width = getResources().getDisplayMetrics().widthPixels - dp(SIDE + PAD_LEFT + PAD_RIGHT);
        int cols = Math.max(2, width / dp(104)), gap = dp(12);
        int cardW = (width - gap * (cols - 1)) / cols;
        int logoH = cardW - dp(28); // square, with room either side for the focus outline

        List<View> cards = new ArrayList<>();
        View focus = null;
        for (Store.Instance in : apps) {
            View card = card(in.name, logoH);
            Icons.show(this, in, card.findViewById(R.id.icon));
            card.setOnClickListener(v -> open(in));
            card.setOnLongClickListener(v -> {
                menu(in);
                return true;
            });
            cards.add(card);
            if (in.id.equals(focusId)) focus = card;
        }
        if (category.equals(ALL)) {
            View add = card(getString(R.string.add_application), logoH);
            ((ImageView) add.findViewById(R.id.icon)).setImageBitmap(
                    Icons.symbolTile(this, R.drawable.ic_plus, 0xFF6B7280, 0xFF374151));
            add.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_ADD, true)));
            cards.add(add);
            View ask = card(getString(R.string.request_short), logoH);
            ((ImageView) ask.findViewById(R.id.icon)).setImageBitmap(
                    Icons.symbolTile(this, R.drawable.ic_chat, 0xFFF472B6, 0xFFBE185D));
            ask.setOnClickListener(v -> Requests.show(this, "", ""));
            cards.add(ask);
        }

        LinearLayout row = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                grid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i % cols != 0) lp.leftMargin = gap;
            lp.bottomMargin = dp(10);
            row.addView(cards.get(i), lp);
        }
        Appearance.font(grid);
        gridScroll.scrollTo(0, 0);
        if (focus != null) {
            focus.requestFocus();
            focusId = null;
        } else if (getCurrentFocus() == null) {
            for (View v : sideItems) if (category.equals(v.getTag())) v.requestFocus();
        }
    }

    /** An app's square icon with its name under it. */
    private View card(String label, int logoH) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(4), dp(4), dp(4), dp(6));
        c.setFocusable(true);
        c.setClickable(true);
        c.setBackgroundResource(R.drawable.icon_focus);
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable shape = pill(0);
        shape.setCornerRadius(dp(18));
        frame.setBackground(shape);
        frame.setClipToOutline(true); // rounded corners on the icon
        ImageView logo = new ImageView(this);
        logo.setId(R.id.icon);
        logo.setScaleType(ImageView.ScaleType.FIT_XY);
        frame.addView(logo, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        c.addView(frame, new LinearLayout.LayoutParams(logoH, logoH));
        TextView name = text(label, 13, R.color.dim);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        c.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        c.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(140);
            name.setTextColor(Icons.color(this, f ? R.color.text : R.color.dim));
        });
        return c;
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
                        buildGrid();
                    } else if (which == 1) {
                        startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_EDIT, in.id));
                    } else {
                        instances.remove(in);
                        Store.save(this, instances);
                        focusId = null;
                        buildSide();
                        buildGrid();
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
