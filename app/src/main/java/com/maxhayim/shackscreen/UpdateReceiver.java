package com.maxhayim.shackscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Hears back from Android's installer after Updater commits an update. */
public class UpdateReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation") // the typed getParcelableExtra needs API 33
    public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null && Updater.visible > 0) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                    Updater.setStatus(c, "Waiting for you to confirm the install");
                } else {
                    // Android won't show the prompt over another app, so wait for the next visit.
                    Updater.setStatus(c, "Update downloaded: open Settings to install");
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Updater.setStatus(c, "Updated");
                break;
            default:
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Updater.setStatus(c, "Update failed" + (msg == null ? "" : ": " + msg));
        }
    }
}
