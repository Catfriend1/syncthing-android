package com.nutomic.syncthingandroid.service;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.collect.Lists;
import com.nutomic.syncthingandroid.SyncthingApp;
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
public class RunConditionMonitor implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RunConditionMonitor";

    public interface OnRunConditionChangedListener {
        void onRunConditionChanged(boolean shouldRun);
    }

    private final Context mContext;
    @Inject SharedPreferences mPreferences;
    private ReceiverManager mReceiverManager;

    /**
     * Sending callback notifications through {@link OnDeviceStateChangedListener} is enabled if not null.
     */
    private @Nullable OnRunConditionChangedListener mOnRunConditionChangedListener = null;

    /**
     * Stores the result of the last call to {@link decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public RunConditionMonitor(Context context, OnRunConditionChangedListener listener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        mOnRunConditionChangedListener = listener;

        /**
         * Register broadcast receivers.
         */
        // NetworkReceiver
        ReceiverManager.registerReceiver(mContext, new NetworkReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // BatteryReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ReceiverManager.registerReceiver(mContext, new BatteryReceiver(), filter);

        // PowerSaveModeChangedReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ReceiverManager.registerReceiver(mContext,
                    new PowerSaveModeChangedReceiver(),
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        }

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        List<String> watched = Lists.newArrayList(
            Constants.PREF_SYNC_ONLY_CHARGING,
            Constants.PREF_SYNC_ONLY_WIFI, Constants.PREF_RESPECT_BATTERY_SAVING,
            Constants.PREF_SYNC_ONLY_WIFI_SSIDS);
        if (watched.contains(key)) {
            // Force a re-evaluation of which run conditions apply according to the changed prefs.
            updateShouldRunDecision();
        }
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    || Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class PowerSaveModeChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    public void updateShouldRunDecision() {
        // Check if the current conditions changed the result of decideShouldRun()
        // compared to the last determined result.
        boolean newShouldRun = decideShouldRun();
        if (newShouldRun != lastDeterminedShouldRun) {
            if (mOnRunConditionChangedListener != null) {
                mOnRunConditionChangedListener.onRunConditionChanged(newShouldRun);
            }
            lastDeterminedShouldRun = newShouldRun;
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    private boolean decideShouldRun() {
        // Get run conditions preferences.
        boolean prefAlwaysRunInBackground = mPreferences.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRunOnlyOnWifi= mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false);
        boolean prefRunOnlyWhenCharging = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false);
        Set<String> whitelistedWifiSsids = mPreferences.getStringSet(Constants.PREF_SYNC_ONLY_WIFI_SSIDS, new HashSet<>());
        boolean prefWifiWhitelistEnabled = !whitelistedWifiSsids.isEmpty();

        // Power saving
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (prefRespectPowerSaving && isPowerSaving()) {
                return false;
            }
        }

        // Always run in background
        if (!prefAlwaysRunInBackground) {
            /**
             * User did not specify run conditions in the options.
             * The app is displaying a foreground activity and syncthing should run.
             */
            Log.v(TAG, "decideShouldRun: !prefAlwaysRunInBackground");
            return true;
        }

        // Run only when charging.
        if (prefRunOnlyWhenCharging && !isCharging()) {
            Log.v(TAG, "decideShouldRun: prefRunOnlyWhenCharging && !isCharging");
            return false;
        }

        // Run only on wifi.
        if (prefRunOnlyOnWifi && !isWifiOrEthernetConnection()) {
            // Not on wifi.
            Log.v(TAG, "decideShouldRun: prefRunOnlyOnWifi && !isWifiOrEthernetConnection");
            return false;
        }

        // Run only on whitelisted wifi ssids.
        if (prefRunOnlyOnWifi && prefWifiWhitelistEnabled) {
            // Wifi connection detected. Wifi ssid whitelist enabled.
            Log.v(TAG, "decideShouldRun: prefRunOnlyOnWifi && prefWifiWhitelistEnabled");
            return isWifiConnectionWhitelisted(whitelistedWifiSsids);
        }

        /**
         * Respect power saving, device is not in power-save mode.
         * Always run in background is the only pref that is enabled.
         * Run only when charging, charging.
         * Run only on wifi, wifi connection detected, wifi ssid whitelist disabled.
         */
        Log.v(TAG, "decideShouldRun: return true");
        return true;
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isCharging() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @TargetApi(21)
    private boolean isPowerSaving() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "isPowerSaving may not be called on pre-lollipop android versions.");
            return false;
        }
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(TAG, "getSystemService(POWER_SERVICE) unexpectedly returned NULL.");
            return false;
        }
        return powerManager.isPowerSaveMode();
    }

    private boolean isWifiOrEthernetConnection() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            return false;
        }
        if (!ni.isConnected()) {
            // No network connection.
            return false;
        }
        switch (ni.getType()) {
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_WIMAX:
            case ConnectivityManager.TYPE_ETHERNET:
                return true;
            default:
                return false;
        }
    }

    private boolean isWifiConnectionWhitelisted(Set<String> whitelistedSsids) {
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            // May be null, if wifi has been turned off in the meantime.
            Log.d(TAG, "isWifiConnectionWhitelisted: SSID unknown due to wifiInfo == null");
            return false;
        }
        String wifiSsid = wifiInfo.getSSID();
        if (wifiSsid == null) {
            Log.w(TAG, "isWifiConnectionWhitelisted: Got null SSID. Try to enable android location service.");
            return false;
        }
        return whitelistedSsids.contains(wifiSsid);
    }

}
