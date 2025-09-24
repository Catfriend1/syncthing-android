package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.service.SyncthingService;

import eu.chainfire.libsuperuser.Shell;

import java.lang.SecurityException;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /**
     * For testing purposes:
     * adb root & adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Boolean bootCompleted = intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED);
        Boolean packageReplaced = intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED);
        if (!bootCompleted && !packageReplaced) {
            return;
        }

        if ("HMD Global".equals(Build.MANUFACTURER)) {
            disableDuraSpeed(context);
        }

        if (packageReplaced) {
            if (getPrefUseRoot(context) && Shell.SU.available()) {
                /**
                 * In Root mode, there will be a SyncthingNative process left running after app update.
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

    /**
     * Prerequisistes:
     * - android.permission.WRITE_SETTINGS
     * - android.permission.WRITE_SECURE_SETTINGS
     *      adb shell pm grant ${applicationId} android.permission.WRITE_SECURE_SETTINGS
     */
    private static void disableDuraSpeed(Context context) {
        Log.d(TAG, "Disabling DuraSpeed");
        try {
            Settings.Global.putInt(context.getContentResolver(), "setting.duraspeed.enabled", -1);
            Settings.Global.putInt(context.getContentResolver(), "setting.duraspeed.enabled", 0);
        } catch (SecurityException e) {
            Log.e(TAG, "Insufficient permissions to disable DuraSpeed. Run the following command from a computer: 'adb shell pm grant " +
                    context.getPackageName() +
                    " android.permission.WRITE_SECURE_SETTINGS'");
        }
    }
}
