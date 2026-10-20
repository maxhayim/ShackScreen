package com.maxhayim.shackscreen;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** The daily background check, so a TV that's left on still finds updates and backs up. */
public class UpdateJob extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Updater.run(getApplicationContext(), false);
            Backup.ifDue(getApplicationContext());
            jobFinished(params, false);
        }, "shackscreen-update").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // try again later
    }
}
