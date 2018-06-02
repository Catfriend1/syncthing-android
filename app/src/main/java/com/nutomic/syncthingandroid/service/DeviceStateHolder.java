package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.collect.Lists;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.receiver.BatteryReceiver;
import com.nutomic.syncthingandroid.receiver.NetworkReceiver;
import com.nutomic.syncthingandroid.receiver.PowerSaveModeChangedReceiver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents
 * that are passed with {@link #ACTION_DEVICE_STATE_CHANGED}.
 */
public class DeviceStateHolder implements SharedPreferences.OnSharedPreferenceChangeListener {

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
        void onDeviceStateChanged(boolean shouldRun);
    }

    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;
    private final DeviceStateChangedReceiver mReceiver = new DeviceStateChangedReceiver();
    private final OnDeviceStateChangedListener mListener;
    @Inject SharedPreferences mPreferences;

    private @Nullable NetworkReceiver mNetworkReceiver = null;
    private @Nullable BatteryReceiver mBatteryReceiver = null;
    private @Nullable BroadcastReceiver mPowerSaveModeChangedReceiver = null;

    private boolean mIsAllowedNetworkConnection;
    private String mWifiSsid;
    private boolean mIsCharging;
    private boolean mIsPowerSaving;

    /**
     * Stores the result of the last call to {@link decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public DeviceStateHolder(Context context, OnDeviceStateChangedListener listener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mBroadcastManager.registerReceiver(mReceiver, new IntentFilter(ACTION_DEVICE_STATE_CHANGED));
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        mListener = listener;
        updateReceivers();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        mBroadcastManager.unregisterReceiver(mReceiver);
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);

        unregisterReceiver(mNetworkReceiver);
        unregisterReceiver(mBatteryReceiver);
        unregisterReceiver(mPowerSaveModeChangedReceiver);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        List<String> watched = Lists.newArrayList(Constants.PREF_SYNC_ONLY_CHARGING,
                Constants.PREF_SYNC_ONLY_WIFI, Constants.PREF_RESPECT_BATTERY_SAVING,
                Constants.PREF_SYNC_ONLY_WIFI_SSIDS);
        if (watched.contains(key)) {
            updateReceivers();
        }
    }

    private void updateReceivers() {
        if (mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false)) {
            Log.i(TAG, "Listening for network state changes");
            NetworkReceiver.updateNetworkStatus(mContext);
            mNetworkReceiver = new NetworkReceiver();
            mContext.registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } else {
            Log.i(TAG, "Stopped listening to network state changes");
            unregisterReceiver(mNetworkReceiver);
            mNetworkReceiver = null;
        }

        if (mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false)) {
            Log.i(TAG, "Listening to battery state changes");
            BatteryReceiver.updateInitialChargingStatus(mContext);
            mBatteryReceiver = new BatteryReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            mContext.registerReceiver(mBatteryReceiver, filter);
        } else {
            Log.i(TAG, "Stopped listening to battery state changes");
            unregisterReceiver(mBatteryReceiver);
            mBatteryReceiver = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                mPreferences.getBoolean("respect_battery_saving", true)) {
            Log.i(TAG, "Listening to power saving changes");
            PowerSaveModeChangedReceiver.updatePowerSavingState(mContext);
            mPowerSaveModeChangedReceiver = new PowerSaveModeChangedReceiver();
            mContext.registerReceiver(mPowerSaveModeChangedReceiver,
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        } else {
            Log.i(TAG, "Stopped listening to power saving changes");
            unregisterReceiver(mPowerSaveModeChangedReceiver);
            mPowerSaveModeChangedReceiver = null;
        }
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        if (receiver != null)
            mContext.unregisterReceiver(receiver);
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
            updateShouldRunDecision();
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

    public void updateShouldRunDecision() {
        // Check if the current conditions changed the result of decideShouldRun()
        // compared to the last determined result.
        boolean newShouldRun = decideShouldRun();
        if (newShouldRun != lastDeterminedShouldRun) {
            mListener.onDeviceStateChanged(newShouldRun);
            lastDeterminedShouldRun = newShouldRun;
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    private boolean decideShouldRun() {
        boolean prefRespectPowerSaving = mPreferences.getBoolean("respect_battery_saving", true);
        if (prefRespectPowerSaving && mIsPowerSaving)
            return false;

        if (alwaysRunInBackground(mContext)) {
            boolean prefStopMobileData = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false);

            updateWifiSsid();
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
        return false;
    }

}
