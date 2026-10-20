package com.maxhayim.shackscreen;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/** Applies the chosen theme and tells the updater when ShackScreen is on screen. */
public abstract class BaseActivity extends Activity {

    private String appliedTheme;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        appliedTheme = Appearance.mode(base);
        int night = Appearance.night(base);
        if (night != 0) {
            // Overriding uiMode picks values/ (light) or values-night/ (dark) for this activity.
            Configuration o = new Configuration();
            o.uiMode = (base.getResources().getConfiguration().uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
            applyOverrideConfiguration(o);
        }
    }

    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(Appearance.background(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Updater.visible++;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The theme was changed in Settings while this screen was in the background.
        if (!Appearance.mode(this).equals(appliedTheme)) recreate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Updater.visible--;
    }
}
