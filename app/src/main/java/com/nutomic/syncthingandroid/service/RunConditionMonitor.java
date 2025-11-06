package com.nutomic.syncthingandroid.service;

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
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.SystemClock;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.util.JobUtils;

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

    private Boolean ENABLE_VERBOSE_LOG = false;

    public static final String ACTION_SYNC_TRIGGER_FIRED =
        ".service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED";

    public static final String ACTION_UPDATE_SHOULDRUN_DECISION =
        ".service.RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION";

    public static final String EXTRA_BEGIN_ACTIVE_TIME_WINDOW =
        ".service.RunConditionMonitor.BEGIN_ACTIVE_TIME_WINDOW";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
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
    private @Nullable SyncTriggerReceiver mSyncTriggerReceiver = null;
    private @Nullable UpdateShouldRunDecisionReceiver mUpdateShouldRunDecisionReceiver = null;
    private Resources res;
    private String mRunDecisionExplanation = "";

    /**
     * Only relevant if the user has enabled turning Syncthing on by
     * time schedule for a specific amount of time periodically.
     * Holds true if we are within a "SyncthingNative should run" time frame.
     * Initial status false because we check if the last sync was more than one hour ago on app start.
     */
    private Boolean mTimeConditionMatch = false;
    // Avoid re-scheduling start if run conditions change while already running.
    private Boolean mRunAllowedStopScheduled = false;

    private int triggeredSyncDurationS = 10;

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
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("Created new instance");
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
        ReceiverManager.registerReceiver(mContext,
                new PowerSaveModeChangedReceiver(),
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));

        // SyncStatusObserver to monitor android's "AutoSync" quick toggle.
        mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);

        // SyncTriggerReceiver
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mSyncTriggerReceiver = new SyncTriggerReceiver();
        localBroadcastManager.registerReceiver(mSyncTriggerReceiver,
                new IntentFilter(ACTION_SYNC_TRIGGER_FIRED));

        // UpdateShouldRunDecisionReceiver
        mUpdateShouldRunDecisionReceiver = new UpdateShouldRunDecisionReceiver();
        localBroadcastManager.registerReceiver(mUpdateShouldRunDecisionReceiver,
                new IntentFilter(ACTION_UPDATE_SHOULDRUN_DECISION));

        long lastSyncTimeSinceBootMillisecs = mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0);
        long elapsedRealtime = SystemClock.elapsedRealtime();
        /**
         * after a reboot lastSyncTimeSinceBootMillisecs might be larger than elapsedRealtime,
         * since it is referring to the previous reboot
         * in this case we set mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0)
         * to -WAIT_FOR_NEXT_SYNC_DELAY_SECS, so mTimeConditionMatch is guaranteed to be true
         */
        if (lastSyncTimeSinceBootMillisecs > elapsedRealtime) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putLong(Constants.PREF_LAST_RUN_TIME, -Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60 * 1000);
            editor.apply();
            lastSyncTimeSinceBootMillisecs = 0;
        }

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();

        // Initially schedule the SyncTrigger job.
        int elapsedSecondsSinceLastSync = (int) (elapsedRealtime - lastSyncTimeSinceBootMillisecs) / 1000;
        Log.d(TAG, "JobPrepare: mTimeConditionMatch=" + mTimeConditionMatch.toString() +
                ", elapsedRealtime=" + elapsedRealtime +
                ", lastSyncTimeSinceBootMillisecs=" + lastSyncTimeSinceBootMillisecs +
                ", elapsedSecondsSinceLastSync=" + elapsedSecondsSinceLastSync
        );
        JobUtils.scheduleSyncTriggerServiceJob(
                context,
                mTimeConditionMatch ?
                    triggeredSyncDurationS :
                       /**
                        * if Constants.WAIT_FOR_NEXT_SYNC_DELAY_SECS - elapsedSecondsSinceLastSync is < 0,
                        * mTimeConditionMatch is set to true during updateShouldRunDecision().
                        * Thus the false case cannot be triggered if the delay for scheduleSyncTriggerServiceJob would be negative
                        */
                        (Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60) - elapsedSecondsSinceLastSync,
                !mTimeConditionMatch
        );
    }

    public void shutdown() {
        LogV("Shutting down");
        JobUtils.cancelAllScheduledJobs(mContext);
        if (mSyncStatusObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncStatusObserverHandle);
            mSyncStatusObserverHandle = null;
        }

        // SyncTriggerReceiver
        if (mSyncTriggerReceiver != null) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            localBroadcastManager.unregisterReceiver(mSyncTriggerReceiver);
            mSyncTriggerReceiver = null;
        }

        // UpdateShouldRunDecisionReceiver
        if (mUpdateShouldRunDecisionReceiver != null) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            localBroadcastManager.unregisterReceiver(mUpdateShouldRunDecisionReceiver);
            mUpdateShouldRunDecisionReceiver = null;
        }
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    || Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                SystemClock.sleep(5000);
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

    private class SyncTriggerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mRunAllowedStopScheduled = false;
            boolean extraBeginActiveTimeWindow = intent.getBooleanExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, false);
            LogV("SyncTriggerReceiver: onReceive, extraBeginActiveTimeWindow=" + Boolean.toString(extraBeginActiveTimeWindow));

            boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);
            if (!prefRunOnTimeSchedule) {
                /**
                 * The feature is currently disabled.
                 * Reschedule the job to see if the user turned on this feature in the meantime.
                 */
                mTimeConditionMatch = false;
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60,
                        true
                );
                return;
            }

            // extraBeginActiveTimeWindow determines whether syncthing should start or stop
            if (extraBeginActiveTimeWindow) {
                // We should immediately start SyncthingNative for TRIGGERED_SYNC_DURATION_SECS.
                mTimeConditionMatch = true;
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncDurationS,
                        false
                );
                mRunAllowedStopScheduled = true;
            } else {
                /**
                 * Toggle the "digital input" for this condition as the condition change is
                 * triggered by a time schedule.
                 */
                mTimeConditionMatch = false;
                /**
                 * If Syncthing is running and the last run was more than one hour ago,
                 * this stop job might actually start Syncthing (resp. leave it running) because
                 * mTimeConditionsMatch is switched to true if last run was more than 1 hour ago.
                 * So in this case we put a new (fake) last run time slightly less than one hour ago.
                 * If Syncthing really is stopped (which it should) then the wrong time gets
                 * corrected immediately
                 */
                long lastRunTimeMillis = mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0);
                if (lastDeterminedShouldRun && SystemClock.elapsedRealtime() - lastRunTimeMillis > Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60 * 1000) {
                    SharedPreferences.Editor editor = mPreferences.edit();
                    editor.putLong(Constants.PREF_LAST_RUN_TIME, SystemClock.elapsedRealtime() - Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60 * 1000 + 60*1000);
                    editor.apply();
                }
            }
            updateShouldRunDecision();

            /**
             * Reschedule the job.
             * If we are within a "SyncthingNative shouldn't run" time frame,
             * let the receiver fire and change to "SyncthingNative should run" after
             * WAIT_FOR_NEXT_SYNC_DELAY_SECS seconds elapsed.
             * If we are within a "SyncthingNative should run" time frame,
             * the change to "SyncthingNative shouldn't run" after
             * TRIGGERED_SYNC_DURATION_SECS seconds elapsed should actually
             * be scheduled inside updateShouldRunDecision(), but his might
             * not always be the case.
             * Thus we schedule an additional change to "SyncthingNative shouldn't run"
             * after TRIGGERED_SYNC_DURATION_SECS seconds elapsed, but without
             * cancelling other jobs. This should only serve as a backup job and
             * will not fire if the job inside updateShouldRunDecision() is
             * scheduled correctly.
             */
            if (!mRunAllowedStopScheduled && !lastDeterminedShouldRun) {
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60,
                        true
                );
            } else {
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncDurationS,
                        false);
            }
        }
    }

    private class UpdateShouldRunDecisionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogV("UpdateShouldRunDecisionReceiver: onReceive");
            updateShouldRunDecision();
        }
    }

    /**
     * Event handler that is fired after preconditions changed.
     * We then need to decide if syncthing should run.
     */
    public void updateShouldRunDecision() {
        if (!Constants.isRunningOnEmulator()) {
            triggeredSyncDurationS = Integer.parseInt(mPreferences.getString(Constants.PREF_SYNC_DURATION_MINUTES, "5")) * 60;
        }

        boolean newShouldRun = decideShouldRun();
        if (newShouldRun) {
            /**
             * Trigger:
             *  a) Sync pre-conditions changed
             *      a1) AND SyncthingService.State should remain ACTIVE
             *      a2) AND SyncthingService.State should transition from INIT/DISABLED to ACTIVE
             *  b) Sync pre-conditions did not change
             *      b1) AND SyncthingService.State should remain ACTIVE
             *          because a reevaluation of the run conditions was forced from code.
             * Action:
             *  SyncthingService will evaluate custom per-object run conditions
             *  and pause/unpause objects accordingly.
             */
             if (mOnSyncPreconditionChangedListener != null) {
                 mOnSyncPreconditionChangedListener.onSyncPreconditionChanged(this);
             }
        }

        /**
         * Check if the current conditions changed the result of decideShouldRun()
         * compared to the last determined result.
         */
        if (newShouldRun != lastDeterminedShouldRun) {
            /**
             * Notify SyncthingService in case it has to transition from
             * a) INIT/DISABLED => STARTING => ACTIVE
             * b) ACTIVE => DISABLED
             */
            if (mOnShouldRunChangedListener != null) {
                mOnShouldRunChangedListener.onShouldRunDecisionChanged(newShouldRun);
                lastDeterminedShouldRun = newShouldRun;
            }
            if (newShouldRun &&
                    !mRunAllowedStopScheduled &&
                    mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false) &&
                    mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP) == Constants.BTNSTATE_NO_FORCE_START_STOP) {
                JobUtils.cancelAllScheduledJobs(mContext);
                JobUtils.scheduleSyncTriggerServiceJob(
                        mContext,
                        triggeredSyncDurationS,
                        false
                );
                mRunAllowedStopScheduled = true;
            }
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putLong(Constants.PREF_LAST_RUN_TIME, SystemClock.elapsedRealtime());
            editor.apply();
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

    private SyncConditionResult checkConditionSyncOnPowerSource(String prefNameSyncOnPowerSource) {
        switch (mPreferences.getString(prefNameSyncOnPowerSource, Constants.PowerSource.CHARGER_BATTERY)) {
            case Constants.PowerSource.CHARGER:
                if (!isCharging_API17()) {
                    return new SyncConditionResult(false, res.getString(R.string.reason_not_charging));
                }
                break;
            case Constants.PowerSource.BATTERY:
                if (isCharging_API17()) {
                    return new SyncConditionResult(false, res.getString(R.string.reason_not_on_battery_power));
                }
                break;
            case Constants.PowerSource.CHARGER_BATTERY:
            default:
                break;
        }
        return new SyncConditionResult(true, "");
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
     * Constants.PREF_RUN_ON_ROAMING
     */
    private SyncConditionResult checkConditionSyncOnRoaming(String prefNameSyncOnRoaming) {
        boolean prefSyncOnRoaming = mPreferences.getBoolean(prefNameSyncOnRoaming, false);
        if (prefSyncOnRoaming) {
            // Condition is always met as we allow both types of mobile data networks - roaming and non-roaming.
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_roaming_nonroaming_mobile_data));
        }

        // Check if we are on a non-roaming mobile data network.
        if (!isRoamingNetworkConnection()) {
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_nonroaming_mobile_data));
        }

        // We disallowed non-roaming mobile data and are connected to a mobile data network in roaming mode.
        return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_nonroaming_mobile_data));
    }

    /**
     * Determines if Syncthing should currently run.
     * Updates mRunDecisionExplanation.
     */
    private boolean decideShouldRun() {
        mRunDecisionExplanation = "";

        // Get sync condition preferences.
        int prefBtnStateForceStartStop = mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = mPreferences.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);
        boolean prefRunInFlightMode = mPreferences.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);
        boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);

        // PREF_BTNSTATE_FORCE_START_STOP
        switch (prefBtnStateForceStartStop) {
            case Constants.BTNSTATE_FORCE_START:
                LogV("decideShouldRun: PREF_BTNSTATE_FORCE_START");
                mRunDecisionExplanation = res.getString(R.string.reason_force_start);
                return true;
            case Constants.BTNSTATE_FORCE_STOP:
                LogV("decideShouldRun: PREF_BTNSTATE_FORCE_STOP");
                mRunDecisionExplanation = res.getString(R.string.reason_force_stop);
                return false;
        }

        // PREF_RUN_ON_TIME_SCHEDULE
        // set mTimeConditionMatch to true if the last run was more than WAIT_FOR_NEXT_SYNC_DELAY_SECS ago
        if (SystemClock.elapsedRealtime() - mPreferences.getLong(Constants.PREF_LAST_RUN_TIME,0) > Integer.parseInt(mPreferences.getString(Constants.PREF_SLEEP_INTERVAL_MINUTES,"60")) * 60 * 1000)
            mTimeConditionMatch = true;
        if (prefRunOnTimeSchedule && !mTimeConditionMatch) {
            // Currently, we aren't within a "SyncthingNative should run" time frame.
            LogV("decideShouldRun: PREF_RUN_ON_TIME_SCHEDULE && !mTimeConditionMatch");
            int minutes = (int) (SystemClock.elapsedRealtime() - mPreferences.getLong(Constants.PREF_LAST_RUN_TIME,0))/(60*1000);
            String minutesText;
            if (minutes == 0)
                minutesText = res.getString(R.string.reason_not_within_time_frame_0_min);
            else
                minutesText = String.format(res.getQuantityString(R.plurals.reason_not_within_time_frame_minutes,minutes),minutes);
            mRunDecisionExplanation = String.format(res.getString(R.string.reason_not_within_time_frame_2),minutesText);
            return false;
        }

        // PREF_POWER_SOURCE
        SyncConditionResult scr = checkConditionSyncOnPowerSource(Constants.PREF_POWER_SOURCE);
        if (!scr.conditionMet) {
            LogV("checkConditionSyncOnPowerSource: " + scr.explanation);
            mRunDecisionExplanation = scr.explanation;
            return false;
        }

        // Power saving
        if (prefRespectPowerSaving && isPowerSaving()) {
            LogV("decideShouldRun: prefRespectPowerSaving && isPowerSaving");
            mRunDecisionExplanation = res.getString(R.string.reason_not_while_power_saving);
            return false;
        }

        // Android global AutoSync setting.
        if (prefRespectMasterSync && !ContentResolver.getMasterSyncAutomatically()) {
            LogV("decideShouldRun: prefRespectMasterSync && !getMasterSyncAutomatically");
            mRunDecisionExplanation = res.getString(R.string.reason_not_while_auto_sync_data_disabled);
            return false;
        }

        // Run on mobile data?
        scr = checkConditionSyncOnMobileData(Constants.PREF_RUN_ON_MOBILE_DATA);
        mRunDecisionExplanation += scr.explanation;
        if (scr.conditionMet) {
            // Mobile data is connected.
            LogV("decideShouldRun: checkConditionSyncOnMobileData");

            scr = checkConditionSyncOnRoaming(Constants.PREF_RUN_ON_ROAMING);
            mRunDecisionExplanation += scr.explanation;
            if (scr.conditionMet) {
                // Mobile data connection type is allowed.
                LogV("decideShouldRun: checkConditionSyncOnMobileData && checkConditionSyncOnRoaming");
                return true;
            }
        }

        // Run on WiFi?
        scr = checkConditionSyncOnWifi(Constants.PREF_RUN_ON_WIFI);
        mRunDecisionExplanation += scr.explanation;
        if (scr.conditionMet) {
            // Wifi is connected.
            LogV("decideShouldRun: checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.PREF_RUN_ON_METERED_WIFI);
            mRunDecisionExplanation += scr.explanation;
            if (scr.conditionMet) {
                // Wifi type is allowed.
                LogV("decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(Constants.PREF_USE_WIFI_SSID_WHITELIST, Constants.PREF_WIFI_SSID_WHITELIST);
                mRunDecisionExplanation += scr.explanation;
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    LogV("decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
                    return true;
                }
            }
        }

        // Run in flight mode.
        if (prefRunInFlightMode && isFlightMode()) {
            LogV("decideShouldRun: prefRunInFlightMode && isFlightMode");
            mRunDecisionExplanation += "\n" + res.getString(R.string.reason_on_flight_mode);
            return true;
        }

        /**
         * If none of the above run conditions matched, don't run.
         */
        LogV("decideShouldRun: return false");
        return false;
    }

    /**
     * Check if an object's individual sync conditions are met.
     * Precondition: Object must own pref "...CustomSyncConditionsEnabled == true".
     */
    public Boolean checkObjectSyncConditions(String objectPrefixAndId) {
        // Sync on specific power source?
        SyncConditionResult scr = checkConditionSyncOnPowerSource(Constants.DYN_PREF_OBJECT_SYNC_ON_POWER_SOURCE(objectPrefixAndId));
        if (!scr.conditionMet) {
            LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnPowerSource");
            return false;
        }

        // Sync on mobile data?
        scr = checkConditionSyncOnMobileData(Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(objectPrefixAndId));
        if (scr.conditionMet) {
            // Mobile data is connected.
            LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnMobileData");
            return true;
        }

        // Sync on WiFi?
        scr = checkConditionSyncOnWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(objectPrefixAndId));
        if (scr.conditionMet) {
            // Wifi is connected.
            LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(objectPrefixAndId));
            if (scr.conditionMet) {
                // Wifi type is allowed.
                LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(
                    Constants.DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(objectPrefixAndId),
                    Constants.DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(objectPrefixAndId)
                );
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
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
            LogV("handleWifiWhitelist: !prefWifiWhitelistEnabled");
            return true;
        }
        if (isWifiConnectionWhitelisted(whitelistedWifiSsids)) {
            LogV("handleWifiWhitelist: isWifiConnectionWhitelisted");
            return true;
        }
        return false;
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isCharging_API17() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    private boolean isPowerSaving() {
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
        if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
            /**
             * We treat Wi-Fi and ETHERNET as "Wi-Fi" connection.
             * Assume ETHERNET connection is un-metered to allow syncing on
             * Android TV or VirtualBox ETHERNET connection.
             */
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

    private boolean isRoamingNetworkConnection() {
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
        return ni.isRoaming();
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
        // DO NOT RELEASE WITH THIS LINE: Log.v(TAG, "isWifiConnectionWhitelisted: wifiSsid=[" + wifiSsid + "]");
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

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
