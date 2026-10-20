package com.maxhayim.shackscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.nayuki.qrcodegen.QrCode;

/**
 * "Request an app" (or a new icon for one): opens a GitHub issue form for the app to be added to the catalog. On a TV,
 * the QR code lets the user finish it on their phone instead of typing with the remote.
 */
final class Requests {

    static String url(String name, String address) {
        Uri.Builder b = Uri.parse("https://github.com/" + Updater.REPO + "/issues/new").buildUpon()
                .appendQueryParameter("template", "app_request.yml")
                .appendQueryParameter("title", "App request: " + (name == null ? "" : name));
        // Issue forms fill a field from a query parameter named after its id.
        if (name != null && !name.isEmpty()) b.appendQueryParameter("name", name);
        if (address != null && !address.isEmpty()) b.appendQueryParameter("url", address);
        return b.build().toString();
    }

    /** A new or better icon for an app, to be added to the catalog for everyone. */
    static String iconUrl(String name, String address) {
        Uri.Builder b = Uri.parse("https://github.com/" + Updater.REPO + "/issues/new").buildUpon()
                .appendQueryParameter("template", "icon_request.yml")
                .appendQueryParameter("title", "Icon request: " + (name == null ? "" : name));
        if (name != null && !name.isEmpty()) b.appendQueryParameter("name", name);
        if (address != null && !address.isEmpty()) b.appendQueryParameter("url", address);
        return b.build().toString();
    }

    static void show(Activity a, String name, String address) {
        show(a, R.string.request_app, url(name, address));
    }

    static void showIcon(Activity a, String name, String address) {
        show(a, R.string.icon_request, iconUrl(name, address));
    }

    private static void show(Activity a, int title, String link) {
        int dp = Math.round(a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(24 * dp, 16 * dp, 24 * dp, 0);
        ImageView qr = new ImageView(a);
        qr.setImageBitmap(qr(link, 220 * dp));
        box.addView(qr, new LinearLayout.LayoutParams(220 * dp, 220 * dp));
        TextView hint = new TextView(a);
        hint.setText(R.string.request_hint);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 14 * dp, 0, 0);
        box.addView(hint);
        Appearance.font(box);
        new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.open_here, (d, w) ->
                        a.startActivity(new Intent(a, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_URL, link)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /** Black modules on white with a quiet zone, scaled to about px square. */
    private static Bitmap qr(String text, int px) {
        QrCode code = QrCode.encodeText(text, QrCode.Ecc.LOW);
        int border = 2, n = code.size + border * 2, scale = Math.max(1, px / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.RGB_565);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean dark = code.getModule(x - border, y - border);
                int color = dark ? Color.BLACK : Color.WHITE;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) b.setPixel(x * scale + dx, y * scale + dy, color);
                }
            }
        }
        return b;
    }
}
