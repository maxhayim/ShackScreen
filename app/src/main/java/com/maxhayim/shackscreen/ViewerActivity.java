package com.maxhayim.shackscreen;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
 * Logins typed into a page can be saved (encrypted, see Logins) and are filled in, and optionally
 * submitted, the next time that site asks.
 */
public class ViewerActivity extends BaseActivity {

    static final String EXTRA_ID = "id", EXTRA_URL = "url";
    static final int[] ZOOMS = {50, 60, 67, 75, 80, 90, 100, 110, 125, 150};
    private static final long RETRY_MS = 30_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView error;
    private Store.Instance in;
    private final java.util.List<String[]> pages = new java.util.ArrayList<>(); // {name, url}

    /** When a saved login was last submitted for a page, so a wrong password isn't sent over and over. */
    private long autoSubmitted;
    private String offered; // the login last offered for saving, so one sign-in asks once

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
        // Logins are kept encrypted by ShackScreen; WebView's own form memory would keep them in plain text.
        s.setSaveFormData(false);
        if (!in.id.isEmpty() && Logins.supported()) web.addJavascriptInterface(new LoginBridge(), "ShackScreenLogin");

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            @android.annotation.TargetApi(23)
            public void onPageCommitVisible(WebView view, String url) {
                applyZoom(); // early, so the page doesn't jump once it has loaded
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applyZoom();
                logins(url);
            }

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

    /** The app's pages (when it has more than one), its page size, and Reload. */
    private void showPages() {
        int n = pages.size() > 1 ? pages.size() : 0;
        String[] items = new String[n + 2];
        for (int i = 0; i < n; i++) items[i] = pages.get(i)[0];
        items[n] = getString(R.string.zoom_value, zoom(this, in));
        items[n + 1] = getString(R.string.reload);
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(in.name)
                .setItems(items, (d, which) -> {
                    error.setVisibility(View.GONE);
                    if (which == n) {
                        pickZoom(this, in, z -> {
                            in.zoom = z;
                            if (!in.id.isEmpty()) {
                                java.util.List<Store.Instance> all = Store.load(this);
                                for (Store.Instance x : all) if (x.id.equals(in.id)) x.zoom = z;
                                Store.save(this, all);
                            }
                            web.reload();
                        });
                    } else if (which == n + 1) {
                        web.reload();
                    } else {
                        web.loadUrl(pages.get(which)[1]);
                    }
                })
                .show();
    }

    // --- Page size --------------------------------------------------------------------------

    interface ZoomPicked { void picked(int zoom); }

    /** The app's page size in percent: the user's choice, or the catalog's for that app. */
    static int zoom(android.content.Context c, Store.Instance in) {
        return in.zoom > 0 ? in.zoom : Catalog.get(c, in.type).zoom;
    }

    static void pickZoom(android.app.Activity a, Store.Instance in, ZoomPicked done) {
        int current = zoom(a, in), checked = 0;
        String[] items = new String[ZOOMS.length];
        for (int i = 0; i < ZOOMS.length; i++) {
            items[i] = ZOOMS[i] + "%";
            if (ZOOMS[i] == current) checked = i;
        }
        new android.app.AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(R.string.zoom)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    done.picked(ZOOMS[which]);
                })
                .show();
    }

    /**
     * Sizes the page by widening (or narrowing) its viewport, so a site laid out for a big
     * screen fits more on the TV. Unlike CSS zoom, maps and touch keep working.
     */
    private void applyZoom() {
        int z = zoom(this, in);
        if (z == 100 || web == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.round(dm.widthPixels / dm.density * 100f / z);
        // Every viewport tag: some pages (GMRS Hub's map) have two, and the last one wins.
        web.evaluateJavascript("(function(w){var ms=document.querySelectorAll('meta[name=viewport]'),c='width='+w;"
                + "if(!ms.length){var m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);ms=[m];}"
                + "for(var i=0;i<ms.length;i++)if(ms[i].content!=c)ms[i].content=c;})(" + width + ")", null);
    }

    // --- Saved logins --------------------------------------------------------------------------

    /** Watches the page's login form so a sign-in can be offered for saving. */
    private static final String CAPTURE = "(function(){if(window.__ssLogin)return;window.__ssLogin=1;"
            + "function grab(f){var r=f||document,p=r.querySelector('input[type=password]');if(!p||!p.value)return;"
            + "var u='',ins=r.querySelectorAll('input');for(var i=0;i<ins.length;i++){if(ins[i]===p)break;"
            + "var t=(ins[i].type||'text').toLowerCase();if((t=='text'||t=='email'||t=='tel')&&ins[i].value)u=ins[i].value;}"
            + "ShackScreenLogin.offer(u,p.value);}"
            + "document.addEventListener('submit',function(e){grab(e.target)},true);"
            + "document.addEventListener('click',function(e){var b=e.target.closest&&e.target.closest('button,input[type=submit]');if(b)grab(b.form)},true);"
            + "document.addEventListener('keydown',function(e){if(e.key=='Enter'&&e.target.type=='password')grab(e.target.form)},true);})();";

    /** Fills a login form with (user, pass) and presses its sign-in button when submit is true. */
    private static final String FILL = "(function(u,p,go){var pw=document.querySelector('input[type=password]');if(!pw)return 'none';"
            + "function set(el,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);"
            + "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}"
            + "var f=pw.form,r=f||document,ins=r.querySelectorAll('input'),user=null;"
            + "for(var i=0;i<ins.length;i++){if(ins[i]===pw)break;var t=(ins[i].type||'text').toLowerCase();"
            + "if(t=='text'||t=='email'||t=='tel')user=ins[i];}"
            + "if(user&&u)set(user,u);set(pw,p);"
            + "if(go){var b=r.querySelector('button[type=submit],input[type=submit],button:not([type])');"
            + "if(b)b.click();else if(f)(f.requestSubmit?f.requestSubmit():f.submit());}return 'filled';})";

    private void logins(String url) {
        if (in.id.isEmpty() || !Logins.supported()) return;
        web.evaluateJavascript(CAPTURE, null);
        Logins.Login l = Logins.find(this, in.id, host(url));
        if (l == null) return;
        // Pages that draw their form after loading get a few chances.
        ui.removeCallbacksAndMessages(FILL_TOKEN);
        for (long delay : new long[]{0, 1500, 4000}) {
            ui.postAtTime(() -> fill(l, url), FILL_TOKEN, android.os.SystemClock.uptimeMillis() + delay);
        }
    }

    private void fill(Logins.Login l, String url) {
        if (web == null || !url.equals(web.getUrl())) return;
        // Signing in again within a minute means the saved login didn't work: fill it, but let the user press the button.
        boolean go = l.auto && System.currentTimeMillis() - autoSubmitted > 60_000;
        String call = FILL + "(" + org.json.JSONObject.quote(l.user) + "," + org.json.JSONObject.quote(l.pass) + "," + go + ")";
        web.evaluateJavascript(call, result -> {
            if (!"\"filled\"".equals(result)) return;
            ui.removeCallbacksAndMessages(FILL_TOKEN);
            if (go) autoSubmitted = System.currentTimeMillis();
        });
    }

    private static final Object FILL_TOKEN = new Object();

    private static String host(String url) {
        String h = url == null ? null : Uri.parse(url).getHost();
        return h == null ? "" : h.toLowerCase(java.util.Locale.US);
    }

    /** Called by the page's script when a login form is submitted. Never hands anything back to the page. */
    private class LoginBridge {
        @JavascriptInterface
        public void offer(String user, String pass) {
            ui.post(() -> offerToSave(user, pass));
        }
    }

    private void offerToSave(String user, String pass) {
        if (web == null || pass == null || pass.isEmpty()) return;
        String host = host(web.getUrl());
        if (host.isEmpty() || Logins.isNever(this, in.id, host)) return;
        Logins.Login saved = Logins.find(this, in.id, host);
        if (saved != null && saved.user.equals(user) && saved.pass.equals(pass)) return;
        String key = host + "\n" + user + "\n" + pass;
        if (key.equals(offered)) return;
        offered = key;
        new android.app.AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(getString(saved == null ? R.string.login_save_title : R.string.login_update_title, host))
                .setMessage(user.isEmpty() ? getString(R.string.login_save_body_nouser) : getString(R.string.login_save_body, user))
                .setPositiveButton(R.string.save, (d, w) -> {
                    Logins.Login l = new Logins.Login();
                    l.host = host;
                    l.user = user;
                    l.pass = pass;
                    l.auto = saved == null || saved.auto;
                    if (!Logins.save(this, in.id, l)) {
                        android.widget.Toast.makeText(this, R.string.login_failed, android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton(R.string.login_never, (d, w) -> Logins.setNever(this, in.id, host))
                .setNegativeButton(R.string.login_not_now, null)
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
        // Menu lists the app's pages, its page size and Reload.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showPages();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_REFRESH) {
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
        // Keeps "remember me" sign-ins across restarts.
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
