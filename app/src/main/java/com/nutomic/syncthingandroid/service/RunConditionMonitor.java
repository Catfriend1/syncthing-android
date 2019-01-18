package com.nutomic.syncthingandroid.service;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.ReceiverManager;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents.
 */
public class RunConditionMonitor {

    private static final String TAG = "RunConditionMonitor";

    private static final String POWER_SOURCE_CHARGER_BATTERY = "ac_and_battery_power";
    private static final String POWER_SOURCE_CHARGER = "ac_power";
    private static final String POWER_SOURCE_BATTERY = "battery_power";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            /**
             * We need a Looper here, see issue https://github.com/Catfriend1/syncthing-android/issues/149
             */
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable updateShouldRunDecisionRunnable = new Runnable() {
                @Override
                public void run() {
                    updateShouldRunDecision();
                }
            };
            mainLooper.post(updateShouldRunDecisionRunnable);
        }
    };

    public interface OnShouldRunChangedListener {
        void onShouldRunDecisionChanged(boolean shouldRun);
    }

    public interface OnSyncPreconditionChangedListener {
        void onSyncPreconditionChanged(RunConditionMonitor runConditionMonitor);
    }

    private class SyncConditionResult {
        public Boolean conditionMet = false;
        public String explanation = "";

        SyncConditionResult(Boolean conditionMet) {
            this.conditionMet = conditionMet;
        }

        SyncConditionResult(Boolean conditionMet, String explanation) {
            this.conditionMet = conditionMet;
            this.explanation = explanation;
        }
    }

    private final Context mContext;
    private ReceiverManager mReceiverManager;
    private Resources res;
    private String mRunDecisionExplanation = "";

    @Inject
    SharedPreferences mPreferences;

    /**
     * Sending callback notifications through OnShouldRunChangedListener is enabled if not null.
     */
    private @Nullable OnShouldRunChangedListener mOnShouldRunChangedListener = null;

    /**
     * Sending callback notifications through OnSyncPreconditionChangedListener is enabled if not null.
     */
    private @Nullable OnSyncPreconditionChangedListener mOnSyncPreconditionChangedListener = null;

    /**
     * Stores the result of the last call to {@link #decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public RunConditionMonitor(Context context,
            OnShouldRunChangedListener onShouldRunChangedListener,
            OnSyncPreconditionChangedListener onSyncPreconditionChangedListener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        res = mContext.getResources();
        mOnShouldRunChangedListener = onShouldRunChangedListener;
        mOnSyncPreconditionChangedListener = onSyncPreconditionChangedListener;

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

    /**
     * Event handler that is fired after preconditions changed.
     * We then need to decide if syncthing should run.
     */
    public void updateShouldRunDecision() {
        // Check if the current conditions changed the result of decideShouldRun()
        // compared to the last determined result.
        boolean newShouldRun = decideShouldRun();
        if (newShouldRun != lastDeterminedShouldRun) {
            if (mOnShouldRunChangedListener != null) {
                mOnShouldRunChangedListener.onShouldRunDecisionChanged(newShouldRun);
            }
            lastDeterminedShouldRun = newShouldRun;
        }

        // Notify about changed preconditions.
        if (mOnSyncPreconditionChangedListener != null) {
            mOnSyncPreconditionChangedListener.onSyncPreconditionChanged(this);
        }
    }

    public String getRunDecisionExplanation() {
        return mRunDecisionExplanation;
    }

    /**
     * Each sync condition has its own evaluator function which
     * determines if the condition is met.
     */
    /**
     * Constants.PREF_RUN_ON_WIFI
     */
    private SyncConditionResult checkConditionSyncOnWifi(String prefNameSyncOnWifi) {
        boolean prefSyncOnWifi = mPreferences.getBoolean(prefNameSyncOnWifi, true);
        if (!prefSyncOnWifi) {
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_wifi_disallowed));
        }

        if (isWifiOrEthernetConnection()) {
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_wifi));
        }

        /**
         * if (prefRunOnWifi && !isWifiOrEthernetConnection()) { return false; }
         * This is intentionally not returning "false" as the flight mode workaround
         * relevant for some phone models needs to be done by the code below.
         * ConnectivityManager.getActiveNetworkInfo() returns "null" on those phones which
         * results in assuming !isWifiOrEthernetConnection even if the phone is connected
         * to wifi during flight mode, see {@link isWifiOrEthernetConnection}.
         */
        return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_on_wifi));
    }

    /**
     * Constants.PREF_WIFI_SSID_WHITELIST
     */
    private SyncConditionResult checkConditionSyncOnWhitelistedWifi(
            String prefNameUseWifiWhitelist,
            String prefNameSelectedWhitelistSsid) {
        boolean wifiWhitelistEnabled = mPreferences.getBoolean(prefNameUseWifiWhitelist, false);
        Set<String> whitelistedWifiSsids = mPreferences.getStringSet(prefNameSelectedWhitelistSsid, new HashSet<>());
        try {
            if (wifiWhitelistConditionMet(wifiWhitelistEnabled, whitelistedWifiSsids)) {
                return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_whitelisted_wifi));
            }
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_on_whitelisted_wifi));
        } catch (LocationUnavailableException e) {
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_location_unavailable));
        }
    }

    /**
     * Constants.PREF_RUN_ON_METERED_WIFI
     */
    private SyncConditionResult checkConditionSyncOnMeteredWifi(String prefNameSyncOnMeteredWifi) {
        boolean prefSyncOnMeteredWifi = mPreferences.getBoolean(prefNameSyncOnMeteredWifi, false);
        if (prefSyncOnMeteredWifi) {
            // Condition is always met as we allow both types of wifi - metered and non-metered.
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_metered_nonmetered_wifi));
        }

        // Check if we are on a non-metered wifi.
        if (!isMeteredNetworkConnection()) {
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_nonmetered_wifi));
        }

        // We disallowed non-metered wifi and are connected to metered wifi.
        return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_nonmetered_wifi));
    }

    /**
     * Constants.PREF_RUN_ON_MOBILE_DATA
     */
    private SyncConditionResult checkConditionSyncOnMobileData(String prefNameSyncOnMobileData) {
        boolean prefSyncOnMobileData = mPreferences.getBoolean(prefNameSyncOnMobileData, false);
        if (!prefSyncOnMobileData) {
            return new SyncConditionResult(false, res.getString(R.string.reason_mobile_data_disallowed));
        }

        if (isMobileDataConnection()) {
            return new SyncConditionResult(true, res.getString(R.string.reason_on_mobile_data));
        }

        return new SyncConditionResult(false, res.getString(R.string.reason_not_on_mobile_data));
    }

    /**
     * Determines if Syncthing should currently run.
     * Updates mRunDecisionExplanation.
     */
    private boolean decideShouldRun() {
        mRunDecisionExplanation = "";

        // Get sync condition preferences.
        String prefPowerSource = mPreferences.getString(Constants.PREF_POWER_SOURCE, POWER_SOURCE_CHARGER_BATTERY);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = mPreferences.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);
        boolean prefRunInFlightMode = mPreferences.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);

        // PREF_POWER_SOURCE
        switch (prefPowerSource) {
            case POWER_SOURCE_CHARGER:
                if (!isCharging()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_AC && !isCharging");
                    mRunDecisionExplanation = res.getString(R.string.reason_not_charging);
                    return false;
                }
                break;
            case POWER_SOURCE_BATTERY:
                if (isCharging()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_BATTERY && isCharging");
                    mRunDecisionExplanation = res.getString(R.string.reason_not_on_battery_power);
                    return false;
                }
                break;
            case POWER_SOURCE_CHARGER_BATTERY:
            default:
                break;
        }

        // Power saving
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (prefRespectPowerSaving && isPowerSaving()) {
                Log.v(TAG, "decideShouldRun: prefRespectPowerSaving && isPowerSaving");
                mRunDecisionExplanation = res.getString(R.string.reason_not_while_power_saving);
                return false;
            }
        }

        // Android global AutoSync setting.
        if (prefRespectMasterSync && !ContentResolver.getMasterSyncAutomatically()) {
            Log.v(TAG, "decideShouldRun: prefRespectMasterSync && !getMasterSyncAutomatically");
            mRunDecisionExplanation = res.getString(R.string.reason_not_while_auto_sync_data_disabled);
            return false;
        }

        // Run on mobile data?
        SyncConditionResult scr = checkConditionSyncOnMobileData(Constants.PREF_RUN_ON_MOBILE_DATA);
        mRunDecisionExplanation += scr.explanation;
        if (scr.conditionMet) {
            // Mobile data is connected.
            Log.v(TAG, "decideShouldRun: checkConditionSyncOnMobileData");
            return true;
        }

        // Run on WiFi?
        scr = checkConditionSyncOnWifi(Constants.PREF_RUN_ON_WIFI);
        mRunDecisionExplanation += scr.explanation;
        if (scr.conditionMet) {
            // Wifi is connected.
            Log.v(TAG, "decideShouldRun: checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.PREF_RUN_ON_METERED_WIFI);
            mRunDecisionExplanation += scr.explanation;
            if (scr.conditionMet) {
                // Wifi type is allowed.
                Log.v(TAG, "decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(Constants.PREF_USE_WIFI_SSID_WHITELIST, Constants.PREF_WIFI_SSID_WHITELIST);
                mRunDecisionExplanation += scr.explanation;
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    Log.v(TAG, "decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
                    return true;
                }
            }
        }

        // Run in flight mode.
        if (prefRunInFlightMode && isFlightMode()) {
            Log.v(TAG, "decideShouldRun: prefRunInFlightMode && isFlightMode");
            mRunDecisionExplanation += "\n" + res.getString(R.string.reason_on_flight_mode);
            return true;
        }

        /**
         * If none of the above run conditions matched, don't run.
         */
        Log.v(TAG, "decideShouldRun: return false");
        return false;
    }

    /**
     * Check if an object's individual sync conditions are met.
     */
    public Boolean checkObjectSyncConditions(String objectPrefixAndId) {
        // Sync on mobile data?
        SyncConditionResult scr = checkConditionSyncOnMobileData(Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(objectPrefixAndId));
        if (scr.conditionMet) {
            // Mobile data is connected.
            Log.v(TAG, "checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnMobileData");
            return true;
        }

        // Sync on WiFi?
        scr = checkConditionSyncOnWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(objectPrefixAndId));
        if (scr.conditionMet) {
            // Wifi is connected.
            Log.v(TAG, "checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(objectPrefixAndId));
            if (scr.conditionMet) {
                // Wifi type is allowed.
                Log.v(TAG, "checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(
                    Constants.DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(objectPrefixAndId),
                    Constants.DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(objectPrefixAndId)
                );
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    Log.v(TAG, "checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return whether the wifi whitelist run condition is met.
     * Precondition: An active wifi connection has been detected.
     */
    private boolean wifiWhitelistConditionMet (boolean prefWifiWhitelistEnabled,
            Set<String> whitelistedWifiSsids) throws LocationUnavailableException {
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
    private boolean isCharging() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // API level < 21
            return isCharging_API16();
        } else {
            // API level >= 21
            return isCharging_API17();
        }
    }

    @TargetApi(16)
    private boolean isCharging_API16() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @TargetApi(17)
    private boolean isCharging_API17() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
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

    private boolean isWifiConnectionWhitelisted(Set<String> whitelistedSsids)
            throws LocationUnavailableException{
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            // May be null, if wifi has been turned off in the meantime.
            Log.d(TAG, "isWifiConnectionWhitelisted: SSID unknown due to wifiInfo == null");
            return false;
        }
        String wifiSsid = wifiInfo.getSSID();
        if (wifiSsid == null || wifiSsid.equals("<unknown ssid>")) {
            throw new LocationUnavailableException("isWifiConnectionWhitelisted: Got null SSID. Try to enable android location service.");
        }
        return whitelistedSsids.contains(wifiSsid);
    }

    public class LocationUnavailableException extends Exception {

        public LocationUnavailableException(String message) {
            super(message);
        }

        public LocationUnavailableException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }

}
