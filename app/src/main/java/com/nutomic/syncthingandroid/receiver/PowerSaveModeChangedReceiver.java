package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;

import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.service.SyncthingService;

public class PowerSaveModeChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction()))
            return;

        updatePowerSavingState(context);
    }

    public static void updatePowerSavingState(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isPowerSaveMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pm.isPowerSaveMode();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(DeviceStateHolder.ACTION_DEVICE_STATE_CHANGED);
        intent.putExtra(DeviceStateHolder.EXTRA_IS_POWER_SAVING, isPowerSaveMode);
        lbm.sendBroadcast(intent);
    }
}
