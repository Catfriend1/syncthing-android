package com.nutomic.syncthingandroid.service;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
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
public class RunConditionMonitor {

    private static final String TAG = "RunConditionMonitor";

    private static final String POWER_SOURCE_AC_BATTERY = "ac_and_battery_power";
    private static final String POWER_SOURCE_AC = "ac_power";
    private static final String POWER_SOURCE_BATTERY = "battery_power";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            updateShouldRunDecision();
        }
    };

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

        // SyncStatusObserver to monitor android's "AutoSync" quick toggle.
        mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        if (mSyncStatusObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncStatusObserverHandle);
            mSyncStatusObserverHandle = null;
        }
        mReceiverManager.unregisterAllReceivers(mContext);
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
        boolean prefRunOnMobileData= mPreferences.getBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, false);
        boolean prefRunOnWifi= mPreferences.getBoolean(Constants.PREF_RUN_ON_WIFI, true);
        boolean prefRunOnMeteredWifi= mPreferences.getBoolean(Constants.PREF_RUN_ON_METERED_WIFI, false);
        Set<String> whitelistedWifiSsids = mPreferences.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>());
        boolean prefWifiWhitelistEnabled = !whitelistedWifiSsids.isEmpty();
        boolean prefRunInFlightMode = mPreferences.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);
        String prefPowerSource = mPreferences.getString(Constants.PREF_POWER_SOURCE, POWER_SOURCE_AC_BATTERY);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = mPreferences.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);

        // PREF_POWER_SOURCE
        switch (prefPowerSource) {
            case POWER_SOURCE_AC:
                if (!isOnAcPower()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_AC && !isOnAcPower");
                    return false;
                }
                break;
            case POWER_SOURCE_BATTERY:
                if (isOnAcPower()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_BATTERY && isOnAcPower");
                    return false;
                }
                break;
            case POWER_SOURCE_AC_BATTERY:
            default:
                break;
        }

        // Power saving
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (prefRespectPowerSaving && isPowerSaving()) {
                Log.v(TAG, "decideShouldRun: prefRespectPowerSaving && isPowerSaving");
                return false;
            }
        }

        // Android global AutoSync setting.
        if (prefRespectMasterSync && !ContentResolver.getMasterSyncAutomatically()) {
            Log.v(TAG, "decideShouldRun: prefRespectMasterSync && !getMasterSyncAutomatically");
            return false;
        }

        // Run on mobile data.
        if (prefRunOnMobileData && isMobileDataConnection()) {
            Log.v(TAG, "decideShouldRun: prefRunOnMobileData && isMobileDataConnection");
            return true;
        }

        // Run on wifi.
        if (prefRunOnWifi && isWifiOrEthernetConnection()) {
            if (prefRunOnMeteredWifi) {
                // We are on non-metered or metered wifi. Check if wifi whitelist run condition is met.
                if (wifiWhitelistConditionMet(prefWifiWhitelistEnabled, whitelistedWifiSsids)) {
                    Log.v(TAG, "decideShouldRun: prefRunOnWifi && isWifiOrEthernetConnection && prefRunOnMeteredWifi && wifiWhitelistConditionMet");
                    return true;
                }
            } else {
                // Check if we are on a non-metered wifi and if wifi whitelist run condition is met.
                if (!isMeteredNetworkConnection() && wifiWhitelistConditionMet(prefWifiWhitelistEnabled, whitelistedWifiSsids)) {
                    Log.v(TAG, "decideShouldRun: prefRunOnWifi && isWifiOrEthernetConnection && !prefRunOnMeteredWifi && !isMeteredNetworkConnection && wifiWhitelistConditionMet");
                    return true;
                }
            }
        }

        // Run in flight mode.
        if (prefRunInFlightMode && isFlightMode()) {
            Log.v(TAG, "decideShouldRun: prefRunInFlightMode && isFlightMode");
            return true;
        }

        /**
         * If none of the above run conditions matched, don't run.
         */
        Log.v(TAG, "decideShouldRun: return false");
        return false;
    }

    /**
     * Return whether the wifi whitelist run condition is met.
     * Precondition: An active wifi connection has been detected.
     */
    private boolean wifiWhitelistConditionMet(boolean prefWifiWhitelistEnabled,
            Set<String> whitelistedWifiSsids) {
        if (!prefWifiWhitelistEnabled) {
            Log.v(TAG, "handleWifiWhitelist: !prefWifiWhitelistEnabled");
            return true;
        }
        if (isWifiConnectionWhitelisted(whitelistedWifiSsids)) {
            Log.v(TAG, "handleWifiWhitelist: isWifiConnectionWhitelisted");
            return true;
        }
        return false;
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isOnAcPower() {
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

    private boolean isFlightMode() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni == null;
    }

    private boolean isMeteredNetworkConnection() {
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
        return cm.isActiveNetworkMetered();
    }

    private boolean isMobileDataConnection() {
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
            case ConnectivityManager.TYPE_BLUETOOTH:
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return true;
            default:
                return false;
        }
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
