package com.nutomic.syncthingandroid.receiver;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.service.SyncthingService;

/**
 * Receives network connection change intents and sends the wifi state to {@link SyncthingService}.
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
            return;

        if (!DeviceStateHolder.alwaysRunInBackground(context))
            return;

        updateNetworkStatus(context);
    }

    @TargetApi(16)
    public static void updateNetworkStatus(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean isAllowedConnectionType = false;
        if (ni == null) {
            Log.v(TAG, "In flight mode");
            // We still allow opening MainActivity and WebGuiActivity for local administration.
            isAllowedConnectionType = true;
        } else {
            Log.v(TAG, "Not in flight mode");
            boolean isWifi = ni.getType() == ConnectivityManager.TYPE_WIFI && ni.isConnected();
            boolean isNetworkMetered = (Build.VERSION.SDK_INT >= 16) ? cm.isActiveNetworkMetered() : false;
            isAllowedConnectionType = isWifi && !isNetworkMetered;
        }

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(DeviceStateHolder.ACTION_DEVICE_STATE_CHANGED);
        intent.putExtra(DeviceStateHolder.EXTRA_IS_ALLOWED_NETWORK_CONNECTION, isAllowedConnectionType);
        lbm.sendBroadcast(intent);
    }

}
