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

import androidx.core.util.Consumer;

import com.annimon.stream.Stream;
// import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderStatus;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

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
    private static final long EVENT_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5);

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
            public void onError() {
            }

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
            case "DeviceConnected":
                mRestApi.updateRemoteDeviceConnected(
                        (String) event.data.get("id"),          // deviceId
                        true
                );
                break;
            case "DeviceDisconnected":
                mRestApi.updateRemoteDeviceConnected(
                        (String) event.data.get("id"),          // deviceId
                        false
                );
                break;
            case "DevicePaused":
                mRestApi.updateRemoteDevicePaused(
                        (String) event.data.get("device"),          // deviceId
                        true
                );
                break;
            case "DeviceResumed":
                mRestApi.updateRemoteDevicePaused(
                        (String) event.data.get("device"),          // deviceId
                        false
                );
                break;
            case "FolderCompletion":
                onFolderCompletion(event.data);
                break;
            case "FolderErrors":
                LogV("Event " + event.type + ", data " + event.data);
                onFolderErrors(json);
                break;
            case "FolderPaused":
                onFolderPaused(
                        (String) event.data.get("id")              // folderId
                );
                break;
            case "FolderResumed":
                onFolderResumed(
                        (String) event.data.get("id")              // folderId
                );
                break;
            case "FolderSummary":
                onFolderSummary(
                        json,
                        (String) event.data.get("folder")          // folderId
                );
                break;
            case "ItemFinished":
                String action               = (String) event.data.get("action");
                String error                = (String) event.data.get("error");
                String folderId             = (String) event.data.get("folder");
                String relativeFilePath     = (String) event.data.get("item");

                // Lookup folder.path for the given folder.id if all fields were contained in the event.data.
                String folderPath = null;
                String folderType = null;
                if (!TextUtils.isEmpty(action) &&
                        !TextUtils.isEmpty(folderId) &&
                        !TextUtils.isEmpty(relativeFilePath)) {
                    for (Folder folder : mRestApi.getFolders()) {
                        if (folder.id.equals(folderId)) {
                            folderPath = folder.path;
                            folderType = folder.type;
                            break;
                        }
                    }
                }
                if (!TextUtils.isEmpty(folderPath) ||
                        !TextUtils.isEmpty(folderType)) {
                    if (TextUtils.isEmpty(error)) {
                        // We don't intend to show errors as the last synced item on the UI.
                        mRestApi.setLocalFolderLastItemFinished(folderId, action, relativeFilePath, event.time);
                    }
                    onItemFinished(action, error, folderType, folderPath + File.separator + relativeFilePath);
                } else {
                    Log.w(TAG, "ItemFinished: Failed to determine folder.path for folder.id=\"" + (TextUtils.isEmpty(folderId) ? "" : folderId) + "\"");
                }
                break;
            case "LocalIndexUpdated":
                LogV("Event " + event.type + ", data " + event.data);
                onLocalIndexUpdated(json, (String) event.data.get("folder"), event.time);
                break;
            case "PendingDevicesChanged":
                mapNullable((List<Map<String,String>>) event.data.get("added"), this::onPendingDevicesChanged);
                break;
            case "PendingFoldersChanged":
                mapNullable((List<Map<String,Object>>) event.data.get("added"), this::onPendingFoldersChanged);
                break;
            case "Ping":
                // Ignored.
                break;
            case "StateChanged":
                onStateChanged(
                        (String) event.data.get("folder"),         // folderId
                        (String) event.data.get("to")
                );
                break;
            case "DeviceDiscovered":
            case "DownloadProgress":
            case "FolderScanProgress":
            case "FolderWatchStateChanged":
            case "ItemStarted":
            case "ListenAddressesChanged":
            case "LoginAttempt":
            case "RemoteDownloadProgress":
                /*
                onRemoteDownloadProgress(
                        (String) event.data.get("device"),         // deviceId
                        (String) event.data.get("folder"),         // folderId
                        event.data.get("state") == null ? null : new Gson().toJsonTree(event.data.get("state")).getAsJsonObject() 
                );
                break;
                */
            case "RemoteIndexUpdated":
                onRemoteIndexUpdated(
                        (String) event.data.get("device"),         // deviceId
                        (String) event.data.get("folder"),         // folderId
                        event.data.get("items") == null ? 0 : (double) event.data.get("items")
                );
                break;
            case "Starting":
            case "StartupComplete":
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

    @Override
    public void onError() {
        synchronized (mMainThreadHandler) {
            if (!mShutdown) {
                Log.d(TAG, "Event sink aborted, will retry in " + Long.toString(EVENT_UPDATE_INTERVAL) + " ms");
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

    private void onPendingDevicesChanged(Map<String, String> added) {
        String deviceId = added.get("deviceID");
        String deviceName = added.get("name");
        String deviceAddress = added.get("address");
        if (deviceId == null) {
            return;
        }
        Log.d(TAG, "Unknown device '" + deviceName + "' (" + deviceId + ") wants to connect");
        // Show device approve/ignore notification.
        mNotificationHandler.showDeviceConnectNotification(deviceId, deviceName, deviceAddress);
    }

    private void onPendingFoldersChanged(Map<String, Object> added) {
        String deviceId = added.get("deviceID").toString();
        String folderId = added.get("folderID").toString();
        String folderLabel = added.get("folderLabel").toString();
        Boolean receiveEncrypted = (Boolean) added.get("receiveEncrypted");
        Boolean remoteEncrypted = (Boolean) added.get("remoteEncrypted");
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
            receiveEncrypted,
            remoteEncrypted,
            isNewFolder
        );
    }

    private void onFolderCompletion(final Map<String, Object> eventData) {
        mRestApi.setRemoteCompletionInfo(
            (String) eventData.get("device"),          // deviceId
            (String) eventData.get("folder"),          // folderId
            (Double) eventData.get("needBytes"),
            (Double) eventData.get("completion")
        );
    }

    private void onFolderErrors(final JsonElement json) {
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

    private void onFolderPaused(final String folderId) {
        mRestApi.updateLocalFolderPause(folderId, true);
    }

    private void onFolderResumed(final String folderId) {
        mRestApi.updateLocalFolderPause(folderId, false);
    }

    private void onFolderSummary(final JsonElement json, final String folderId) {
        JsonElement data = ((JsonObject) json).get("data");
        if (data == null) {
            Log.e(TAG, "onFolderSummary: data == null");
            return;
        }
        JsonElement summary = ((JsonObject) data).get("summary");
        if (summary == null) {
            Log.e(TAG, "onFolderSummary: summary == null");
            return;
        }
        FolderStatus folderStatus = new FolderStatus();
        try {
            folderStatus = new GsonBuilder().create()
                    .fromJson(summary, FolderStatus.class);
        } catch (Exception e) {
            Log.e(TAG, "onFolderSummary: gson.fromJson failed", e);
            return;
        }
        mRestApi.setLocalFolderStatus(folderId, folderStatus);
    }


    /**
     * Precondition: action != null
     */
    private void onItemFinished(String action, String error, final String folderType, final String fullFilePath) {
        if (!TextUtils.isEmpty(error)) {
            Log.e(TAG, "onItemFinished: Error \"" + error + "\" reported on file: " + fullFilePath);
            if (error.contains("no space left on device")) {
                String[] segments = fullFilePath.split(File.separator);
                String shortenedFileAndFolder =
                        segments.length < 2 ?
                        fullFilePath :
                        segments[segments.length-2] + File.separator + segments[segments.length-1];
                mNotificationHandler.showCrashedNotification(R.string.notification_out_of_disk_space, shortenedFileAndFolder);
            }
            return;
        }

        if (folderType.equals(Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED)) {
            // Skip notifying Android's MediaStore, MediaScanner.
            return;
        }

        switch (action) {
            case "delete":          // file deleted
                if (new File(fullFilePath).exists()) {
                    Log.i(TAG, "onItemFinished: MediaStore, Skip file deletion because file exists: " + fullFilePath);
                    break;
                }
                Log.i(TAG, "onItemFinished: MediaStore, Deleting file: " + fullFilePath);
                Uri contentUri = MediaStore.Files.getContentUri("external");
                ContentResolver resolver = mContext.getContentResolver();
                LoggingAsyncQueryHandler asyncQueryHandler = new LoggingAsyncQueryHandler(resolver);
                asyncQueryHandler.startDelete(
                    0,                          // this will be passed to "onUpdatedComplete#token"
                    fullFilePath,               // this will be passed to "onUpdatedComplete#cookie"
                    contentUri,
                    MediaStore.Images.ImageColumns.DATA + " = ?",
                    new String[]{fullFilePath}
                );
                break;
            case "update":          // file contents changed
                Log.i(TAG, "onItemFinished: MediaScanner, Rescanning file: " + fullFilePath);
                MediaScannerConnection.scanFile(mContext, new String[]{fullFilePath},
                        null, null);
                break;
            case "metadata":        // file metadata changed but not contents
                Log.i(TAG, "onItemFinished: MediaScanner, Skipping file: " + fullFilePath);
                break;
            default:
                Log.w(TAG, "onItemFinished: Unhandled action \"" + action + "\"");
        }
    }

    private void onLocalIndexUpdated(final JsonElement json,
                                            final String folderId,
                                            final String dateTimeStamp) {
        JsonElement data = ((JsonObject) json).get("data");
        if (data == null) {
            Log.e(TAG, "onLocalIndexUpdated: data == null");
            return;
        }
        JsonArray filenames = (JsonArray) ((JsonObject) data).get("filenames");
        if (filenames == null) {
            Log.e(TAG, "onLocalIndexUpdated: filenames == null");
            return;
        }
        for (int i = 0; i < filenames.size(); i++) {
            String filename = ((JsonElement) filenames.get(i)).toString();
            if (!TextUtils.isEmpty(filename)) {
                filename = filename.replaceAll("^\"|\"$", "");
                LogV("onLocalIndexUpdated: filename=[" + filename + "], time=[" + dateTimeStamp + "]");
                if (i == filenames.size() - 1) {
                    // Send the last (latest) local change to the UI.
                    mRestApi.setLocalFolderLastItemFinished(
                            folderId,
                            "update",
                            filename,
                            dateTimeStamp
                    );
                }
            }
        }
    }

    /*
    private void onRemoteDownloadProgress(final String deviceId, 
                                                final String folderId,
                                                final JsonObject state) {
        if (state == null) {
            LogV("onRemoteDownloadProgress: state == null");
            return;
        }                        
        LogV("onRemoteDownloadProgress: state=[" + state + "]");
    }
    */

    private void onRemoteIndexUpdated(final String deviceId, 
                                            final String folderId, 
                                            final Double items) {
        if (deviceId == null || folderId == null || items == null) {
            return;
        }
        // LogV("onRemoteIndexUpdated: deviceId=[" + deviceId + "], folder=[" + folderId + "], items=" + items);
        if (items > 0) {
            mRestApi.setRemoteIndexUpdated(deviceId, folderId, true);
        }
    }

    /**
     * Emitted when a folder changes state.
     */
    private void onStateChanged(final String folderId, final String newState) {
        mRestApi.updateLocalFolderState(folderId, newState);
        // LogV("onStateChanged: folder=[" + folderId + "], newState=[" + newState + "]");
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

    private <T> void mapNullable(List<T> l, Consumer<T> c) {
        if (l != null) {
            for (T m : l) {
                c.accept(m);
            }
        }
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
