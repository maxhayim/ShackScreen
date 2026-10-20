package com.maxhayim.shackscreen;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * One app, full-screen, meant to stay up on a wall for hours: the screen stays on, and a page
 * that fails to load is retried until the server is back. An app with several pages (GMRS Hub's
 * map and hub status, or pages the user added) switches between them from Menu or the Pages button.
 */
public class ViewerActivity extends BaseActivity {

    static final String EXTRA_ID = "id", EXTRA_URL = "url";
    private static final long RETRY_MS = 30_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView error;
    private Store.Instance in;
    private final java.util.List<String[]> pages = new java.util.ArrayList<>(); // {name, url}

    private final Runnable retry = () -> {
        error.setVisibility(View.GONE);
        web.reload();
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String direct = getIntent().getStringExtra(EXTRA_URL);
        if (direct != null) {
            // A one-off page, such as the GitHub request form.
            in = new Store.Instance();
            in.id = "";
            in.type = "custom";
            in.name = Uri.parse(direct).getHost();
            in.url = direct;
        } else {
            in = Store.find(this, getIntent().getStringExtra(EXTRA_ID));
        }
        if (in == null || !in.hasUrl()) {
            finish();
            return;
        }
        Catalog.Type type = Catalog.get(this, in.type);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // WebSDR and Broadcastify play sound, and there is no touch to unlock audio on a TV.
        s.setMediaPlaybackRequiresUserGesture(!type.audio);
        // These dashboards are laid out for a desktop, which suits a TV better than the phone layout.
        s.setUserAgentString(s.getUserAgentString()
                .replaceAll("\\(Linux; Android[^)]*\\)", "(X11; Linux x86_64)")
                .replace("; wv", "")
                .replace(" Mobile", "") + " ShackScreen/" + BuildConfig.VERSION_NAME);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            @android.annotation.TargetApi(23)
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showError();
            }

            @Override
            @SuppressWarnings("deprecation") // the request-based version above needs API 23
            public void onReceivedError(WebView view, int code, String desc, String url) {
                if (url != null && url.equals(view.getUrl())) showError();
            }
        });

        error = new TextView(this);
        error.setTextSize(22);
        error.setTextColor(0xFFE6EDF3);
        error.setBackgroundColor(0xEE0B0F14);
        error.setGravity(android.view.Gravity.CENTER);
        error.setVisibility(View.GONE);

        FrameLayout root = new FrameLayout(this);
        root.addView(web);
        root.addView(error);

        // The app's own address first, then its built-in pages, then the user's.
        pages.add(new String[]{in.name, in.url});
        for (String[] p : type.pages) if (!p[1].equals(in.url)) pages.add(p);
        if (type.pages.size() > 0 && type.pages.get(0)[1].equals(in.url)) pages.set(0, type.pages.get(0));
        pages.addAll(in.pages);
        if (pages.size() > 1) root.addView(pagesButton());

        setContentView(root);
        web.requestFocus();
        web.loadUrl(in.url);
    }

    /** A small Pages button in the corner for touch screens; on a remote, Menu does the same. */
    private View pagesButton() {
        TextView b = new TextView(this);
        b.setText(R.string.pages);
        b.setTextSize(15);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundResource(R.drawable.pages_button);
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        b.setPadding(pad, pad / 2, pad, pad / 2);
        b.setOnClickListener(v -> showPages());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.END);
        lp.setMargins(0, pad, pad, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void showPages() {
        String[] items = new String[pages.size() + 1];
        for (int i = 0; i < pages.size(); i++) items[i] = pages.get(i)[0];
        items[pages.size()] = getString(R.string.reload);
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(in.name)
                .setItems(items, (d, which) -> {
                    error.setVisibility(View.GONE);
                    if (which == pages.size()) web.reload();
                    else web.loadUrl(pages.get(which)[1]);
                })
                .show();
    }

    private void showError() {
        error.setText(getString(R.string.viewer_error, in.name));
        error.setVisibility(View.VISIBLE);
        ui.removeCallbacks(retry);
        ui.postDelayed(retry, RETRY_MS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && error.getVisibility() != View.VISIBLE && web.canGoBack()) {
            web.goBack();
            return true;
        }
        // Menu lists the app's pages (with Reload) when it has several, and reloads otherwise.
        if (keyCode == KeyEvent.KEYCODE_MENU && pages.size() > 1) {
            showPages();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_REFRESH) {
            retry.run();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        // Keep audio (WebSDR, Broadcastify) only while the app is on screen.
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
