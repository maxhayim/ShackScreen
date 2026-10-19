package com.maxhayim.shackscreen;

import android.annotation.SuppressLint;
import android.app.Activity;
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
 * that fails to load is retried until the server is back.
 */
public class ViewerActivity extends Activity {

    static final String EXTRA_ID = "id";
    private static final long RETRY_MS = 30_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView error;
    private Store.Instance in;

    private final Runnable retry = () -> {
        error.setVisibility(View.GONE);
        web.reload();
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        in = Store.find(this, getIntent().getStringExtra(EXTRA_ID));
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
        setContentView(root);
        web.requestFocus();
        web.loadUrl(in.url);
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
        // Menu or the remote's refresh key reloads the page.
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_REFRESH) {
            retry.run();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
