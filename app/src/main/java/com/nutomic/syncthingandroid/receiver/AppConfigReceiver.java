package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;

import javax.inject.Inject;

/**
 * Broadcast-receiver to control and configure Syncthing remotely.
 */
public class AppConfigReceiver extends BroadcastReceiver {

    private static final String TAG = "AppConfigReceiver";

    /**
     * Start the Syncthing-Service
     */
    private static final String ACTION_START = "com.github.catfriend1.syncthingandroid.action.START";

    /**
     * Stop the Syncthing-Service
     * If startServiceOnBoot is enabled the service must not be stopped. Instead a
     * notification is presented to the user.
     */
    private static final String ACTION_STOP  = "com.github.catfriend1.syncthingandroid.action.STOP";

    @Inject NotificationHandler mNotificationHandler;

    @Override
    public void onReceive(Context context, Intent intent) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        String intentAction = intent.getAction();
        if (!getPrefBroadcastServiceControl(context)) {
            switch (intentAction) {
                case ACTION_START:
                case ACTION_STOP:
                    Log.w(TAG, "Ignored intent action \"" + intentAction +
                                "\". Enable Settings > Experimental > Service Control by Broadcast if you like to control syncthing remotely.");
                    break;
            }
            return;
        }

        switch (intentAction) {
            case ACTION_START:
                BootReceiver.startServiceCompat(context);
                break;
            case ACTION_STOP:
                if (getPrefStartServiceOnBoot(context)) {
                    mNotificationHandler.showStopSyncthingWarningNotification();
                } else {
                    context.stopService(new Intent(context, SyncthingService.class));
                }
                break;
        }
    }

    private static boolean getPrefBroadcastServiceControl(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_BROADCAST_SERVICE_CONTROL, false);
    }

    private static boolean getPrefStartServiceOnBoot(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
    }
}
