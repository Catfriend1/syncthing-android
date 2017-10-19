package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.receiver.BatteryReceiver;
import com.nutomic.syncthingandroid.receiver.NetworkReceiver;
import com.nutomic.syncthingandroid.receiver.PowerSaveModeChangedReceiver;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents
 * that are passed with {@link #ACTION_DEVICE_STATE_CHANGED}.
 */
public class DeviceStateHolder {

    private static final String TAG = "DeviceStateHolder";

    public static final String ACTION_DEVICE_STATE_CHANGED =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.DEVICE_STATE_CHANGED";

    /**
     * Intent extra containing a boolean saying whether wifi is connected or not.
     */
    public static final String EXTRA_IS_ALLOWED_NETWORK_CONNECTION =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_ALLOWED_NETWORK_CONNECTION";

    /**
     * Intent extra containging a boolean saying whether the device is
     * charging or not (any power source).
     */
    public static final String EXTRA_IS_CHARGING =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_CHARGING";

    public static final String EXTRA_IS_POWER_SAVING =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_POWER_SAVING";

    /**
     * Returns the value of "always_run_in_background" preference.
     */
    public static boolean alwaysRunInBackground(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);
    }

    public interface OnDeviceStateChangedListener {
        void onDeviceStateChanged();
    }

    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;
    private final DeviceStateChangedReceiver mReceiver = new DeviceStateChangedReceiver();
    private final OnDeviceStateChangedListener mListener;
    @Inject SharedPreferences mPreferences;

    private boolean mIsAllowedNetworkConnection = false;
    private String mWifiSsid;
    private boolean mIsCharging = false;
    private boolean mIsPowerSaving = true;

    public DeviceStateHolder(Context context, OnDeviceStateChangedListener listener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mBroadcastManager.registerReceiver(mReceiver, new IntentFilter(ACTION_DEVICE_STATE_CHANGED));
        mListener = listener;

        BatteryReceiver.updateInitialChargingStatus(mContext);
        NetworkReceiver.updateNetworkStatus(mContext);
        PowerSaveModeChangedReceiver.updatePowerSavingState(mContext);
    }

    public void shutdown() {
        mBroadcastManager.unregisterReceiver(mReceiver);
    }

    private class DeviceStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIsAllowedNetworkConnection =
                    intent.getBooleanExtra(EXTRA_IS_ALLOWED_NETWORK_CONNECTION, mIsAllowedNetworkConnection);
            mIsCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, mIsCharging);
            mIsPowerSaving = intent.getBooleanExtra(EXTRA_IS_POWER_SAVING, mIsPowerSaving);
            Log.i(TAG, "State updated, allowed network connection: " + mIsAllowedNetworkConnection +
                    ", charging: " + mIsCharging + ", power saving: " + mIsPowerSaving);

            updateWifiSsid();
            mListener.onDeviceStateChanged();
        }
    }

    private void updateWifiSsid() {
        mWifiSsid = null;
        WifiManager wifiManager =
                (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        // May be null, if WiFi has been turned off in meantime.
        if (wifiInfo != null) {
            mWifiSsid = wifiInfo.getSSID();
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    boolean shouldRun() {
        boolean prefRespectPowerSaving = mPreferences.getBoolean("respect_battery_saving", true);
        if (prefRespectPowerSaving && mIsPowerSaving)
            return false;

        if (alwaysRunInBackground(mContext)) {
            boolean prefStopMobileData = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false);

            if (prefStopMobileData && !isWhitelistedNetworkConnection())
                return false;

            if (prefStopNotCharging && !mIsCharging)
                return false;
        }

        return true;
    }

    private boolean isWhitelistedNetworkConnection() {
        boolean wifiConnected = mIsAllowedNetworkConnection;
        if (wifiConnected) {
            Set<String> ssids = mPreferences.getStringSet(Constants.PREF_SYNC_ONLY_WIFI_SSIDS, new HashSet<>());
            if (ssids.isEmpty()) {
                Log.d(TAG, "All SSIDs allowed for syncing");
                return true;
            } else {
                if (mWifiSsid != null && ssids.contains(mWifiSsid)) {
                    Log.d(TAG, "SSID [" + mWifiSsid + "] found in whitelist: " + ssids);
                    return true;
                } else {
                    Log.w(TAG, "SSID [" + mWifiSsid + "] unknown or not whitelisted, disallowing sync.");
                    return false;
                }
            }
        }
        Log.d(TAG, "Wifi not connected");
        return false;
    }

}
