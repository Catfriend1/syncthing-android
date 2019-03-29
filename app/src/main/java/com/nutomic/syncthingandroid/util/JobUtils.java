package com.nutomic.syncthingandroid.util;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.nutomic.syncthingandroid.service.SyncTriggerJobService;

public class JobUtils {

    private static final String TAG = "JobUtils";

    private static final int TOLERATED_INACCURACY_IN_SECONDS = 120;

    @TargetApi(21)
    public static void scheduleSyncTriggerServiceJob(Context context, int delayInSeconds) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        ComponentName serviceComponent = new ComponentName(context, SyncTriggerJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);

        // Wait at least "delayInSeconds".
        builder.setMinimumLatency(delayInSeconds * 1000);

        // Maximum tolerated delay.
        builder.setOverrideDeadline((delayInSeconds + TOLERATED_INACCURACY_IN_SECONDS) * 1000);

        // Schedule the start of "SyncTriggerJobService" in "X" seconds.
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
        Log.i(TAG, "Scheduled SyncTriggerJobService to run in " +
                Integer.toString(delayInSeconds) +
                "(+" + Integer.toString(TOLERATED_INACCURACY_IN_SECONDS) + ") seconds.");
    }

    @TargetApi(21)
    public static void cancelAllScheduledJobs(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }
}
