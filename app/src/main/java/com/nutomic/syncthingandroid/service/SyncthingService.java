package com.nutomic.syncthingandroid.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.PermissionUtil;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.model.enums.AesKeyStrength;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Intent action to perform a Syncthing restart.
     */
    public static final String ACTION_RESTART =
            ".SyncthingService.RESTART";

    /**
     * Intent action to perform a Syncthing stop.
     */
    public static final String ACTION_STOP =
            ".SyncthingService.STOP";

    /**
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET_DATABASE =
            ".SyncthingService.RESET_DATABASE";

    /**
     * Intent action to reset Syncthing's delta indexes.
     */
    public static final String ACTION_RESET_DELTAS =
            ".SyncthingService.RESET_DELTAS";

    public static final String ACTION_REFRESH_NETWORK_INFO =
            ".SyncthingService.REFRESH_NETWORK_INFO";

    /**
     * Intent action to permanently ignore a device connection request.
     */
    public static final String ACTION_IGNORE_DEVICE =
            ".SyncthingService.IGNORE_DEVICE";

    /**
     * Intent action to permanently ignore a folder share request.
     */
    public static final String ACTION_IGNORE_FOLDER =
            ".SyncthingService.IGNORE_FOLDER";

    /**
     * Intent action to override folder changes.
     */
    public static final String ACTION_OVERRIDE_CHANGES =
            ".SyncthingService.OVERRIDE_CHANGES";

    /**
     * Intent action to revert local folder changes.
     */
    public static final String ACTION_REVERT_LOCAL_CHANGES =
            ".SyncthingService.REVERT_LOCAL_CHANGES";


    /**
     * Extra used together with ACTION_IGNORE_DEVICE, ACTION_IGNORE_FOLDER.
     */
    public static final String EXTRA_NOTIFICATION_ID =
            ".SyncthingService.EXTRA_NOTIFICATION_ID";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE
     */
    public static final String EXTRA_DEVICE_ID =
            ".SyncthingService.EXTRA_DEVICE_ID";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE
     */
    public static final String EXTRA_DEVICE_ADDRESS =
            ".SyncthingService.EXTRA_DEVICE_ADDRESS";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE
     */
    public static final String EXTRA_DEVICE_NAME =
            ".SyncthingService.EXTRA_DEVICE_NAME";

    /**
     * Extra used together with ACTION_IGNORE_FOLDER
     */
    public static final String EXTRA_FOLDER_ID =
            ".SyncthingService.EXTRA_FOLDER_ID";

    /**
     * Extra used together with ACTION_IGNORE_FOLDER
     */
    public static final String EXTRA_FOLDER_LABEL =
            ".SyncthingService.EXTRA_FOLDER_LABEL";

    /**
     * Extra used together with ACTION_STOP.
     */
    public static final String EXTRA_STOP_AFTER_CRASHED_NATIVE =
            ".SyncthingService.EXTRA_STOP_AFTER_CRASHED_NATIVE";

    public interface OnServiceStateChangeListener {
        void onServiceStateChange(State currentState);
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    public enum State {
        /**
         * Service is initializing, Syncthing was not started yet.
         */
        INIT,
        /**
         * Syncthing binary is starting.
         */
        STARTING,
        /**
         * Syncthing binary is running,
         * Rest API is available,
         * RestApi class read the config and is fully initialized.
         */
        ACTIVE,
        /**
         * Syncthing binary is shutting down.
         */
        DISABLED,
        /**
         * There is some problem that prevents Syncthing from running.
         */
        ERROR,
    }

    /**
     * Initialize the service with State.DISABLED as {@link RunConditionMonitor} will
     * send an update if we should run the binary after it got instantiated in
     * {@link #onStartCommand}.
     */
    private State mCurrentState = State.DISABLED;
    private ConfigRouter mConfigRouter;
    private ConfigXml mConfig;
    private Thread mSyncthingRunnableThread = null;
    private Handler mHandler;

    private final HashSet<OnServiceStateChangeListener> mOnServiceStateChangeListeners = new HashSet<>();
    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    private @Nullable
    PollWebGuiAvailableTask mPollWebGuiAvailableTask = null;

    private @Nullable
    RestApi mRestApi = null;

    private @Nullable
    EventProcessor mEventProcessor = null;

    private @Nullable
    RunConditionMonitor mRunConditionMonitor = null;

    private @Nullable
    SyncthingRunnable mSyncthingRunnable = null;

    @Inject
    NotificationHandler mNotificationHandler;

    @Inject
    SharedPreferences mPreferences;

    /**
     * Object that must be locked upon accessing mCurrentState
     */
    private final Object mStateLock = new Object();

    /**
     * Stores the result of the last should run decision received by OnShouldRunChangedListener.
     */
    private boolean mLastDeterminedShouldRun = false;

    /**
     * True if the user granted the storage permission.
     */
    private boolean mStoragePermissionGranted = false;

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        ((SyncthingApp) getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("onCreate");
        mConfigRouter = new ConfigRouter(SyncthingService.this);
        mHandler = new Handler();

        /**
         * If runtime permissions are revoked, android kills and restarts the service.
         * We need to recheck if we still have the storage permission.
         */
        mStoragePermissionGranted = PermissionUtil.haveStoragePermission(this);

        if (mNotificationHandler != null) {
            mNotificationHandler.setAppShutdownInProgress(false);
        }
    }

    /**
     * Handles intent actions, e.g. {@link #ACTION_RESTART}
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (!mStoragePermissionGranted) {
            Log.e(TAG, "User revoked storage permission. Stopping service.");
            if (mNotificationHandler != null) {
                mNotificationHandler.showStoragePermissionRevokedNotification();
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        // Run condition monitor is enabled.
        if (mRunConditionMonitor == null) {
            /**
             * Instantiate the run condition monitor on first onStartCommand and
             * enable callback on run condition change affecting the final decision to
             * run/terminate syncthing. After initial run conditions are collected
             * the first decision is sent to {@link onShouldRunDecisionChanged}.
             */
            mRunConditionMonitor = new RunConditionMonitor(SyncthingService.this,
                this::onShouldRunDecisionChanged,
                this::applyCustomRunConditions
            );
        }
        mNotificationHandler.updatePersistentNotification(this);

        if (intent == null) {
            return START_STICKY;
        }

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.INIT);
            launchStartupTask(SyncthingRunnable.Command.main);
        } else if (ACTION_STOP.equals(intent.getAction())) {
            if (intent.getBooleanExtra(EXTRA_STOP_AFTER_CRASHED_NATIVE, false)) {
                /**
                 * We were requested to stop the service because the syncthing native binary crashed.
                 * Changing mCurrentState prevents the "defer until syncthing is started" routine we normally
                 * use for clean shutdown to take place. Instead, we will immediately shutdown the crashed
                 * instance forcefully.
                 */
                mCurrentState = State.ERROR;
                shutdown(State.DISABLED);
            } else {
                // Graceful shutdown.
                if (mCurrentState == State.STARTING ||
                        mCurrentState == State.ACTIVE) {
                    shutdown(State.DISABLED);
                }
            }
        } else if (ACTION_RESET_DATABASE.equals(intent.getAction())) {
            /**
             * 1. Stop syncthing native if it's running.
             * 2. Reset the database, syncthing native will exit after performing the reset.
             * 3. Relaunch syncthing native if it was previously running.
             */
            Log.i(TAG, "Invoking reset of database");
            if (mCurrentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdown(State.DISABLED);
            }
            new SyncthingRunnable(this, SyncthingRunnable.Command.resetdatabase).run();
            if (mLastDeterminedShouldRun) {
                launchStartupTask(SyncthingRunnable.Command.main);
            }
        } else if (ACTION_RESET_DELTAS.equals(intent.getAction())) {
            /**
             * 1. Stop syncthing native if it's running.
             * 2. Reset delta index, syncthing native will NOT exit after performing the reset.
             * 3. If syncthing was previously NOT running:
             * 3.1  Schedule a shutdown of the native binary after it left State.STARTING (to State.ACTIVE).
             *      This is the moment, when the reset delta index work was completed and Web UI came up.
             * 3.2  The shutdown gets deferred until State.ACTIVE was reached and then syncthing native will
             *      be shutdown synchronously.
             */
            Log.i(TAG, "Invoking reset of delta indexes");
            if (mCurrentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdown(State.DISABLED);
            }
            launchStartupTask(SyncthingRunnable.Command.resetdeltas);
            if (!mLastDeterminedShouldRun) {
                // Shutdown if syncthing was not running before the UI action was raised.
                shutdown(State.DISABLED);
            }
        } else if (ACTION_REFRESH_NETWORK_INFO.equals(intent.getAction())) {
            if (mRunConditionMonitor != null) {
                mRunConditionMonitor.updateShouldRunDecision();
            }
        } else if (ACTION_IGNORE_DEVICE.equals(intent.getAction())) {
            mConfigRouter.ignoreDevice(
                    mRestApi,
                    intent.getStringExtra(EXTRA_DEVICE_ID),
                    intent.getStringExtra(EXTRA_DEVICE_NAME),
                    intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
            );
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_IGNORE_FOLDER.equals(intent.getAction())) {
            mConfigRouter.ignoreFolder(
                    mRestApi,
                    intent.getStringExtra(EXTRA_DEVICE_ID),
                    intent.getStringExtra(EXTRA_FOLDER_ID),
                    intent.getStringExtra(EXTRA_FOLDER_LABEL)
            );
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_OVERRIDE_CHANGES.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            mRestApi.overrideChanges(intent.getStringExtra(EXTRA_FOLDER_ID));
        } else if (ACTION_REVERT_LOCAL_CHANGES.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            mRestApi.revertLocalChanges(intent.getStringExtra(EXTRA_FOLDER_ID));
        } else {
            afterFreshServiceInstanceStart();
        }
        return START_STICKY;
    }

    /**
     * Event handler ot catch a fresh service startup right after the run condition evaluation took place
     * and SyncthingNative may be starting in the background meanwhilst or non-present.
     */
    private void afterFreshServiceInstanceStart() {
        LogV("afterFreshServiceInstanceStart: Service started from scratch, SyncthingNative is going to STATE_" + mCurrentState + " meanwhilst ...");
        if (mCurrentState == State.DISABLED) {
            // Read and parse the config from disk.
            ConfigXml configXml = new ConfigXml(this);
            try {
                configXml.loadConfig();
            } catch (ConfigXml.OpenConfigException e) {
                mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "afterFreshServiceInstanceStart:OpenConfigException");
                synchronized (mStateLock) {
                    onServiceStateChange(State.ERROR);
                }
                stopSelf();
                return;
            }
        }
    }

    /**
     * After run conditions monitored by {@link RunConditionMonitor} changed and
     * it had an influence on the decision to run/terminate syncthing, this
     * function is called to notify this class to run/terminate the syncthing binary.
     * {@link #onServiceStateChange} is called while applying the decision change.
     */
    private void onShouldRunDecisionChanged(boolean newShouldRunDecision) {
        if (newShouldRunDecision != mLastDeterminedShouldRun) {
            Log.i(TAG, "shouldRun decision changed to " + newShouldRunDecision + " according to configured run conditions.");
            mLastDeterminedShouldRun = newShouldRunDecision;

            // React to the shouldRun condition change.
            if (newShouldRunDecision) {
                // Start syncthing.
                switch (mCurrentState) {
                    case DISABLED:
                    case INIT:
                        launchStartupTask(SyncthingRunnable.Command.main);
                        break;
                    case STARTING:
                    case ACTIVE:
                    case ERROR:
                        break;
                    default:
                        break;
                }
            } else {
                // Stop syncthing.
                if (mCurrentState == State.DISABLED) {
                    return;
                }
                shutdown(State.DISABLED);
            }
        }
    }

    /**
     * After sync preconditions changed, we need to inform {@link RestApi} to pause or
     * unpause devices and folders as defined in per-object sync preferences.
     */
    private void applyCustomRunConditions(RunConditionMonitor runConditionMonitor) {
        synchronized (mStateLock) {
            if (mRestApi != null && mCurrentState == State.ACTIVE) {
                // Forward event because syncthing is running.
                mRestApi.applyCustomRunConditions(runConditionMonitor);
                return;
            }
        }

        Boolean configChanged = false;
        ConfigXml configXml;

        // Read and parse the config from disk.
        configXml = new ConfigXml(this);
        try {
            configXml.loadConfig();
        } catch (ConfigXml.OpenConfigException e) {
            mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "applyCustomRunConditions:OpenConfigException");
            synchronized (mStateLock) {
                onServiceStateChange(State.ERROR);
            }
            stopSelf();
            return;
        }

        // Check if the folders are available from config.
        List<Folder> folders = configXml.getFolders();
        if (folders != null) {
            for (Folder folder : folders) {
                // LogV("applyCustomRunConditions: Processing config of folder(" + folder.label + ")");
                Boolean folderCustomSyncConditionsEnabled = mPreferences.getBoolean(
                    Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id), false
                );
                if (folderCustomSyncConditionsEnabled) {
                    Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                        Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id
                    );
                    LogV("applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? "1" : "0"));
                    if (folder.paused != !syncConditionsMet) {
                        configXml.setFolderPause(folder.id, !syncConditionsMet);
                        Log.d(TAG, "applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? ">1" : ">0"));
                        configChanged = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: folders == null");
            return;
        }

        // Check if the devices are available from config.
        List<Device> devices = configXml.getDevices(false);
        if (devices != null) {
            for (Device device : devices) {
                // LogV("applyCustomRunConditions: Processing config of device(" + device.name + ")");
                Boolean deviceCustomSyncConditionsEnabled = mPreferences.getBoolean(
                    Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID), false
                );
                if (deviceCustomSyncConditionsEnabled) {
                    Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                        Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID
                    );
                    LogV("applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? "1" : "0"));
                    if (device.paused != !syncConditionsMet) {
                        configXml.setDevicePause(device.deviceID, !syncConditionsMet);
                        Log.d(TAG, "applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? ">1" : ">0"));
                        configChanged = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: devices == null");
            return;
        }

        if (configChanged) {
            LogV("applyCustomRunConditions: Saving changed config ...");
            configXml.saveChanges();
        } else {
            LogV("applyCustomRunConditions: No action was necessary.");
        }
    }

    /**
     * Prepares to launch the syncthing binary.
     */
    private void launchStartupTask(SyncthingRunnable.Command srCommand) {
        synchronized (mStateLock) {
            if (mCurrentState != State.DISABLED && mCurrentState != State.INIT) {
                Log.e(TAG, "launchStartupTask: Wrong state " + mCurrentState + " detected. Cancelling.");
                return;
            }
        }

        mConfig = new ConfigXml(this);
        try {
            mConfig.loadConfig();
        } catch (ConfigXml.OpenConfigException e) {
            mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "launchStartupTask:OpenConfigException");
            synchronized (mStateLock) {
                onServiceStateChange(State.ERROR);
            }
            stopSelf();
            return;
        }

        // Check if the SyncthingNative's configured webgui port is allocated by another app or process.
        Integer webGuiTcpPort = mConfig.getWebGuiBindPort();
        Boolean isWebUIPortListening = Util.isTcpPortListening(webGuiTcpPort);
        if (isWebUIPortListening) {
            // We shouldn't start SyncthingNative as we would wait forever for life signs on the configured port. (ANR)
            Log.e(TAG, "launchStartupTask: WebUI tcp port " + Integer.toString(webGuiTcpPort) + " unavailable. Second instance?");
            mNotificationHandler.showCrashedNotification(R.string.webui_tcp_port_unavailable, Integer.toString(webGuiTcpPort));
            return;
        }

        onServiceStateChange(State.STARTING);

        if (mRestApi == null) {
            mRestApi = new RestApi(this, mConfig.getWebGuiUrl(), mConfig.getApiKey(),
                    this::onApiAvailable, () -> onServiceStateChange(mCurrentState));
            Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());
        }

        // Check mSyncthingRunnable lifecycle and create singleton.
        if (mSyncthingRunnable != null || mSyncthingRunnableThread != null) {
            Log.e(TAG, "onStartupTaskCompleteListener: Syncthing binary lifecycle violated");
            return;
        }
        mSyncthingRunnable = new SyncthingRunnable(this, srCommand);

        /**
         * Check if an old syncthing instance is still running.
         * This happens after an in-place app upgrade. If so, end it.
         */
        mSyncthingRunnable.killSyncthing();

        // Start the syncthing binary in a separate thread.
        Thread.UncaughtExceptionHandler syncthingRunnableThreadExceptionHandler = new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread syncthingRunnableThread, Throwable ex) {
                    Log.e(TAG, "mSyncthingRunnableThread: Uncaught exception [ExecutableNotFoundException]");
                    mNotificationHandler.showCrashedNotification(R.string.executable_not_found, Constants.FILENAME_SYNCTHING_BINARY);
                }
        };
        mSyncthingRunnableThread = new Thread(mSyncthingRunnable);
        mSyncthingRunnableThread.setUncaughtExceptionHandler(syncthingRunnableThreadExceptionHandler);
        mSyncthingRunnableThread.start();

        /**
         * Wait for the web-gui of the native syncthing binary to come online.
         *
         * In case the binary is to be stopped, also be aware that another thread could request
         * to stop the binary in the time while waiting for the GUI to become active. See the comment
         * for {@link SyncthingService#onDestroy} for details.
         */
        if (mPollWebGuiAvailableTask == null) {
            mPollWebGuiAvailableTask = new PollWebGuiAvailableTask(
                    this, mConfig.getWebGuiUrl(), mConfig.getApiKey(), result -> {
                Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
                if (mRestApi != null) {
                    mRestApi.readConfigFromRestApi();
                }
            }
            );
        }
    }

    /**
     * Called when {@link RestApi#checkReadConfigFromRestApiCompleted} detects
     * the RestApi class has been fully initialized.
     * UI stressing results in mRestApi getting null on simultaneous shutdown, so
     * we check it for safety.
     */
    private void onApiAvailable() {
        if (mRestApi == null) {
            Log.e(TAG, "onApiAvailable: Did we stop the binary during startup? mRestApi == null");
            return;
        }
        synchronized (mStateLock) {
            if (mCurrentState != State.STARTING) {
                Log.e(TAG, "onApiAvailable: Wrong state " + mCurrentState + " detected. Cancelling callback.");
                return;
            }
            onServiceStateChange(State.ACTIVE);
        }

        if (mEventProcessor == null) {
            mEventProcessor = new EventProcessor(SyncthingService.this, mRestApi);
            mEventProcessor.start();
        }
    }

    @Override
    public SyncthingServiceBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the native binary.
     * Shuts down RunConditionMonitor instance.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mRunConditionMonitor != null) {
            /**
             * Shut down the OnShouldRunChangedListener so we won't get interrupted by run
             * condition events that occur during shutdown.
             */
            mRunConditionMonitor.shutdown();
        }
        if (mNotificationHandler != null) {
            mNotificationHandler.setAppShutdownInProgress(true);
        }
        if (!mStoragePermissionGranted) {
            // If the storage permission got revoked, we did not start the binary and
            // are in State.INIT requiring an immediate shutdown of this service class.
            Log.i(TAG, "Shutting down syncthing binary due to missing storage permission.");
        }
        shutdown(State.DISABLED);
        super.onDestroy();
    }

    /**
     * Stop SyncthingNative and all helpers like event processor and api handler.
     * Sets {@link #mCurrentState} to newState.
     * Performs a synchronous shutdown of the native binary.
     */
    private void shutdown(State newState) {
        if (mCurrentState == State.STARTING) {
            Log.w(TAG, "Deferring shutdown until State.STARTING was left");
            mHandler.postDelayed(() -> {
                shutdown(newState);
            }, 1000);
            return;
        }

        synchronized (mStateLock) {
            onServiceStateChange(newState);
        }

        if (mPollWebGuiAvailableTask != null) {
            mPollWebGuiAvailableTask.cancelRequestsAndCallback();
            mPollWebGuiAvailableTask = null;
        }

        if (mEventProcessor != null) {
            mEventProcessor.stop();
            mEventProcessor = null;
        }

        if (mNotificationHandler != null) {
            mNotificationHandler.cancelRestartNotification();
        }

        if (mRestApi != null) {
            if (mSyncthingRunnable != null) {
                mRestApi.shutdown();
            }
            mRestApi = null;
        }

        if (mSyncthingRunnable != null) {
            mSyncthingRunnable.killSyncthing();
            if (mSyncthingRunnableThread != null) {
                LogV("Waiting for mSyncthingRunnableThread to finish after killSyncthing ...");
                try {
                    mSyncthingRunnableThread.join();
                } catch (InterruptedException e) {
                    Log.w(TAG, "mSyncthingRunnableThread InterruptedException");
                }
                Log.d(TAG, "Finished mSyncthingRunnableThread.");
                mSyncthingRunnableThread = null;
            }
            mSyncthingRunnable = null;
        }
    }

    public @Nullable
    RestApi getApi() {
        return mRestApi;
    }

    /**
     * Force re-evaluating run conditions immediately e.g. after
     * preferences were modified by {@link ../activities/SettingsActivity#onStop}.
     */
    public void evaluateRunConditions() {
        if (mRunConditionMonitor == null) {
            return;
        }
        Log.d(TAG, "Forced re-evaluating run conditions ...");
        mRunConditionMonitor.updateShouldRunDecision();
    }

    /**
     * Register a listener for the syncthing API state changing.
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see #unregisterOnServiceStateChangeListener
     */
    public void registerOnServiceStateChangeListener(OnServiceStateChangeListener listener) {
        /**
         * Initially send the current state to the new subscriber to make sure it doesn't stay
         * in undefined state forever until the state next change occurs.
         */
        listener.onServiceStateChange(mCurrentState);
        mOnServiceStateChangeListeners.add(listener);
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @see #registerOnServiceStateChangeListener
     */
    public void unregisterOnServiceStateChangeListener(OnServiceStateChangeListener listener) {
        mOnServiceStateChangeListeners.remove(listener);
    }

    /**
     * Called to notify listeners of an API change.
     */
    private void onServiceStateChange(State newState) {
        if (newState == mCurrentState) {
            Log.d(TAG, "onServiceStateChange: Called with unchanged state " + newState);
            return;
        }
        Log.i(TAG, "onServiceStateChange: from " + mCurrentState + " to " + newState);
        mCurrentState = newState;
        mHandler.post(() -> {
            mNotificationHandler.updatePersistentNotification(this);
            Iterator<OnServiceStateChangeListener> it = mOnServiceStateChangeListeners.iterator();
            while (it.hasNext()) {
                OnServiceStateChangeListener listener = it.next();
                if (listener != null) {
                    listener.onServiceStateChange(mCurrentState);
                } else {
                    it.remove();
                }
            }
        });
    }

    public State getCurrentState() {
        return mCurrentState;
    }

    public NotificationHandler getNotificationHandler() {
        return mNotificationHandler;
    }

    public String getRunDecisionExplanation() {
        if (mRunConditionMonitor != null) {
            return mRunConditionMonitor.getRunDecisionExplanation();
        }

        // mRunConditionMonitor == null
        return getResources().getString(R.string.reason_run_condition_monitor_not_instantiated);
    }

    /**
     * Get backup zip file.
     * Default: /storage/emulated0/backups/syncthing/config.zip
     */
    private final File getBackupZipFile() {
        String relPathToZip = mPreferences.getString(
                Constants.PREF_BACKUP_REL_PATH_TO_ZIP,
                "backups/syncthing/config.zip"
        );
        return new File(Environment.getExternalStorageDirectory(), relPathToZip);
    }

    /**
     * Exports the local config and keys to {@link Constants#EXPORT_PATH}.
     *
     * Test with Android Virtual Device using emulator.
     * cls & adb shell su 0 "ls -a -l -R /data/data/${applicationId}/files; echo === SDCARD ===; ls -a -l -R /storage/emulated/0/backups/syncthing"
     *
     */
    public boolean exportConfig() {
        Boolean failSuccess = true;
        Log.d(TAG, "exportConfig BEGIN");

        if (mCurrentState != State.DISABLED) {
            // Shutdown synchronously.
            shutdown(State.DISABLED);
        }

        // Create export dir if non-existant.
        File targetZip = getBackupZipFile();
        targetZip.getParentFile().mkdirs();

        // Export SharedPreferences.
        File sharedPreferencesFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            sharedPreferencesFile = Constants.getSharedPrefsFile(this);
            fileOutputStream = new FileOutputStream(sharedPreferencesFile);
            if (!sharedPreferencesFile.exists()) {
                sharedPreferencesFile.createNewFile();
            }
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(mPreferences.getAll());
            objectOutputStream.flush();
            fileOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "exportConfig: Failed to export SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectOutputStream != null) {
                    objectOutputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "exportConfig: Failed to export SharedPreferences #2", e);
            }
        }

        // Make a list of files to backup.
        List<File> includePaths = Arrays.asList(
            Constants.getConfigFile(this),

            Constants.getPrivateKeyFile(this),
            Constants.getPublicKeyFile(this),

            Constants.getHttpsCertFile(this),
            Constants.getHttpsKeyFile(this),

            Constants.getSharedPrefsFile(this),

            Constants.getIndexDbFolder(this)
        );

        // If user set one, apply a password and encrypt the zip file.
        String zipEncryptionPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");

        // Compress files to zip file.
        try {
            // Delete existing ZIP file to ensure we create a fresh archive instead of appending
            if (targetZip.exists()) {
                targetZip.delete();
            }
            
            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
            parameters.setCompressionLevel(CompressionLevel.NORMAL);

            ZipFile zipFile;
            if (zipEncryptionPassword.isEmpty()) {
                zipFile = new ZipFile(targetZip);
                parameters.setEncryptFiles(false);
            } else {
                zipFile = new ZipFile(targetZip, zipEncryptionPassword.toCharArray());
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
                parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }

            // Add files.
            for (File includePath : includePaths) {
                if (includePath.exists()) {
                    if (includePath.isFile()) {
                        zipFile.addFile(includePath, parameters);
                    } else if (includePath.isDirectory()) {
                        zipFile.addFolder(includePath, parameters);
                    }
                }
            }

            if (sharedPreferencesFile != null && sharedPreferencesFile.exists()) {
                sharedPreferencesFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "exportConfig: Failed to export config, " + e.getMessage());
            failSuccess = false;
        }
        Log.d(TAG, "exportConfig END");

        // Start syncthing after export if run conditions apply.
        if (mLastDeterminedShouldRun) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable launchStartupTaskRunnable = new Runnable() {
                @Override
                public void run() {
                    launchStartupTask(SyncthingRunnable.Command.main);
                }
            };
            mainLooper.post(launchStartupTaskRunnable);
        }
        return failSuccess;
    }

    /**
     * Imports config and keys from {@link Constants#EXPORT_PATH}.
     *
     * Test with Android Virtual Device using emulator.
     * cls & adb shell su 0 "ls -a -l -R /data/data/${applicationId}/files; echo === SDCARD ===; ls -a -l -R /storage/emulated/0/backups/syncthing"
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        ZipFile zipFile = null;
        Log.d(TAG, "importConfig PRECHECK");

        // Check if ZIP exists.
        File zipFilePath = getBackupZipFile();
        if (!zipFilePath.exists()) {
            Log.e(TAG, "importConfig: ZIP file is missing. Please check if it is present at '" + zipFilePath.getAbsolutePath() + "' as specified in the settings screen.");
            return false;
        }

        // Open ZIP file.
        try {
            // If user set one, get password to decrypt the zip file.
            String zipEncryptionPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");
            if (zipEncryptionPassword.isEmpty()) {
                zipFile = new ZipFile(zipFilePath);
            } else {
                zipFile = new ZipFile(zipFilePath, zipEncryptionPassword.toCharArray());
                if (!zipFile.isEncrypted()) {
                    Log.e(TAG, "importConfig: ZIP file is not encrypted, but password was specified in settings screen. Try to specify an empty password temporarily.");
                    return false;
                }
            }

            // Check if ZIP archive contains required files.
            List<String> checkFiles = Arrays.asList(
                Constants.CONFIG_FILE,

                Constants.PRIVATE_KEY_FILE,
                Constants.PUBLIC_KEY_FILE
            );
            for (final String checkFile : checkFiles) {
                if (zipFile.getFileHeader(checkFile) == null) {
                    Log.e(TAG, "importConfig: Required file not found inside zip [" + checkFile + "]");
                    return false;
                }
            }

            // Test if supplied encryption password is correct.
            String cacheDir = this.getCacheDir().getAbsolutePath();
            zipFile.extractFile(Constants.PUBLIC_KEY_FILE, cacheDir);
            new File(cacheDir, Constants.PUBLIC_KEY_FILE).delete();
        } catch (ZipException e) {
            Log.e(TAG, "importConfig: Failed to open zip, " + e.getMessage());
            return false;
        }

        // Shutdown SyncthingNative.
        Boolean failSuccess = true;
        Log.d(TAG, "importConfig BEGIN");
        if (mCurrentState != State.DISABLED) {
            // Shutdown synchronously.
            shutdown(State.DISABLED);
        }

        // Remove database folder if it exists.
        File databasePath = Constants.getIndexDbFolder(this);
        if (databasePath.exists()) {
            Log.d(TAG, "importConfig: Clearing index database");
            try {
                FileUtils.deleteDirectoryRecursively(databasePath);
            } catch (IOException e) {
                Log.e(TAG, "Failed to delete directory '" + databasePath.getAbsolutePath() + "'" + e);
            }
        }

        // Decompress zip file.
        try {
            zipFile.extractAll(this.getFilesDir().getAbsolutePath());
        } catch (ZipException e) {
            Log.e(TAG, "importConfig: Failed to extract zip, " + e.getMessage());
            failSuccess = false;
        }

        // Check if necessary files are present after extraction.
        List<File> checkPaths = Arrays.asList(
            Constants.getConfigFile(this),

            Constants.getPrivateKeyFile(this),
            Constants.getPublicKeyFile(this),

            Constants.getHttpsCertFile(this),
            Constants.getHttpsKeyFile(this),

            Constants.getSharedPrefsFile(this)
        );
        for (final File checkPath : checkPaths) {
            if (!checkPath.exists()) {
                Log.e(TAG, "importConfig: Missing file after extraction [" + checkPath.getName() + "]");
                failSuccess = false;
            }
        }
        
        // Import shared preferences.
        File sharedPreferencesFile = Constants.getSharedPrefsFile(this);
        if (sharedPreferencesFile.exists()) {
            Log.d(TAG, "importConfig: Importing shared preferences");
            failSuccess = failSuccess && importConfigSharedPrefs(sharedPreferencesFile);
            sharedPreferencesFile.delete();
        }
        Log.d(TAG, "importConfig END");

        // Start syncthing after import if run conditions apply.
        if (mLastDeterminedShouldRun) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable launchStartupTaskRunnable = new Runnable() {
                @Override
                public void run() {
                    launchStartupTask(SyncthingRunnable.Command.main);
                }
            };
            mainLooper.post(launchStartupTaskRunnable);
        }
        return failSuccess;
    }

    private boolean importConfigSharedPrefs(final File file) {
        Boolean failSuccess = true;
        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        Map<?, ?> sharedPrefsMap = null;
        try {
            
            // Read, deserialize shared preferences.
            fileInputStream = new FileInputStream(file);
            objectInputStream = new ObjectInputStream(fileInputStream);
            Object objectFromInputStream = objectInputStream.readObject();
            if (objectFromInputStream instanceof Map) {
                sharedPrefsMap = (Map<?, ?>) objectFromInputStream;

                // Store backup folder to restore it back later in the process.
                String relPathToZip = mPreferences.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, "");
                String backupPassword = mPreferences.getString(Constants.PREF_BACKUP_PASSWORD, "");

                // Prepare a SharedPreferences commit.
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.clear();
                for (Map.Entry<?, ?> e : sharedPrefsMap.entrySet()) {
                    String prefKey = (String) e.getKey();
                    switch (prefKey) {
                        // Preferences that are no longer used and left-overs from previous versions of the app.
                        case "first_start":
                        case "advanced_folder_picker":
                        case "backup_folder_name":
                        case "bind_network":
                        case "log_to_file":
                        case "notification_type":
                        case "notify_crashes":
                        case "suggest_new_folder_root":
                        case "use_legacy_hashing":
                        case "pref_current_language":
                        case "restartOnWakeup":
                            LogV("importConfig: Ignoring deprecated pref \"" + prefKey + "\".");
                            break;
                        // Cached information which is not available on SettingsActivity.
                        case Constants.PREF_BTNSTATE_FORCE_START_STOP:
                        case Constants.PREF_DEBUG_FACILITIES_AVAILABLE:
                        case Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID:
                        case Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION:
                        case Constants.PREF_LAST_BINARY_VERSION:
                        case Constants.PREF_LOCAL_DEVICE_ID:
                        case Constants.PREF_LAST_RUN_TIME:
                            LogV("importConfig: Ignoring cache pref \"" + prefKey + "\".");
                            break;
                        default:
                            Log.i(TAG, "importConfig: Adding pref \"" + prefKey + "\" to commit ...");

                            // The editor only provides typed setters.
                            if (e.getValue() instanceof Boolean) {
                                editor.putBoolean(prefKey, (Boolean) e.getValue());
                            } else if (e.getValue() instanceof String) {
                                editor.putString(prefKey, (String) e.getValue());
                            } else if (e.getValue() instanceof Integer) {
                                editor.putInt(prefKey, (Integer) e.getValue());
                            } else if (e.getValue() instanceof Float) {
                                editor.putFloat(prefKey, (Float) e.getValue());
                            } else if (e.getValue() instanceof Long) {
                                editor.putLong(prefKey, (Long) e.getValue());
                            } else if (e.getValue() instanceof Set) {
                                editor.putStringSet(prefKey, asSet((Set<?>) e.getValue(), String.class));
                            } else {
                                Log.w(TAG, "importConfig: SharedPref type " + e.getValue().getClass().getName() + " is unknown");
                            }
                            break;
                    }
                }
                editor.putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relPathToZip);
                editor.putString(Constants.PREF_BACKUP_PASSWORD, backupPassword);

                /**
                 * If all shared preferences have been added to the commit successfully,
                 * apply the commit.
                 */
                failSuccess = failSuccess && editor.commit();
            } else {
                Log.e(TAG, "importConfig: Invalid object stream");
            }
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "importConfig: Failed to import SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectInputStream != null) {
                    objectInputStream.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "importConfig: Failed to import SharedPreferences #2", e);
            }
        }
        return failSuccess;
    }

    public static <T> Set<T> asSet(Set<?> c, Class<? extends T> type) {
        if (c == null) {
            return null;
        }
        Set<T> set = new HashSet<T>();
        for (Object o : c) {
            set.add(type.cast(o));
        }
        return set;
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
