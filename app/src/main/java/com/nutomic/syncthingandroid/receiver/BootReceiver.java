package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) &&
                !intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED))
            return;

        if (!startServiceOnBoot(context))
            return;

        startServiceCompat(context);
    }

    /**
     * Workaround for starting service from background on Android 8+.
     *
     * https://stackoverflow.com/a/44505719/1837158
     */
    static void startServiceCompat(Context context) {
        Intent intent = new Intent(context, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        }
        else {
            context.startService(intent);
        }
    }

    private static boolean startServiceOnBoot(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
    }
}
