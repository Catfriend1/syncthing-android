package com.nutomic.syncthingandroid.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
// import android.util.Log;

import com.nutomic.syncthingandroid.util.JobUtils;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

/**
 * SyncTriggerJobService to be scheduled by the JobScheduler.
 * See {@link JobUtils#scheduleSyncTriggerServiceJob} for more details.
 */
public class SyncTriggerJobService extends JobService {
    private static final String TAG = "SyncTriggerJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        // Log.v(TAG, "onStartJob: Job fired.");
        Context context = getApplicationContext();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED);

        // if Syncthing should start, forward this information to SyncTriggerReceiver
        // otherwise Syncthing will stop
        if (params.getExtras().getInt(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, 0) == 1)
            intent.putExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
        localBroadcastManager.sendBroadcast(intent);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
