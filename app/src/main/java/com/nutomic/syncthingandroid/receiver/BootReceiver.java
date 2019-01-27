package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.service.SyncthingService;

import eu.chainfire.libsuperuser.Shell;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Boolean bootCompleted = intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED);
        Boolean packageReplaced = intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED);
        if (!bootCompleted && !packageReplaced) {
            return;
        }

        if (packageReplaced) {
            if (getPrefUseRoot(context) && Shell.SU.available()) {
                /**
                 * In Root mode, there will be a SyncthingNative process left running after app update.
                 * See https://github.com/Catfriend1/syncthing-android/issues/261
                 */
                Log.d(TAG, "ACTION_MY_PACKAGE_REPLACED: Killing leftover SyncthingNative instance if present ...");
                new SyncthingRunnable(context, SyncthingRunnable.Command.main).killSyncthing();
            }
        }

        // Check if we should (re)start now.
        if (!getPrefStartServiceOnBoot(context)) {
            return;
        }

        startServiceCompat(context);
    }

    /**
     * Workaround for starting service from background on Android 8+.
     *
     * https://stackoverflow.com/a/44505719/1837158
     */
    public static void startServiceCompat(Context context) {
        Intent intent = new Intent(context, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        }
        else {
            context.startService(intent);
        }
    }

    private static boolean getPrefStartServiceOnBoot(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
    }

    private static boolean getPrefUseRoot(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_USE_ROOT, false);
    }
}
