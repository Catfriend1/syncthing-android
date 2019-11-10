package com.nutomic.syncthingandroid.service;

import android.app.PendingIntent;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.annimon.stream.Stream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Run by the syncthing service to convert syncthing events into local broadcasts.
 *
 * It uses {@link RestApi#getEvents} to read the pending events and wait for new events.
 */
public class EventProcessor implements  Runnable, RestApi.OnReceiveEventListener {

    private static final String TAG = "EventProcessor";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Minimum interval in seconds at which the events are polled from syncthing and processed.
     * This interval will not wake up the device to save battery power.
     */
    private static final long EVENT_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(15);

    /**
     * Use the MainThread for all callbacks and message handling
     * or we have to track down nasty threading problems.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private volatile long mLastEventId = 0;
    private volatile boolean mShutdown = true;

    private final Context mContext;
    private final RestApi mRestApi;
    @Inject SharedPreferences mPreferences;
    @Inject NotificationHandler mNotificationHandler;

    public EventProcessor(Context context, RestApi restApi) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        mContext = context;
        mRestApi = restApi;
    }

    @Override
    public void run() {
        // Restore the last event id if the event processor may have been restarted.
        if (mLastEventId == 0) {
            mLastEventId = mPreferences.getLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, 0);
        }

        // First check if the event number ran backwards.
        // If that's the case we've to start at zero because syncthing was restarted.
        mRestApi.getEvents(0, 1, new RestApi.OnReceiveEventListener() {
            @Override
            public void onEvent(Event event, JsonElement json) {
            }

            @Override
            public void onDone(long lastId) {
                if (lastId < mLastEventId) mLastEventId = 0;

                LogV("Reading events starting with id " + mLastEventId);

                mRestApi.getEvents(mLastEventId, 0, EventProcessor.this);
            }
        });
    }

    /**
     * Performs the actual event handling.
     */
    @Override
    public void onEvent(Event event, JsonElement json) {
        switch (event.type) {
            case "ConfigSaved":
                if (mRestApi != null) {
                    LogV("Forwarding ConfigSaved event to RestApi to get the updated config.");
                    mRestApi.reloadConfig();
                }
                break;
            case "DeviceRejected":
                /**
                 * This is obsolete since v0.14.51, https://github.com/syncthing/syncthing/pull/5084
                 * Unknown devices are now stored to "config.xml" and persisted until the user decided
                 * to accept or ignore the device connection request. We don't need to catch the event
                 * as a "ConfigSaved" event is fired which will be forwarded to:
                 * {@link RestApi#reloadConfig} => {@link RestApi#onReloadConfigComplete}
                 */
                /*
                onDeviceRejected(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("name")             // deviceName
                );
                */
                break;
            case "FolderCompletion":
                CompletionInfo completionInfo = new CompletionInfo();
                completionInfo.completion = (Double) event.data.get("completion");
                mRestApi.setCompletionInfo(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("folder"),          // folderId
                    completionInfo
                );
                break;
            case "FolderErrors":
                LogV("Event " + event.type + ", data " + event.data);
                onFolderErrors(json);
                break;
            case "FolderRejected":
                /**
                 * This is obsolete since v0.14.51, https://github.com/syncthing/syncthing/pull/5084
                 * Unknown folders are now stored to "config.xml" and persisted until the user decided
                 * to accept or ignore the folder share request. We don't need to catch the event
                 * as a "ConfigSaved" event is fired which will be forwarded to:
                 * {@link RestApi#reloadConfig} => {@link RestApi#onReloadConfigComplete}
                 */
                /*
                onFolderRejected(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("folder"),          // folderId
                    (String) event.data.get("folderLabel")      // folderLabel
                );
                */
                break;
            case "ItemFinished":
                String action               = (String) event.data.get("action");
                String error                = (String) event.data.get("error");
                String folderId             = (String) event.data.get("folder");
                String relativeFilePath     = (String) event.data.get("item");

                // Lookup folder.path for the given folder.id if all fields were contained in the event.data.
                String folderPath = null;
                if (!TextUtils.isEmpty(action) &&
                        !TextUtils.isEmpty(folderId) &&
                        !TextUtils.isEmpty(relativeFilePath)) {
                    for (Folder folder : mRestApi.getFolders()) {
                        if (folder.id.equals(folderId)) {
                            folderPath = folder.path;
                            break;
                        }
                    }
                }
                if (!TextUtils.isEmpty(folderPath)) {
                    onItemFinished(action, error, new File(folderPath, relativeFilePath));
                } else {
                    Log.w(TAG, "ItemFinished: Failed to determine folder.path for folder.id=\"" + (TextUtils.isEmpty(folderId) ? "" : folderId) + "\"");
                }
                break;
            case "Ping":
                // Ignored.
                break;
            case "DeviceConnected":
            case "DeviceDisconnected":
            case "DeviceDiscovered":
            case "DownloadProgress":
            case "FolderPaused":
            case "FolderResumed":
            case "FolderScanProgress":
            case "FolderSummary":
            case "FolderWatchStateChanged":
            case "ItemStarted":
            case "ListenAddressesChanged":
            case "LocalIndexUpdated":
            case "LoginAttempt":
            case "RemoteDownloadProgress":
            case "RemoteIndexUpdated":
            case "Starting":
            case "StartupComplete":
            case "StateChanged":
                LogV("Ignored event " + event.type + ", data " + event.data);
                break;
            default:
                Log.d(TAG, "Unhandled event " + event.type);
        }
    }

    @Override
    public void onDone(long id) {
        if (mLastEventId < id) {
            mLastEventId = id;

            // Store the last EventId in case we get killed
            mPreferences.edit().putLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, mLastEventId).apply();
        }

        synchronized (mMainThreadHandler) {
            if (!mShutdown) {
                mMainThreadHandler.removeCallbacks(this);
                mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
            }
        }
    }

    public void start() {
        Log.d(TAG, "Starting event processor.");

        // Remove all pending callbacks and add a new one. This makes sure that only one
        // event poller is running at any given time.
        synchronized (mMainThreadHandler) {
            mShutdown = false;
            mMainThreadHandler.removeCallbacks(this);
            mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
        }
    }

    public void stop() {
        Log.d(TAG, "Stopping event processor.");
        synchronized (mMainThreadHandler) {
            mShutdown = true;
            mMainThreadHandler.removeCallbacks(this);
        }
    }

    /*
    private void onDeviceRejected(String deviceId, String deviceName) {
        if (deviceId == null) {
            return;
        }
        Log.d(TAG, "Unknown device '" + deviceName + "' (" + deviceId + ") wants to connect");

        // Show device approve/ignore notification.
        mNotificationHandler.showDeviceConnectNotification(deviceId, deviceName);
    }
    */

    /*
    private void onFolderRejected(String deviceId, String folderId,
                                    String folderLabel) {
        if (deviceId == null || folderId == null) {
            return;
        }
        Log.d(TAG, "Device '" + deviceId + "' wants to share folder '" +
            folderLabel + "' (" + folderId + ")");

        // Find the deviceName corresponding to the deviceId.
        String deviceName = null;
        for (Device d : mRestApi.getDevices(false)) {
            if (d.deviceID.equals(deviceId)) {
                deviceName = d.getDisplayName();
                break;
            }
        }

        Boolean isNewFolder = Stream.of(mRestApi.getFolders())
                .noneMatch(f -> f.id.equals(folderId));

        // Show folder approve/ignore notification.
        mNotificationHandler.showFolderShareNotification(
            deviceId,
            deviceName,
            folderId,
            folderLabel,
            isNewFolder
        );
    }
    */

    private void onFolderErrors(JsonElement json) {
        JsonElement data = ((JsonObject) json).get("data");
        if (data == null) {
            Log.e(TAG, "onFolderErrors: data == null");
            return;
        }
        JsonArray errors = (JsonArray) ((JsonObject) data).get("errors");
        if (errors == null) {
            Log.e(TAG, "onFolderErrors: errors == null");
            return;
        }
        for (int i = 0; i < errors.size(); i++) {
            JsonElement error = errors.get(i);
            if (error != null) {
                String strError = ((JsonObject) error).get("error").toString();
                String strPath = ((JsonObject) error).get("path").toString();
                if (!TextUtils.isEmpty(strError) &&
                    !TextUtils.isEmpty(strPath) &&
                            strError.contains("insufficient space in basic")) {
                    String[] segments = strPath.split(File.separator);
                    String shortenedFileAndFolder =
                            segments.length < 2 ?
                            strPath :
                            segments[segments.length-2] + File.separator + segments[segments.length-1];
                    mNotificationHandler.showCrashedNotification(R.string.notification_out_of_disk_space, shortenedFileAndFolder);
                }
            }
        }
    }

    /**
     * Precondition: action != null
     */
    private void onItemFinished(String action, String error, File updatedFile) {
        String relativeFilePath = updatedFile.toString();
        if (!TextUtils.isEmpty(error)) {
            Log.e(TAG, "onItemFinished: Error \"" + error + "\" reported on file: " + relativeFilePath);
            if (error.contains("no space left on device")) {
                String[] segments = relativeFilePath.split(File.separator);
                String shortenedFileAndFolder =
                        segments.length < 2 ?
                        relativeFilePath :
                        segments[segments.length-2] + File.separator + segments[segments.length-1];
                mNotificationHandler.showCrashedNotification(R.string.notification_out_of_disk_space, shortenedFileAndFolder);
            }
            return;
        }

        switch (action) {
            case "delete":          // file deleted
                Log.i(TAG, "Deleting file from MediaStore: " + relativeFilePath);
                Uri contentUri = MediaStore.Files.getContentUri("external");
                ContentResolver resolver = mContext.getContentResolver();
                LoggingAsyncQueryHandler asyncQueryHandler = new LoggingAsyncQueryHandler(resolver);
                asyncQueryHandler.startDelete(
                    0,                          // this will be passed to "onUpdatedComplete#token"
                    relativeFilePath,           // this will be passed to "onUpdatedComplete#cookie"
                    contentUri,
                    MediaStore.Images.ImageColumns.DATA + " LIKE ?",
                    new String[]{updatedFile.getPath()}
                );
                break;
            case "update":          // file contents changed
            case "metadata":        // file metadata changed but not contents
                Log.i(TAG, "Rescanning file via MediaScanner: " + relativeFilePath);
                MediaScannerConnection.scanFile(mContext, new String[]{updatedFile.getPath()},
                        null, null);
                break;
            default:
                Log.w(TAG, "onItemFinished: Unhandled action \"" + action + "\"");
        }
    }

    private static class LoggingAsyncQueryHandler extends AsyncQueryHandler {

        public LoggingAsyncQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onUpdateComplete(token, cookie, result);
            if (result == 1 && cookie != null) {
                // Log.v(TAG, "onItemFinished: onDeleteComplete: [ok] file=" + cookie.toString() + ", token=" + Integer.toString(token));
            }
        }
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
