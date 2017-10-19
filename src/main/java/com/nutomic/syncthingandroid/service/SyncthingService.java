package com.nutomic.syncthingandroid.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;
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
import com.nutomic.syncthingandroid.util.FolderObserver;

import java.io.File;
import java.io.IOException;
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
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET";

    /**
     * Callback for when the Syncthing web interface becomes first available after service start.
     */
    public interface OnWebGuiAvailableListener {
        void onWebGuiAvailable();
    }

    private final HashSet<OnWebGuiAvailableListener> mOnWebGuiAvailableListeners =
            new HashSet<>();

    public interface OnApiChangeListener {
        void onApiChange(State currentState);
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    public enum State {
        /** Service is initializing, Syncthing was not started yet. */
        INIT,
        /** Syncthing binary is starting. */
        STARTING,
        /** Syncthing binary is running, API is available. */
        ACTIVE,
        /** Syncthing is stopped according to user preferences. */
        DISABLED,
        /** There is some problem that prevents Syncthing from running. */
        ERROR,
    }

    private State mCurrentState = State.INIT;

    private ConfigXml mConfig;
    private RestApi mApi;
    private EventProcessor mEventProcessor;
    private DeviceStateHolder mDeviceStateHolder;
    private SyncthingRunnable mSyncthingRunnable;

    private final LinkedList<FolderObserver> mObservers = new LinkedList<>();
    private final HashSet<OnApiChangeListener> mOnApiChangeListeners = new HashSet<>();
    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    @Inject NotificationHandler mNotificationHandler;
    @Inject SharedPreferences mPreferences;

    /**
     * Object that can be locked upon when accessing mCurrentState
     * Currently used to male onDestroy() and PollWebGuiAvailableTaskImpl.onPostExcecute() tread-safe
     */
    private final Object mStateLock = new Object();

    /**
     * True if a stop was requested while syncthing is starting, in that case, perform stop in
     * {@link #pollWebGui}.
     */
    private boolean mStopScheduled = false;

    /**
     * Handles intents, either {@link #ACTION_RESTART}, or intents having
     * {@link DeviceStateHolder#EXTRA_IS_ALLOWED_NETWORK_CONNECTION} or
     * {@link DeviceStateHolder#EXTRA_IS_CHARGING} (which are handled by {@link DeviceStateHolder}.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.INIT, () -> new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));
        } else if (ACTION_RESET.equals(intent.getAction())) {
            shutdown(State.INIT, () -> {
                new SyncthingRunnable(this, SyncthingRunnable.Command.reset).run();
                new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            });
        }
        return START_STICKY;
    }

    /**
     * Checks according to preferences and charging/wifi state, whether syncthing should be enabled
     * or not.
     *
     * Depending on the result, syncthing is started or stopped, and {@link #onApiChange} is
     * called.
     */
    private void updateState() {
        // Start syncthing.
        if (mDeviceStateHolder.shouldRun()) {
            if (mCurrentState == State.ACTIVE || mCurrentState == State.STARTING) {
                mStopScheduled = false;
                return;
            }

            // HACK: Make sure there is no syncthing binary left running from an improper
            // shutdown (eg Play Store update).
            shutdown(State.INIT, () -> {
                Log.i(TAG, "Starting syncthing according to current state and preferences");
                new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            });
        }
        // Stop syncthing.
        else {
            if (mCurrentState == State.DISABLED)
                return;

            Log.i(TAG, "Stopping syncthing according to current state and preferences");
            shutdown(State.DISABLED, () -> {});
        }
    }

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        PRNGFixes.apply();
        ((SyncthingApp) getApplication()).component().inject(this);

        mDeviceStateHolder = new DeviceStateHolder(SyncthingService.this, this::updateState);
        updateState();
        mNotificationHandler.updatePersistentNotification(this);
    }

    /**
     * Sets up the initial configuration, and updates the config when coming from an old
     * version.
     */
    private class StartupTask extends AsyncTask<Void, Void, Void> {

        public StartupTask() {
            onApiChange(State.STARTING);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                mConfig = new ConfigXml(SyncthingService.this);
                mConfig.updateIfNeeded();
            } catch (ConfigXml.OpenConfigException e) {
                mNotificationHandler.showCrashedNotification(R.string.config_create_failed, true);
                onApiChange(State.ERROR);
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mApi = new RestApi(SyncthingService.this, mConfig.getWebGuiUrl(), mConfig.getApiKey(),
                    SyncthingService.this::onSyncthingStarted, () -> onApiChange(mCurrentState));

            mEventProcessor = new EventProcessor(SyncthingService.this, mApi);

            if (mApi != null)
                registerOnWebGuiAvailableListener(mApi);
            if (mEventProcessor != null)
                registerOnWebGuiAvailableListener(mEventProcessor);
            Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());

            pollWebGui();
            mSyncthingRunnable = new SyncthingRunnable(SyncthingService.this, SyncthingRunnable.Command.main);
            new Thread(mSyncthingRunnable).start();
        }
    }

    private void onSyncthingStarted() {
        onApiChange(State.ACTIVE);
        new Thread(() -> {
            for (Folder r : mApi.getFolders()) {
                try {
                    mObservers.add(new FolderObserver(mApi, r));
                } catch (FolderObserver.FolderNotExistingException e) {
                    Log.w(TAG, "Failed to add observer for folder", e);
                } catch (StackOverflowError e) {
                    Log.w(TAG, "Failed to add folder observer", e);
                    Toast.makeText(SyncthingService.this,
                            R.string.toast_folder_observer_stack_overflow,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the native binary.
     *
     * The native binary crashes if stopped before it is fully active. In that case signal the
     * stop request to PollWebGuiAvailableTaskImpl that is active in that situation and terminate
     * the service there.
     */
    @Override
    public void onDestroy() {
        synchronized (mStateLock) {
            if (mCurrentState == State.INIT || mCurrentState == State.STARTING) {
                Log.i(TAG, "Delay shutting down service until initialisation of Syncthing finished");
                mStopScheduled = true;

            } else {
                Log.i(TAG, "Shutting down service immediately");
                shutdown(State.DISABLED, () -> {});
            }
        }

        mDeviceStateHolder.shutdown();
    }

    /**
     * Stop Syncthing and all helpers like event processor, api handler and folder observers.
     *
     * Sets {@link #mCurrentState} to newState, and calls onKilledListener once Syncthing is killed.
     */
    private void shutdown(State newState, SyncthingRunnable.OnSyncthingKilled onKilledListener) {
        Log.i(TAG, "Shutting down background service");
        onApiChange(newState);

        if (mEventProcessor != null)
            mEventProcessor.shutdown();

        if (mApi != null)
            mApi.shutdown();

        mNotificationHandler.cancelPersistentNotification(this);

        Stream.of(mObservers).forEach(FolderObserver::stopWatching);
        mObservers.clear();

        if (mSyncthingRunnable != null) {
            mSyncthingRunnable.killSyncthing(onKilledListener);
            mSyncthingRunnable = null;
        } else {
            onKilledListener.onKilled();
        }
    }

    /**
     * Register a listener for the web gui becoming available..
     *
     * If the web gui is already available, listener will be called immediately.
     * Listeners are unregistered automatically after being called.
     */
    public void registerOnWebGuiAvailableListener(OnWebGuiAvailableListener listener) {
        if (mCurrentState == State.ACTIVE) {
            listener.onWebGuiAvailable();
        } else {
            mOnWebGuiAvailableListeners.add(listener);
        }
    }

    public @Nullable RestApi getApi() {
        return mApi;
    }

    /**
     * Register a listener for the syncthing API state changing.
     *
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see #unregisterOnApiChangeListener
     */
    public void registerOnApiChangeListener(OnApiChangeListener listener) {
        // Make sure we don't send an invalid state or syncthing might show a "disabled" message
        // when it's just starting up.
        listener.onApiChange(mCurrentState);
        mOnApiChangeListeners.add(listener);
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @see #registerOnApiChangeListener
     */
    public void unregisterOnApiChangeListener(OnApiChangeListener listener) {
        mOnApiChangeListeners.remove(listener);
    }

    /**
     * Wait for the web-gui of the native syncthing binary to come online.
     *
     * In case the binary is to be stopped, also be aware that another thread could request
     * to stop the binary in the time while waiting for the GUI to become active. See the comment
     * for SyncthingService.onDestroy for details.
     */
    private void pollWebGui() {
        new PollWebGuiAvailableTask(this, getWebGuiUrl(), mConfig.getApiKey(), result -> {
            synchronized (mStateLock) {
                if (mStopScheduled) {
                    shutdown(State.DISABLED, () -> {});
                    mStopScheduled = false;
                    stopSelf();
                    return;
                }
            }
            Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
            onApiChange(State.STARTING);
            Stream.of(mOnWebGuiAvailableListeners).forEach(OnWebGuiAvailableListener::onWebGuiAvailable);
            mOnWebGuiAvailableListeners.clear();
        });
    }

    /**
     * Called to notifiy listeners of an API change.
     *
     * Must only be called from SyncthingService or {@link RestApi} on the main thread.
     */
    private void onApiChange(State newState) {
        mCurrentState = newState;
        mNotificationHandler.updatePersistentNotification(this);
        for (Iterator<OnApiChangeListener> i = mOnApiChangeListeners.iterator();
             i.hasNext(); ) {
            OnApiChangeListener listener = i.next();
            if (listener != null) {
                listener.onApiChange(mCurrentState);
            } else {
                i.remove();
            }
        }
    }

    public URL getWebGuiUrl() {
        return mConfig.getWebGuiUrl();
    }

    public State getCurrentState() {
        return mCurrentState;
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
            new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });
        return true;
    }
}
