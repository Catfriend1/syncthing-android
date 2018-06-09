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
import com.nutomic.syncthingandroid.service.ReceiverManager;

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
    @Inject SharedPreferences mPreferences;

    private @Nullable DeviceStateChangedReceiver mDeviceStateChangedReceiver = null;
    private ReceiverManager mReceiverManager;

    // Those receivers are managed by {@link mReceiverManager}.
    private NetworkReceiver mNetworkReceiver;
    private BatteryReceiver mBatteryReceiver;
    private PowerSaveModeChangedReceiver mPowerSaveModeChangedReceiver;

    private boolean mIsAllowedConnectionType;
    private String mWifiSsid;
    private boolean mIsCharging;
    private boolean mIsPowerSaving;

    /**
     * Sending callback notifications through {@link OnDeviceStateChangedListener} is enabled if not null.
     */
    private @Nullable OnDeviceStateChangedListener mOnDeviceStateChangedListener = null;

    /**
     * Stores the result of the last call to {@link decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public DeviceStateHolder(Context context, OnDeviceStateChangedListener listener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        mOnDeviceStateChangedListener = listener;

        mDeviceStateChangedReceiver = new DeviceStateChangedReceiver();
        mBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mBroadcastManager.registerReceiver(mDeviceStateChangedReceiver, new IntentFilter(ACTION_DEVICE_STATE_CHANGED));
        registerChildReceivers();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mReceiverManager.unregisterAllReceivers(mContext);
        if (mDeviceStateChangedReceiver != null) {
            mBroadcastManager.unregisterReceiver(mDeviceStateChangedReceiver);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        List<String> watched = Lists.newArrayList(Constants.PREF_SYNC_ONLY_CHARGING,
                Constants.PREF_SYNC_ONLY_WIFI, Constants.PREF_RESPECT_BATTERY_SAVING,
                Constants.PREF_SYNC_ONLY_WIFI_SSIDS);
        if (watched.contains(key)) {
            mReceiverManager.unregisterAllReceivers(mContext);
            registerChildReceivers();
        }
    }

    private void registerChildReceivers() {
        boolean incomingBroadcastEventsExpected = false;

        if (mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false)) {
            Log.i(TAG, "Creating NetworkReceiver");
            NetworkReceiver.updateNetworkStatus(mContext);
            mNetworkReceiver = new NetworkReceiver();
            ReceiverManager.registerReceiver(mContext, mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            incomingBroadcastEventsExpected = true;
        }

        if (mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false)) {
            Log.i(TAG, "Creating BatteryReceiver");
            BatteryReceiver.updateInitialChargingStatus(mContext);
            mBatteryReceiver = new BatteryReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            ReceiverManager.registerReceiver(mContext, mBatteryReceiver, filter);
            incomingBroadcastEventsExpected = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true)) {
            Log.i(TAG, "Creating PowerSaveModeChangedReceiver");
            PowerSaveModeChangedReceiver.updatePowerSavingState(mContext);
            mPowerSaveModeChangedReceiver = new PowerSaveModeChangedReceiver();
            ReceiverManager.registerReceiver(mContext, mPowerSaveModeChangedReceiver,
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
            incomingBroadcastEventsExpected = true;
        }

        // If no broadcast messages can be received as we didn't register an emitter,
        // force an initial decision to be made.
        if (!incomingBroadcastEventsExpected) {
            updateShouldRunDecision();
        }
    }

    private class DeviceStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIsAllowedConnectionType =
                    intent.getBooleanExtra(EXTRA_IS_ALLOWED_NETWORK_CONNECTION, mIsAllowedConnectionType);
            mIsCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, mIsCharging);
            mIsPowerSaving = intent.getBooleanExtra(EXTRA_IS_POWER_SAVING, mIsPowerSaving);
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
            if (mOnDeviceStateChangedListener != null) {
                mOnDeviceStateChangedListener.onDeviceStateChanged(newShouldRun);
            }
            lastDeterminedShouldRun = newShouldRun;
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    private boolean decideShouldRun() {
        Log.v(TAG, "State updated: IsAllowedConnectionType: " + mIsAllowedConnectionType +
                ", IsCharging: " + mIsCharging + ", IsPowerSaving: " + mIsPowerSaving);

        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        if (prefRespectPowerSaving && mIsPowerSaving)
            return false;

        if (alwaysRunInBackground(mContext)) {
            boolean prefStopMobileData = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false);

            updateWifiSsid();
            if (prefStopMobileData && !isWhitelistedWifiConnection())
                return false;

            if (prefStopNotCharging && !mIsCharging)
                return false;
        }

        return true;
    }

    private boolean isWhitelistedWifiConnection() {
        boolean wifiConnected = mIsAllowedConnectionType;
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
