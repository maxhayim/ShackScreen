package com.maxhayim.shackscreen;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher mode, off by default: ShackScreen can be the device's home screen, and then lists the
 * installed Android apps and Android's settings next to its own applications. An Android app on
 * the home row is a Store.Instance of type "android" whose url is "android-app:" + its package.
 */
final class Launcher {

    static final String TYPE = "android", CATEGORY = "Android apps", SCHEME = "android-app:";
    private static final String ALIAS = ".HomeAlias";

    static final class App {
        final String label, pkg;

        App(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
        }
    }

    private Launcher() {}

    private static ComponentName alias(Context c) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + ALIAS);
    }

    /** On when the home-screen entry is switched on; it is off until the user turns it on. */
    static boolean enabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(alias(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void setEnabled(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * Lets the user pick ShackScreen (or go back to the TV's own) as the home screen: Android's
     * Default home app setting where there is one, otherwise the chooser that Home opens.
     */
    static void chooseHome(Activity a) {
        Intent settings = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (settings.resolveActivity(a.getPackageManager()) != null) {
            a.startActivity(settings);
            return;
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            a.startActivity(Intent.createChooser(home, a.getString(R.string.launcher_choose)));
        } catch (RuntimeException e) {
            Toast.makeText(a, R.string.launcher_no_choice, Toast.LENGTH_LONG).show();
        }
    }

    /** True when ShackScreen is the home screen Android opens now. */
    static boolean isHome(Context c) {
        ResolveInfo r = c.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
    }

    /** Installed apps that can be opened, TV and phone ones, by name; ShackScreen itself left out. */
    static List<App> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || byPkg.containsKey(pkg)) continue;
                byPkg.put(pkg, new App(String.valueOf(r.loadLabel(pm)), pkg));
            }
        }
        List<App> out = new ArrayList<>(byPkg.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static boolean is(Store.Instance in) {
        return TYPE.equals(in.type);
    }

    static String pkg(Store.Instance in) {
        return in.url.startsWith(SCHEME) ? in.url.substring(SCHEME.length()) : in.url;
    }

    static Store.Instance instance(Catalog.Type t, App app) {
        Store.Instance in = Store.create(t);
        in.name = app.label;
        in.url = SCHEME + app.pkg;
        in.home = true;
        return in;
    }

    /** Opens an Android app: its TV entry first, then its phone/tablet one. */
    static void launch(Activity a, String pkg) {
        PackageManager pm = a.getPackageManager();
        Intent i = null;
        if (android.os.Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(a, R.string.launcher_missing, Toast.LENGTH_LONG).show();
            return;
        }
        a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** An app's own icon, filling a square like the other icons; null if it isn't installed. */
    static Bitmap icon(Context c, String pkg, int size) {
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(Color.WHITE); // behind icons with transparent corners
            d.setBounds(0, 0, size, size);
            d.draw(cv);
            return b;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
