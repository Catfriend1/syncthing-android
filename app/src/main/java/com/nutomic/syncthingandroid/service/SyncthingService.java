package com.nutomic.syncthingandroid.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.PRNGFixes;
import com.annimon.stream.Stream;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import javax.inject.Inject;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    /**
     * Intent action to perform a Syncthing restart.
     */
    public static final String ACTION_RESTART =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESTART";

    /**
     * Intent action to perform a Syncthing stop.
     */
    public static final String ACTION_STOP =
            "com.nutomic.syncthingandroid.service.SyncthingService.STOP";

    /**
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET_DATABASE =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET_DATABASE";

    /**
     * Intent action to reset Syncthing's delta indexes.
     */
    public static final String ACTION_RESET_DELTAS =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET_DELTAS";

    public static final String ACTION_REFRESH_NETWORK_INFO =
            "com.nutomic.syncthingandroid.service.SyncthingService.REFRESH_NETWORK_INFO";

    /**
     * Intent action to permanently ignore a device connection request.
     */
    public static final String ACTION_IGNORE_DEVICE =
            "com.nutomic.syncthingandroid.service.SyncthingService.IGNORE_DEVICE";

    /**
     * Intent action to permanently ignore a folder share request.
     */
    public static final String ACTION_IGNORE_FOLDER =
            "com.nutomic.syncthingandroid.service.SyncthingService.IGNORE_FOLDER";

    /**
     * Intent action to override folder changes.
     */
    public static final String ACTION_OVERRIDE_CHANGES =
            "com.nutomic.syncthingandroid.service.SyncthingService.OVERRIDE_CHANGES";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE, ACTION_IGNORE_FOLDER.
     */
    public static final String EXTRA_NOTIFICATION_ID =
            "com.nutomic.syncthingandroid.service.SyncthingService.EXTRA_NOTIFICATION_ID";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE
     */
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.service.SyncthingService.EXTRA_DEVICE_ID";

    /**
     * Extra used together with ACTION_IGNORE_FOLDER
     */
    public static final String EXTRA_FOLDER_ID =
            "com.nutomic.syncthingandroid.service.SyncthingService.EXTRA_FOLDER_ID";

    public interface OnSyncthingKilled {
        void onKilled();
    }

    public interface OnServiceStateChangeListener {
        void onServiceStateChange(State currentState);
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    public enum State {
        /** Service is initializing, Syncthing was not started yet. */
        INIT,
        /** Syncthing binary is starting. */
        STARTING,
        /** Syncthing binary is running,
         * Rest API is available,
         * RestApi class read the config and is fully initialized.
         */
        ACTIVE,
        /** Syncthing binary is shutting down. */
        DISABLED,
        /** There is some problem that prevents Syncthing from running. */
        ERROR,
    }

    /**
     * Initialize the service with State.DISABLED as {@link RunConditionMonitor} will
     * send an update if we should run the binary after it got instantiated in
     * {@link onStartCommand}.
     */
    private State mCurrentState = State.DISABLED;

    private ConfigXml mConfig;
    private @Nullable PollWebGuiAvailableTask mPollWebGuiAvailableTask = null;
    private @Nullable RestApi mApi = null;
    private @Nullable EventProcessor mEventProcessor = null;
    private @Nullable RunConditionMonitor mRunConditionMonitor = null;
    private @Nullable SyncthingRunnable mSyncthingRunnable = null;
    private StartupTask mStartupTask = null;
    private Thread mSyncthingRunnableThread = null;
    private Handler mHandler;

    private final HashSet<OnServiceStateChangeListener> mOnServiceStateChangeListeners = new HashSet<>();
    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    @Inject NotificationHandler mNotificationHandler;
    @Inject SharedPreferences mPreferences;

    /**
     * Object that must be locked upon accessing mCurrentState
     */
    private final Object mStateLock = new Object();

    /**
     * Stores the result of the last should run decision received by OnDeviceStateChangedListener.
     */
    private boolean mLastDeterminedShouldRun = false;

    /**
     * True if a service {@link onDestroy} was requested while syncthing is starting,
     * in that case, perform stop in {@link onApiAvailable}.
     */
    private boolean mDestroyScheduled = false;

    /**
     * True if the user granted the storage permission.
     */
    private boolean mStoragePermissionGranted = false;

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");
        super.onCreate();
        PRNGFixes.apply();
        ((SyncthingApp) getApplication()).component().inject(this);
        mHandler = new Handler();

        /**
         * If runtime permissions are revoked, android kills and restarts the service.
         * see issue: https://github.com/syncthing/syncthing-android/issues/871
         * We need to recheck if we still have the storage permission.
         */
        mStoragePermissionGranted = (ContextCompat.checkSelfPermission(this,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                                        PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Handles intent actions, e.g. {@link #ACTION_RESTART}
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        if (!mStoragePermissionGranted) {
            Log.e(TAG, "User revoked storage permission. Stopping service.");
            if (mNotificationHandler != null) {
                mNotificationHandler.showStoragePermissionRevokedNotification();
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        /**
         * Send current service state to listening endpoints.
         * This is required that components know about the service State.DISABLED
         * if RunConditionMonitor does not send a "shouldRun = true" callback
         * to start the binary according to preferences shortly after its creation.
         * See {@link mLastDeterminedShouldRun} defaulting to "false".
         */
        if (mCurrentState == State.DISABLED) {
            synchronized(mStateLock) {
                onServiceStateChange(mCurrentState);
            }
        }
        if (mRunConditionMonitor == null) {
            /**
             * Instantiate the run condition monitor on first onStartCommand and
             * enable callback on run condition change affecting the final decision to
             * run/terminate syncthing. After initial run conditions are collected
             * the first decision is sent to {@link onUpdatedShouldRunDecision}.
             */
            mRunConditionMonitor = new RunConditionMonitor(SyncthingService.this, this::onUpdatedShouldRunDecision);
        }
        mNotificationHandler.updatePersistentNotification(this);

        if (intent == null) {
            return START_STICKY;
        }

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.INIT, () -> launchStartupTask(SyncthingRunnable.Command.main));
        } else if (ACTION_STOP.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.DISABLED, () -> {});
        } else if (ACTION_RESET_DATABASE.equals(intent.getAction())) {
            Log.i(TAG, "Invoking reset of database");
            shutdown(State.INIT, () -> {
                new SyncthingRunnable(this, SyncthingRunnable.Command.resetdatabase).run();
                launchStartupTask(SyncthingRunnable.Command.main);
            });
        } else if (ACTION_RESET_DELTAS.equals(intent.getAction())) {
            Log.i(TAG, "Invoking reset of delta indexes");
            shutdown(State.INIT, () -> {
                launchStartupTask(SyncthingRunnable.Command.resetdeltas);
            });
        } else if (ACTION_REFRESH_NETWORK_INFO.equals(intent.getAction())) {
            mRunConditionMonitor.updateShouldRunDecision();
        } else if (ACTION_IGNORE_DEVICE.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            // mApi is not null due to State.ACTIVE
            mApi.ignoreDevice(intent.getStringExtra(EXTRA_DEVICE_ID));
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_IGNORE_FOLDER.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            // mApi is not null due to State.ACTIVE
            mApi.ignoreFolder(intent.getStringExtra(EXTRA_FOLDER_ID));
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_OVERRIDE_CHANGES.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            mApi.overrideChanges(intent.getStringExtra(EXTRA_FOLDER_ID));
        }
        return START_STICKY;
    }

    /**
     * After run conditions monitored by {@link RunConditionMonitor} changed and
     * it had an influence on the decision to run/terminate syncthing, this
     * function is called to notify this class to run/terminate the syncthing binary.
     * {@link #onServiceStateChange} is called while applying the decision change.
     */
    private void onUpdatedShouldRunDecision(boolean newShouldRunDecision) {
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
                Log.v(TAG, "Stopping syncthing");
                shutdown(State.DISABLED, () -> {});
            }
        }
    }

    /**
     * Prepares to launch the syncthing binary.
     */
    private void launchStartupTask (SyncthingRunnable.Command srCommand) {
        Log.v(TAG, "Starting syncthing");
        synchronized(mStateLock) {
            if (mCurrentState != State.DISABLED && mCurrentState != State.INIT) {
                Log.e(TAG, "launchStartupTask: Wrong state " + mCurrentState + " detected. Cancelling.");
                return;
            }
        }

        // Safety check: Log warning if a previously launched startup task did not finish properly.
        if (mStartupTask != null && (mStartupTask.getStatus() == AsyncTask.Status.RUNNING)) {
            Log.w(TAG, "launchStartupTask: StartupTask is still running. Skipped starting it twice.");
            return;
        }
        onServiceStateChange(State.STARTING);
        mStartupTask = new StartupTask(this, srCommand);
        mStartupTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Sets up the initial configuration, and updates the config when coming from an old
     * version.
     */
     private static class StartupTask extends AsyncTask<Void, Void, Void> {
         private WeakReference<SyncthingService> refSyncthingService;
         private SyncthingRunnable.Command srCommand;

         StartupTask(SyncthingService context, SyncthingRunnable.Command srCommand) {
             refSyncthingService = new WeakReference<>(context);
             this.srCommand = srCommand;
         }

         @Override
         protected Void doInBackground(Void... voids) {
             SyncthingService syncthingService = refSyncthingService.get();
             if (syncthingService == null) {
                 cancel(true);
                 return null;
             }
             try {
                 syncthingService.mConfig = new ConfigXml(syncthingService);
                 syncthingService.mConfig.updateIfNeeded();
             } catch (ConfigXml.OpenConfigException e) {
                 syncthingService.mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "ConfigXml.OpenConfigException");
                 synchronized (syncthingService.mStateLock) {
                     syncthingService.onServiceStateChange(State.ERROR);
                 }
                 cancel(true);
             }
             return null;
         }

         @Override
         protected void onPostExecute(Void aVoid) {
             // Get a reference to the service if it is still there.
             SyncthingService syncthingService = refSyncthingService.get();
             if (syncthingService != null) {
                 syncthingService.onStartupTaskCompleteListener(srCommand);
             }
         }
     }

     /**
      * Callback on {@link StartupTask#onPostExecute}.
      */
     private void onStartupTaskCompleteListener(SyncthingRunnable.Command srCommand) {
         if (mApi == null) {
             mApi = new RestApi(this, mConfig.getWebGuiUrl(), mConfig.getApiKey(),
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
         mSyncthingRunnableThread = new Thread(mSyncthingRunnable);
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
                this, getWebGuiUrl(), mConfig.getApiKey(), result -> {
                    Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
                    if (mApi != null) {
                        mApi.readConfigFromRestApi();
                    }
                }
             );
         }
     }

    /**
     * Called when {@link RestApi#checkReadConfigFromRestApiCompleted} detects
     * the RestApi class has been fully initialized.
     * UI stressing results in mApi getting null on simultaneous shutdown, so
     * we check it for safety.
     */
    private void onApiAvailable() {
        if (mApi == null) {
            Log.e(TAG, "onApiAvailable: Did we stop the binary during startup? mApi == null");
            return;
        }
        synchronized (mStateLock) {
            if (mCurrentState != State.STARTING) {
                Log.e(TAG, "onApiAvailable: Wrong state " + mCurrentState + " detected. Cancelling callback.");
                return;
            }
            onServiceStateChange(State.ACTIVE);
        }

        /**
         * If the service instance got an onDestroy() event while being in
         * State.STARTING we'll trigger the service onDestroy() now. this
         * allows the syncthing binary to get gracefully stopped.
         */
        if (mDestroyScheduled) {
            mDestroyScheduled = false;
            stopSelf();
            return;
        }

        if (mEventProcessor == null) {
            mEventProcessor = new EventProcessor(SyncthingService.this, mApi);
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
        Log.v(TAG, "onDestroy");
        if (mRunConditionMonitor != null) {
            /**
             * Shut down the OnDeviceStateChangedListener so we won't get interrupted by run
             * condition events that occur during shutdown.
             */
            mRunConditionMonitor.shutdown();
        }
        if (mStoragePermissionGranted) {
            synchronized (mStateLock) {
                if (mCurrentState == State.STARTING) {
                    Log.i(TAG, "Delay shutting down synchting binary until initialisation finished");
                    mDestroyScheduled = true;
                } else {
                    Log.i(TAG, "Shutting down syncthing binary immediately");
                    shutdown(State.DISABLED, () -> {});
                }
            }
        } else {
            // If the storage permission got revoked, we did not start the binary and
            // are in State.INIT requiring an immediate shutdown of this service class.
            Log.i(TAG, "Shutting down syncthing binary due to missing storage permission.");
            shutdown(State.DISABLED, () -> {});
        }
        super.onDestroy();
    }

    /**
     * Stop Syncthing and all helpers like event processor and api handler.
     *
     * Sets {@link #mCurrentState} to newState, and calls onKilledListener once Syncthing is killed.
     */
    private void shutdown(State newState, OnSyncthingKilled onKilledListener) {
        if (mCurrentState == State.STARTING) {
            Log.w(TAG, "Deferring shutdown until State.STARTING was left");
            mHandler.postDelayed(() -> {
                shutdown(newState, onKilledListener);
            }, 1000);
            return;
        }

        Log.i(TAG, "Shutting down");
        synchronized(mStateLock) {
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

        if (mApi != null) {
            mApi.shutdown();
            mApi = null;
        }

        if (mNotificationHandler != null) {
            mNotificationHandler.cancelPersistentNotification(this);
        }

        if (mSyncthingRunnable != null) {
            mSyncthingRunnable.killSyncthing();
            if (mSyncthingRunnableThread != null) {
                Log.v(TAG, "Waiting for mSyncthingRunnableThread to finish after killSyncthing ...");
                try {
                    mSyncthingRunnableThread.join();
                } catch (InterruptedException e) {
                    Log.w(TAG, "mSyncthingRunnableThread InterruptedException");
                }
                Log.v(TAG, "Finished mSyncthingRunnableThread.");
                mSyncthingRunnableThread = null;
            }
            mSyncthingRunnable = null;
        }
        onKilledListener.onKilled();
    }

    public @Nullable RestApi getApi() {
        return mApi;
    }

    /**
     * Force re-evaluating run conditions immediately e.g. after
     * preferences were modified by {@link SettingsActivity}.
     */
    public void evaluateRunConditions() {
        if (mRunConditionMonitor == null) {
            return;
        }
        Log.v(TAG, "Forced re-evaluating run conditions ...");
        mRunConditionMonitor.updateShouldRunDecision();
    }

    /**
     * Register a listener for the syncthing API state changing.
     *
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see #unregisterOnServiceStateChangeListener
     */
    public void registerOnServiceStateChangeListener(OnServiceStateChangeListener listener) {
        // Make sure we don't send an invalid state or syncthing might show a "disabled" message
        // when it's just starting up.
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
     * Called to notifiy listeners of an API change.
     */
    private void onServiceStateChange(State newState) {
        Log.v(TAG, "onServiceStateChange: from " + mCurrentState + " to " + newState);
        mCurrentState = newState;
        mHandler.post(() -> {
            mNotificationHandler.updatePersistentNotification(this);
            for (Iterator<OnServiceStateChangeListener> i = mOnServiceStateChangeListeners.iterator();
                 i.hasNext(); ) {
                OnServiceStateChangeListener listener = i.next();
                if (listener != null) {
                    listener.onServiceStateChange(mCurrentState);
                } else {
                    i.remove();
                }
            }
        });
    }

    public URL getWebGuiUrl() {
        return mConfig.getWebGuiUrl();
    }

    public State getCurrentState() {
        return mCurrentState;
    }

    public NotificationHandler getNotificationHandler() {
        return mNotificationHandler;
    }

    public String getRunDecisionExplanation() {
        if (mRunConditionMonitor == null) {
            return "This should not happen: mRunConditionMonitor is not instantiated.";
        }
        return mRunConditionMonitor.getRunDecisionExplanation();
    }

    /**
     * Exports the local config and keys to {@link Constants#EXPORT_PATH}.
     */
    public void exportConfig() {
        Constants.EXPORT_PATH.mkdirs();
        try {
            Files.copy(Constants.getConfigFile(this),
                    new File(Constants.EXPORT_PATH, Constants.CONFIG_FILE));
            Files.copy(Constants.getPrivateKeyFile(this),
                    new File(Constants.EXPORT_PATH, Constants.PRIVATE_KEY_FILE));
            Files.copy(Constants.getPublicKeyFile(this),
                    new File(Constants.EXPORT_PATH, Constants.PUBLIC_KEY_FILE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to export config", e);
        }
    }

    /**
     * Imports config and keys from {@link Constants#EXPORT_PATH}.
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        File config = new File(Constants.EXPORT_PATH, Constants.CONFIG_FILE);
        File privateKey = new File(Constants.EXPORT_PATH, Constants.PRIVATE_KEY_FILE);
        File publicKey = new File(Constants.EXPORT_PATH, Constants.PUBLIC_KEY_FILE);
        if (!config.exists() || !privateKey.exists() || !publicKey.exists())
            return false;
        shutdown(State.INIT, () -> {
            try {
                Files.copy(config, Constants.getConfigFile(this));
                Files.copy(privateKey, Constants.getPrivateKeyFile(this));
                Files.copy(publicKey, Constants.getPublicKeyFile(this));
            } catch (IOException e) {
                Log.w(TAG, "Failed to import config", e);
            }
            launchStartupTask(SyncthingRunnable.Command.main);
        });
        return true;
    }
}
