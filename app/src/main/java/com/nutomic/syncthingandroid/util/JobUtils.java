package com.nutomic.syncthingandroid.util;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import com.nutomic.syncthingandroid.service.SyncTriggerJobService;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

public class JobUtils {

    private static final String TAG = "JobUtils";

    private static final int TOLERATED_INACCURACY_IN_SECONDS = 120;

    public static void scheduleSyncTriggerServiceJob(Context context, int delayInSeconds, boolean startRun) {
        if (delayInSeconds < 0) {
            delayInSeconds = 0;
        }

        ComponentName serviceComponent = new ComponentName(context, SyncTriggerJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);

        // Wait at least "delayInSeconds".
        builder.setMinimumLatency(delayInSeconds * 1000);

        // Maximum tolerated delay.
        builder.setOverrideDeadline((delayInSeconds + TOLERATED_INACCURACY_IN_SECONDS) * 1000);

        // Syncthing should start after the delay if startRun is true, and otherwise stop
        // The PersistableBundle is used to forward this information to the SyncTriggerJobService
        if (startRun) {
            PersistableBundle extraBundle = new PersistableBundle();
            extraBundle.putInt(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, 1); // must be int, because boolean needs API 22
            builder.setExtras(extraBundle);
        }

        // Schedule the start of "SyncTriggerJobService" in "X" seconds.
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
        Log.i(TAG, "Scheduled SyncTriggerJobService to run in " +
                Integer.toString(delayInSeconds) +
                "(+" + Integer.toString(TOLERATED_INACCURACY_IN_SECONDS) + ") seconds.");
    }

    public static void cancelAllScheduledJobs(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }
}
