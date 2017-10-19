package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;

import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.service.SyncthingService;

/**
 * Receives battery plug/unplug intents and sends the charging state to {@link SyncthingService}.
 */
public class BatteryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                && !Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction()))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        boolean isCharging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        Intent i = new Intent(DeviceStateHolder.ACTION_DEVICE_STATE_CHANGED);
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
        lbm.sendBroadcast(i);

        // Make sure service is running.
        BootReceiver.startServiceCompat(context);
    }

    /**
     * Get the current charging status without waiting for connected/disconnected events.
     */
    public static void updateInitialChargingStatus(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(DeviceStateHolder.ACTION_DEVICE_STATE_CHANGED);
        intent.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
        lbm.sendBroadcast(intent);
    }

}
