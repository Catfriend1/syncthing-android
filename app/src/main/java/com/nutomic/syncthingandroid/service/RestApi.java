package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.annimon.stream.Stream;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.http.GetRequest;
import com.nutomic.syncthingandroid.http.PostRequest;
import com.nutomic.syncthingandroid.model.CachedFolderStatus;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DeviceStat;
import com.nutomic.syncthingandroid.model.DiscoveredDevice;
import com.nutomic.syncthingandroid.model.DiskEvent;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.IgnoredFolder;
import com.nutomic.syncthingandroid.model.LocalCompletion;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.PendingDevice;
import com.nutomic.syncthingandroid.model.PendingFolder;
import com.nutomic.syncthingandroid.model.RemoteCompletion;
import com.nutomic.syncthingandroid.model.RemoteCompletionInfo;
import com.nutomic.syncthingandroid.model.RemoteIgnoredDevice;
import com.nutomic.syncthingandroid.model.SharedWithDevice;
import com.nutomic.syncthingandroid.model.SystemStatus;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.Constants.ENABLE_TEST_DATA;
import static com.nutomic.syncthingandroid.util.Util.getLocalZonedDateTime;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi {

    private static final String TAG = "RestApi";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Intents we sent to to other apps that subscribed to us.
     */
    private static final String ACTION_NOTIFY_FOLDER_SYNC_COMPLETE =
            ".ACTION_NOTIFY_FOLDER_SYNC_COMPLETE";

    /**
     * Permission for apps receiving our broadcast intents.
     */
     private static final String PERMISSION_RECEIVE_SYNC_STATUS =
            ".permission.RECEIVE_SYNC_STATUS";

    /**
     * Compares folders by labels, uses the folder ID as fallback if the label is empty
     */
    private final static Comparator<Folder> FOLDERS_COMPARATOR = (lhs, rhs) -> {
        String lhsLabel = lhs.label != null && !lhs.label.isEmpty() ? lhs.label : lhs.id;
        String rhsLabel = rhs.label != null && !rhs.label.isEmpty() ? rhs.label : rhs.id;
        return lhsLabel.compareTo(rhsLabel);
    };

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    public interface OnResultListener1<T> {
        void onResult(T t);
    }

    private final Context mContext;
    private URL mUrl;
    private final String mApiKey;

    private String mVersion;
    private Config mConfig;

    /**
     * Results cached from systemInfo
     */
    private String mLocalDeviceId;
    private Integer mUrVersionMax;

    /**
     * Stores the result of the last successful request to {@link GetRequest#URI_CONNECTIONS},
     * or an empty Map.
     */
    private Optional<Connections> mPreviousConnections = Optional.absent();

    /**
     * Stores the timestamp of the last result of the REST API endpoint {@link GetRequest#URI_CONNECTIONS}.
     */
    private long mPreviousConnectionTime = 0;

    /**
     * In the last-finishing {@link #readConfigFromRestApi} callback, we have to call
     * {@link SyncthingService#onApiAvailable} to indicate that the RestApi class is fully initialized.
     * We do this to avoid getting stuck with our main thread due to synchronous REST queries.
     * The correct indication of full initialisation is crucial to stability as other listeners of
     * {@link ../activities/SettingsActivity#SettingsFragment#onServiceStateChange} needs cached config and system information available.
     * e.g. SettingsFragment need "mLocalDeviceId"
     */
    private Boolean asyncQueryConfigComplete            = false;
    private Boolean asyncQueryVersionComplete           = false;
    private Boolean asyncQuerySystemStatusComplete      = false;

    /**
     * Object that must be locked upon accessing the following variables:
     * asyncQueryConfigComplete, asyncQueryVersionComplete, asyncQuerySystemStatusComplete
     */
    private final Object mAsyncQueryCompleteLock = new Object();

    /**
     * Object that must be locked upon accessing mConfig
     */
    private final Object mConfigLock = new Object();

    /**
     * Stores the latest result of device and folder completion events.
     */
    private LocalCompletion mLocalCompletion;
    private RemoteCompletion mRemoteCompletion;
    private int mLastOnlineDeviceCount = 0;
    private int mLastTotalSyncCompletion = -1;

    private Boolean hasShutdown = false;

    private Gson mGson;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Inject NotificationHandler mNotificationHandler;

    public RestApi(Context context, URL url, String apiKey, OnApiAvailableListener apiListener,
                   OnConfigChangedListener configListener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(context);
        mContext = context;
        mUrl = url;
        mApiKey = apiKey;
        mOnApiAvailableListener = apiListener;
        mOnConfigChangedListener = configListener;
        mLocalCompletion = new LocalCompletion(ENABLE_VERBOSE_LOG);
        mRemoteCompletion = new RemoteCompletion(ENABLE_VERBOSE_LOG);
        mGson = getGson();
    }

    public interface OnApiAvailableListener {
        void onApiAvailable();
    }

    private final OnApiAvailableListener mOnApiAvailableListener;

    private final OnConfigChangedListener mOnConfigChangedListener;

    /**
     * Gets local device ID, syncthing version and config, then calls all OnApiAvailableListeners.
     */
    public void readConfigFromRestApi() {
        LogV("Querying config from REST ...");
        synchronized (mAsyncQueryCompleteLock) {
            asyncQueryVersionComplete = false;
            asyncQueryConfigComplete = false;
            asyncQuerySystemStatusComplete = false;
        }
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            mVersion = json.get("version").getAsString();
            updateDebugFacilitiesCache();
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryVersionComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        }, error -> {});
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, result -> {
            onReloadConfigComplete(result);
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryConfigComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        }, error -> {});
        getSystemStatus(info -> {
            mLocalDeviceId = info.myID;
            mUrVersionMax = info.urVersionMax;
            synchronized (mAsyncQueryCompleteLock) {
                asyncQuerySystemStatusComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
    }

    private void checkReadConfigFromRestApiCompleted() {
        if (asyncQueryVersionComplete &&
                asyncQueryConfigComplete &&
                asyncQuerySystemStatusComplete) {
            LogV("Reading config from REST completed. Syncthing version is " + mVersion);
            // Tell SyncthingService it can transition to State.ACTIVE.
            mOnApiAvailableListener.onApiAvailable();

            // Temporarily lower cleanupIntervalS for every folder to force cleanup after startup.
            setVersioningCleanupIntervalS(2);
            final Handler resetCleanupIntervalHandler = new Handler(Looper.getMainLooper());
            resetCleanupIntervalHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (hasShutdown) {
                        LogV("Skipping setVersioningCleanupIntervalS(3600) due to hasShutdown == true");
                        return;
                    }
                    setVersioningCleanupIntervalS(3600);
                }
            }, 10000);
        }
    }

    public void reloadConfig() {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, this::onReloadConfigComplete, error -> {});
    }

    private void onReloadConfigComplete(String configResult) {
        Boolean configParseSuccess;
        synchronized(mConfigLock) {
            mConfig = mGson.fromJson(configResult, Config.class);
            configParseSuccess = mConfig != null;
        }
        if (!configParseSuccess) {
            throw new RuntimeException("config is null: " + configResult);
        }
        Log.d(TAG, "onReloadConfigComplete: Successfully parsed configuration.");

        synchronized (mConfigLock) {
            String logRemoteIgnoredDevices = mGson.toJson(mConfig.remoteIgnoredDevices);
            if (!logRemoteIgnoredDevices.equals("[]")) {
                LogV("ORCC: remoteIgnoredDevices = " + logRemoteIgnoredDevices);
            }

            // Loop through devices to get ignoredFolders per device.
            for (final Device device : getDevices(false)) {
                String logIgnoredFolders = mGson.toJson(device.ignoredFolders);
                if (!logIgnoredFolders.equals("[]")) {
                    LogV("ORCC: device[" + device.getDisplayName() + "].ignoredFolders = " + logIgnoredFolders);
                }
            }
        }

        new GetRequest(mContext, mUrl, GetRequest.URI_PENDING_DEVICES, mApiKey, null, result -> {
            if (mNotificationHandler == null) {
                Log.e(TAG, "ORCC: URI_PENDING_DEVICES, mNotificationHandler == null");
                return;
            }
            if (result == null) {
                Log.e(TAG, "ORCC: URI_PENDING_DEVICES, result == null");
                return;
            }
            JsonObject jsonObject = new JsonParser().parse(result).getAsJsonObject();
            if (jsonObject == null) {
                Log.e(TAG, "ORCC: URI_PENDING_DEVICES, jsonObject == null");
                return;
            }
            Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
            for (Map.Entry<String, JsonElement> deviceEntry: entries) {
                final String resultDeviceId = deviceEntry.getKey();
                if (resultDeviceId == null) {
                    continue;
                }
                final PendingDevice pendingDevice = mGson.fromJson(deviceEntry.getValue(), PendingDevice.class);
                if (pendingDevice.time == null) {
                    continue;
                }
                Log.d(TAG, "ORCC: resultDeviceId = " + resultDeviceId + "('" + pendingDevice.name + "')");
                mNotificationHandler.showDeviceConnectNotification(
                    resultDeviceId,
                    pendingDevice.name,
                    pendingDevice.address
                );
            }
        }, error -> {});
        new GetRequest(mContext, mUrl, GetRequest.URI_PENDING_FOLDERS, mApiKey, null, result -> {
            if (mNotificationHandler == null) {
                Log.e(TAG, "ORCC: URI_PENDING_FOLDERS, mNotificationHandler == null");
                return;
            }
            if (result == null) {
                Log.e(TAG, "ORCC: URI_PENDING_FOLDERS, result == null");
                return;
            }
            JsonObject jsonObject = new JsonParser().parse(result).getAsJsonObject();
            if (jsonObject == null) {
                Log.e(TAG, "ORCC: URI_PENDING_FOLDERS, jsonObject == null");
                return;
            }
            Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
            for (Map.Entry<String, JsonElement> folderEntry: entries) {
                final String resultFolderId = folderEntry.getKey();
                if (resultFolderId == null) {
                    continue;
                }
                JsonObject jsonObjectOfferedBy = ((JsonObject) folderEntry.getValue().getAsJsonObject()).get("offeredBy").getAsJsonObject();
                Set<Map.Entry<String, JsonElement>> offeredByEntries = jsonObjectOfferedBy.entrySet();
                for (Map.Entry<String, JsonElement> offeredByEntry: offeredByEntries) {
                    final String offeredByDeviceId = offeredByEntry.getKey();
                    if (offeredByDeviceId == null) {
                        continue;
                    }
                    final PendingFolder pendingFolder = mGson.fromJson(offeredByEntry.getValue(), PendingFolder.class);
                    Log.d(TAG, "ORCC: resultFolderId = " + resultFolderId + "('" + pendingFolder.label + "')");
                    Device matchingDevice = Stream.of(getDevices(false))
                            .filter(d -> d.deviceID.equals(offeredByDeviceId))
                            .findFirst()
                            .get();
                    Boolean isNewFolder = Stream.of(getFolders())
                            .noneMatch(f -> f.id.equals(resultFolderId));
                    mNotificationHandler.showFolderShareNotification(
                        offeredByDeviceId,
                        matchingDevice.getDisplayName(),
                        resultFolderId,
                        pendingFolder.label,
                        pendingFolder.receiveEncrypted,
                        pendingFolder.remoteEncrypted,
                        isNewFolder
                    );
                }
            }
        }, error -> {});

        // Update cached device and folder information.
        final List<Folder> tmpFolders = getFolders();
        mLocalCompletion.updateFromConfig(tmpFolders);
        mRemoteCompletion.updateFromConfig(getDevices(true), tmpFolders);

        // Perform first query for remote device status by forcing a cache miss.
        getRemoteDeviceStatus("");

        for (Folder folder : tmpFolders) {
            final List<SharedWithDevice> sharedWithDevices = folder.getSharedWithDevices();
            for (SharedWithDevice device : sharedWithDevices) {
                new GetRequest(mContext,
                        mUrl,
                        GetRequest.URI_DB_COMPLETION,
                        mApiKey,
                        ImmutableMap.of(
                                "device", device.deviceID,
                                "folder", folder.id
                        ),
                        result -> {
                    // LogV("ORCC: /rest/db/completion: folder=" + folder.id + ", device=" + device.deviceID + ", result=" + result);
                    final CompletionInfo completionInfo = mGson.fromJson(result, CompletionInfo.class);
                    LogV("ORCC: /rest/db/completion: folder=" + folder.id +
                            ", device=" + device.getDisplayName() +
                            ", completion=" + completionInfo.completion +
                            ", needBytes=" + String.format(Locale.getDefault(), "%.0f", completionInfo.needBytes) +
                            ", remoteState=" + completionInfo.remoteState);
                    RemoteCompletionInfo remoteCompletionInfo = new RemoteCompletionInfo();
                    remoteCompletionInfo.completion = completionInfo.completion;
                    remoteCompletionInfo.needBytes = completionInfo.needBytes;
                    mRemoteCompletion.setCompletionInfo(device.deviceID, folder.id, remoteCompletionInfo);
                }, error -> {});
            }
        }
    }

    /**
     * Queries debug facilities available from the currently running syncthing binary
     * if the syncthing binary version changed. First launch of the binary is also
     * considered as a version change.
     * Precondition: {@link #mVersion} read from REST
     */
    private void updateDebugFacilitiesCache() {
        if (!mVersion.equals(PreferenceManager.getDefaultSharedPreferences(mContext).getString(Constants.PREF_LAST_BINARY_VERSION, ""))) {
            // First binary launch or binary upgraded case.
            new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM_LOGLEVELS, mApiKey, null, result -> {
                try {
                    Set<String> facilitiesToStore = new HashSet<String>();
                    JsonObject json = new JsonParser().parse(result).getAsJsonObject();
                    JsonObject jsonFacilities = json.getAsJsonObject("packages");
                    for (String facilityName : jsonFacilities.keySet()) {
                        facilitiesToStore.add(facilityName);
                    }
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putStringSet(Constants.PREF_DEBUG_FACILITIES_AVAILABLE, facilitiesToStore)
                        .apply();

                    // Store current binary version so we will only store this information again
                    // after a binary update.
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putString(Constants.PREF_LAST_BINARY_VERSION, mVersion)
                        .apply();
                } catch (Exception e) {
                    Log.w(TAG, "updateDebugFacilitiesCache: Failed to get debug facilities. result=" + result);
                }
            }, error -> {});
        }
    }

    /**
     * Permanently ignore a device when it tries to connect.
     * Ignored devices will not trigger the "DeviceRejected" event
     * in {@link EventProcessor#onEvent}.
     */
    public void ignoreDevice(String deviceId,
                                    String deviceName,
                                    String deviceAddress) {
        synchronized (mConfigLock) {
            // Check if the device has already been ignored.
            for (RemoteIgnoredDevice remoteIgnoredDevice : mConfig.remoteIgnoredDevices) {
                if (deviceId.equals(remoteIgnoredDevice.deviceID)) {
                    // Device already ignored.
                    Log.d(TAG, "Device already ignored [" + deviceId + "]");
                    return;
                }
            }

            /**
             * Ignore device by moving its corresponding "pendingDevice" entry to
             * a newly created "remoteIgnoredDevice" entry.
             */
            RemoteIgnoredDevice remoteIgnoredDevice = new RemoteIgnoredDevice();
            remoteIgnoredDevice.deviceID = deviceId;
            remoteIgnoredDevice.address = deviceAddress;
            remoteIgnoredDevice.name = deviceName;
            remoteIgnoredDevice.time = getLocalZonedDateTime();
            mConfig.remoteIgnoredDevices.add(remoteIgnoredDevice);
            sendConfig();
            Log.d(TAG, "Ignored device [" + deviceId + "]");
        }
    }

    /**
     * Permanently ignore a folder share request.
     * Ignored folders will not trigger the "FolderRejected" event
     * in {@link EventProcessor#onEvent}.
     */
    public void ignoreFolder(String deviceId,
                                    String folderId,
                                    String folderLabel) {
        synchronized (mConfigLock) {
            for (Device device : mConfig.devices) {
                if (deviceId.equals(device.deviceID)) {
                    /**
                     * Check if the folder has already been ignored.
                     */
                    for (IgnoredFolder ignoredFolder : device.ignoredFolders) {
                        if (folderId.equals(ignoredFolder.id)) {
                            // Folder already ignored.
                            Log.d(TAG, "ignoreFolder: Folder [" + folderId + "] already ignored on device [" + deviceId + "]");
                            return;
                        }
                    }

                    /**
                     * Ignore folder by moving its corresponding "pendingFolder" entry to
                     * a newly created "ignoredFolder" entry.
                     */
                    IgnoredFolder ignoredFolder = new IgnoredFolder();
                    ignoredFolder.id = folderId;
                    ignoredFolder.label = folderLabel;
                    ignoredFolder.time = getLocalZonedDateTime();
                    device.ignoredFolders.add(ignoredFolder);
                    LogV("ignoreFolder: device.ignoredFolders = " + mGson.toJson(device.ignoredFolders));
                    sendConfig();
                    Log.d(TAG, "Ignored folder [" + folderId + "] announced by device [" + deviceId + "]");

                    // Given deviceId handled.
                    break;
                }
            }
        }
    }

    /**
     * Undo ignoring devices and folders.
     */
    public void undoIgnoredDevicesAndFolders() {
        Log.d(TAG, "Undo ignoring devices and folders ...");
        synchronized (mConfigLock) {
            mConfig.remoteIgnoredDevices.clear();
            for (Device device : mConfig.devices) {
                device.ignoredFolders.clear();
            }
        }
    }

    /**
     * Override folder changes. This is the same as hitting
     * the "override changes" button from the web UI.
     */
    public void overrideChanges(String folderId) {
        Log.d(TAG, "overrideChanges '" + folderId + "'");
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_OVERRIDE, mApiKey,
            ImmutableMap.of("folder", folderId), null, null);
    }

    /**
     * Rescan all folders
     */
    public void rescanAll() {
        Log.d(TAG, "rescanAll");
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_SCAN, mApiKey,
            null, null, null);
    }

    /**
     * Revert local folder changes. This is the same as hitting
     * the "Revert local changes" button from the web UI.
     */
    public void revertLocalChanges(String folderId) {
        Log.d(TAG, "revertLocalChanges '" + folderId + "'");
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_REVERT, mApiKey,
            ImmutableMap.of("folder", folderId), null, null);
    }

    public URL getWebGuiUrl() {
        synchronized (mConfigLock) {
            String urlProtocol = Constants.osSupportsTLS12() ? "https" : "http";
            try {
                if (mConfig.gui.address == null) {
                    Log.e(TAG, "getWebGuiUrl: mConfig.gui.address == null, returning 127.0.0.1:8384");
                    return new URL(urlProtocol + "://127.0.0.1:8384");
                }
                return new URL(urlProtocol + "://" + mConfig.gui.address);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Failed to parse web interface URL", e);
            }
        }
    }

    /**
     * Sends current config to Syncthing.
     * Will result in a "ConfigSaved" event.
     * EventProcessor will trigger this.reloadConfig().
     */
    public void sendConfig() {
        String jsonConfig;
        synchronized (mConfigLock) {
            jsonConfig = mGson.toJson(mConfig);
        }
        // LogVMultipleLines("sendConfig: config=" + jsonToPrettyFormat(jsonConfig));
        new PostRequest(mContext, mUrl, PostRequest.URI_SYSTEM_CONFIG, mApiKey,
            null, jsonConfig, null);
        mUrl = getWebGuiUrl();
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Posts shutdown request.
     * This will cause SyncthingNative to exit and not restart.
     */
    public void shutdown() {
        hasShutdown = true;
        executorService.shutdown();
        new PostRequest(mContext, mUrl, PostRequest.URI_SYSTEM_SHUTDOWN, mApiKey,
                null, null, null);
    }

    /**
     * Returns the version name, or a (text) error message on failure.
     */
    public String getVersion() {
        return mVersion;
    }

    public List<Folder> getFolders() {
        List<Folder> folders;
        synchronized (mConfigLock) {
            folders = deepCopy(mConfig.folders, new TypeToken<List<Folder>>(){}.getType());
        }
        for (Folder folder : folders) {
            if (folder.path.startsWith("~/")) {
                folder.path = folder.path.replaceFirst("^~", FileUtils.getSyncthingTildeAbsolutePath());
            }
        }
        Collections.sort(folders, FOLDERS_COMPARATOR);
        return folders;
    }

    public final Folder getFolderByID(String folderID) {
        if (ENABLE_TEST_DATA && folderID.equals("abcd-efgh")) {
            final Folder folder = new Folder();
            folder.id = "abcd-efgh";
            folder.label = "label_abcd-efgh";
            folder.path = "/storage/emulated/0/testdata";
            folder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
            return folder;
        }

        final List<Folder> folders = getFolders();
        for (Folder folder : folders) {
            if (folder.id.equals(folderID)) {
                return folder;
            }
        }
        return null;
    }

    /**
     * This is only used for new folder creation, see {@link ../activities/FolderActivity}.
     */
    public void addFolder(Folder folder) {
        synchronized (mConfigLock) {
            // Add the new folder to the model.
            mConfig.folders.add(folder);
            // Send model changes to syncthing, does not require a restart.
            sendConfig();
        }
    }

    public void updateFolder(Folder newFolder) {
        synchronized (mConfigLock) {
            removeFolderInternal(newFolder.id);
            mConfig.folders.add(newFolder);
            sendConfig();
        }
    }

    public void removeFolder(String id) {
        synchronized (mConfigLock) {
            removeFolderInternal(id);
            // mLocalCompletion will be updated after the ConfigSaved event.
            // mRemoteCompletion will be updated after the ConfigSaved event.
            sendConfig();
            // Remove saved data from share activity for this folder.
        }
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(ShareActivity.PREF_FOLDER_SAVED_SUBDIRECTORY+id)
                .apply();
    }

    private void removeFolderInternal(String id) {
        synchronized (mConfigLock) {
            Iterator<Folder> it = mConfig.folders.iterator();
            while (it.hasNext()) {
                Folder f = it.next();
                if (f.id.equals(id)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    /**
     * Returns a list of all existing devices.
     *
     * @param includeLocal True if the local device should be included in the result.
     */
    public List<Device> getDevices(Boolean includeLocal) {
        List<Device> devices;
        synchronized (mConfigLock) {
            devices = deepCopy(mConfig.devices, new TypeToken<List<Device>>(){}.getType());
        }

        Iterator<Device> it = devices.iterator();
        while (it.hasNext()) {
            Device device = it.next();
            boolean isLocalDevice = Objects.equal(mLocalDeviceId, device.deviceID);
            if (!includeLocal && isLocalDevice) {
                it.remove();
                break;
            }
        }
        return devices;
    }

    public Device getLocalDevice() {
        List<Device> devices = getDevices(true);
        if (devices.isEmpty()) {
            throw new RuntimeException("RestApi.getLocalDevice: devices is empty.");
        }
        LogV("getLocalDevice: Looking for local device ID " + mLocalDeviceId);
        for (Device d : devices) {
            if (d.deviceID.equals(mLocalDeviceId)) {
                return deepCopy(d, Device.class);
            }
        }
        throw new RuntimeException("RestApi.getLocalDevice: Failed to get the local device crucial to continuing execution.");
    }

    /**
     * Adds or updates a device identified by its device ID.
     */
    public void updateDevice(Device newDevice) {
        synchronized (mConfigLock) {
            removeDeviceInternal(newDevice.deviceID);
            mConfig.devices.add(newDevice);
            sendConfig();
        }
    }

    public void removeDevice(String deviceId) {
        synchronized (mConfigLock) {
            removeDeviceInternal(deviceId);
            // mRemoteCompletion will be updated after the ConfigSaved event.
            sendConfig();
        }
    }

    private void removeDeviceInternal(String deviceId) {
        synchronized (mConfigLock) {
            Iterator<Device> it = mConfig.devices.iterator();
            while (it.hasNext()) {
                Device d = it.next();
                if (d.deviceID.equals(deviceId)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    public Options getOptions() {
        synchronized (mConfigLock) {
            return deepCopy(mConfig.options, Options.class);
        }
    }

    public Gui getGui() {
        synchronized (mConfigLock) {
            return deepCopy(mConfig.gui, Gui.class);
        }
    }

    public void editSettings(Gui newGui, Options newOptions) {
        synchronized (mConfigLock) {
            mConfig.gui = newGui;
            mConfig.options = newOptions;
        }
    }

    public void updateGui(Gui newGui) {
        synchronized (mConfigLock) {
            mConfig.gui = newGui;
            sendConfig();
        }
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    /**
     * Requests and parses information about current system status and resource usage.
     */
    public void getSystemStatus(OnResultListener1<SystemStatus> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM_STATUS, mApiKey, null, result -> {
            SystemStatus systemStatus;
            try {
                systemStatus = mGson.fromJson(result, SystemStatus.class);
                listener.onResult(systemStatus);
            } catch (Exception e) {
                Log.e(TAG, "getSystemStatus: Parsing REST API result failed. result=" + result);
            }
        }, error -> {});
    }

    public boolean isConfigLoaded() {
        synchronized(mConfigLock) {
            return mConfig != null;
        }
    }

    /**
     * Requests locally discovered devices.
     */
    public void getDiscoveredDevices(OnResultListener1<Map<String, DiscoveredDevice>> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM_DISCOVERY, mApiKey,
                null, result -> {
            Map<String, DiscoveredDevice> discoveredDevices = mGson.fromJson(result, new TypeToken<Map<String, DiscoveredDevice>>(){}.getType());
            if (ENABLE_TEST_DATA) {
                DiscoveredDevice fakeDiscoveredDevice = new DiscoveredDevice();
                fakeDiscoveredDevice.addresses = new String[]{"tcp4://192.168.178.10:40004"};
                discoveredDevices.put(TestData.DEVICE_A_ID, fakeDiscoveredDevice);
                discoveredDevices.put(TestData.DEVICE_B_ID, fakeDiscoveredDevice);
            }
            listener.onResult(discoveredDevices);
        }, error -> {});
    }

    /**
     * Requests ignore list for given folder.
     */
    public void getFolderIgnoreList(String folderId, OnResultListener1<FolderIgnoreList> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DB_IGNORES, mApiKey,
                ImmutableMap.of("folder", folderId), result -> {
            FolderIgnoreList folderIgnoreList = mGson.fromJson(result, FolderIgnoreList.class);
            listener.onResult(folderIgnoreList);
        }, error -> {});
    }

    /**
     * Posts ignore list for given folder.
     */
    public void postFolderIgnoreList(String folderId, String[] ignore) {
        FolderIgnoreList folderIgnoreList = new FolderIgnoreList();
        folderIgnoreList.ignore = ignore;
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_IGNORES, mApiKey,
            ImmutableMap.of("folder", folderId), mGson.toJson(folderIgnoreList), null);
    }

    /**
     * Requests and parses system version information.
     */
    public void getSystemVersion(OnResultListener1<SystemVersion> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            SystemVersion systemVersion = mGson.fromJson(result, SystemVersion.class);
            listener.onResult(systemVersion);
        }, error -> {});
    }

    /**
     * Returns status information about the device with the given id from cache.
     * Set deviceId to "" to query status for an initially empty cache.
     */
    public final Connection getRemoteDeviceStatus(
            final String deviceId) {
        Connection cacheEntry = mRemoteCompletion.getDeviceStatus(deviceId);
        if (cacheEntry.at.isEmpty()) {
            /**
             * Cache miss.
             * Query the required information so it will be available on a future call to this function.
             */
            if (!TextUtils.isEmpty(deviceId)) {
                LogV("getRemoteDeviceStatus: Cache miss, deviceId=\"" + deviceId + "\". Performing query.");
            }
            new GetRequest(mContext, mUrl, GetRequest.URI_CONNECTIONS, mApiKey, null, result -> {
                    /**
                     * We got connection status information for ALL devices instead of one.
                     * It does not hurt storing all of them.
                     */
                    Connections connections = mGson.fromJson(result, Connections.class);
                    calculateConnectionStats(connections);
                    for (Map.Entry<String, Connection> e : connections.connections.entrySet()) {
                        mRemoteCompletion.setDeviceStatus(
                                e.getKey(),             // deviceId
                                e.getValue()            // connection
                        );
                    }
            }, error -> {});
            new GetRequest(mContext, mUrl, GetRequest.URI_STATS_DEVICE, mApiKey, null, result -> {
                    /**
                     * We got the last seen timestamp for ALL devices - including the local device - instead of one.
                     * It does not hurt storing all of them.
                     */
                    if (result == null) {
                        Log.e(TAG, "getRemoteDeviceStatus: URI_STATS_DEVICE, result == null");
                        return;
                    }
                    JsonObject jsonObject = new JsonParser().parse(result).getAsJsonObject();
                    if (jsonObject == null) {
                        Log.e(TAG, "getRemoteDeviceStatus: URI_STATS_DEVICE, jsonObject == null");
                        return;
                    }
                    Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
                    for (Map.Entry<String, JsonElement> entry: entries) {
                        final String resultDeviceId = entry.getKey();
                        final DeviceStat deviceStat = mGson.fromJson(entry.getValue(), DeviceStat.class);
                        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                                .putString(Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + resultDeviceId, deviceStat.lastSeen)
                                .apply();
                    }
            }, error -> {});
        }
        return cacheEntry;
    }

    public final int getRemoteDeviceCompletion(
            final String deviceId) {
        return mRemoteCompletion.getDeviceCompletion(deviceId);
    }

    public final double getRemoteDeviceNeedBytes(
            final String deviceId) {
        return mRemoteCompletion.getDeviceNeedBytes(deviceId);
    }

    public final Connection getTotalConnectionStatistic() {
        if (!mPreviousConnections.isPresent()) {
            return new Connection();
        }
        return deepCopy(mPreviousConnections.get().total, Connection.class);
    }

    /**
     * Calculate transfer rates for each remote device connection and the "total device" stats.
     */
    private void calculateConnectionStats(Connections connections) {
        Long now = System.currentTimeMillis();
        Long msElapsed = now - mPreviousConnectionTime;
        if (msElapsed < Constants.REST_UPDATE_INTERVAL) {
            connections = deepCopy(mPreviousConnections.get(), Connections.class);
            return;
        }

        mPreviousConnectionTime = now;
        for (Map.Entry<String, Connection> e : connections.connections.entrySet()) {
            Connection prev =
                    (mPreviousConnections.isPresent() && mPreviousConnections.get().connections.containsKey(e.getKey()))
                            ? mPreviousConnections.get().connections.get(e.getKey())
                            : new Connection();
            e.getValue().setTransferRate(prev, msElapsed);
        }
        Connection prev =
                mPreviousConnections.transform(c -> c.total).or(new Connection());
        connections.total.setTransferRate(prev, msElapsed);
        mPreviousConnections = Optional.of(connections);
    }

    /**
     * Returns overall sync completion percentage representing all
     * currently running folder and device transfers.
     * Folder percentage means we are currently pulling changes from remotes.
     * Device percentage means remotes currently pull changes from us.
     * Uses cached stats instead of performing REST queries.
     */
    public int getTotalSyncCompletion() {
        int totalDeviceCompletion = mRemoteCompletion.getTotalDeviceCompletion();
        if (totalDeviceCompletion == -1) {
            // Total sync completion is not applicable because there are no devices or no devices are connected.
            return -1;
        }

        int totalFolderCompletion = mLocalCompletion.getTotalFolderCompletion();

        // Calculate overall sync completion percentage.
        int totalSyncCompletion;
        if (totalFolderCompletion == 100) {
            totalSyncCompletion = totalDeviceCompletion;
        } else {
            totalSyncCompletion = (int) Math.floor((double) (totalFolderCompletion + totalDeviceCompletion) / 2);
        }

        // Filter invalid percentage values.
        if (totalSyncCompletion < 0) {
            totalSyncCompletion = 0;
        } else if (totalSyncCompletion > 100) {
            totalSyncCompletion = 100;
        }
        /*
        LogV("getTotalSyncCompletion: totalSyncCompletion=" + Integer.toString(totalSyncCompletion) + "%, " +
                "folders=" + Integer.toString(totalFolderCompletion) + "%, " +
                "devices=" + Integer.toString(totalDeviceCompletion) + "%");
        */
        return totalSyncCompletion;
    }

    /**
     * Requests and parses information about recent changes.
     */
    public void getDiskEvents(int limit, OnResultListener1<List<DiskEvent>> listener) {
        new GetRequest(
                mContext, mUrl,
                GetRequest.URI_EVENTS_DISK, mApiKey,
                ImmutableMap.of("limit", Integer.toString(limit)),
                result -> {
                    List<DiskEvent> diskEvents = new ArrayList<>();
                    try {
                        JsonArray jsonDiskEvents = new JsonParser().parse(result).getAsJsonArray();
                        for (int i = jsonDiskEvents.size()-1; i >= 0; i--) {
                            JsonElement jsonDiskEvent = jsonDiskEvents.get(i);
                            diskEvents.add(mGson.fromJson(jsonDiskEvent, DiskEvent.class));
                        }
                        listener.onResult(diskEvents);
                    } catch (Exception e) {
                        Log.e(TAG, "getDiskEvents: Parsing REST API result failed. result=" + result);
                    }
                }, error -> {}
        );
    }

    /**
     * Listener for {@link #getEvents}.
     */
    public interface OnReceiveEventListener {
        void onError();

        /**
         * Called for each event.
         */
        void onEvent(Event event, JsonElement json);

        /**
         * Called after all available events have been processed.
         * @param lastId The id of the last event processed. Should be used as a starting point for
         *               the next round of event processing.
         */
        void onDone(long lastId);
    }

    /**
     * Retrieves the events that have accumulated since the given event id.
     *
     * The OnReceiveEventListeners onEvent method is called for each event.
     */
    public final void getEvents(final long sinceId, final long limit, final OnReceiveEventListener listener) {
        Map<String, String> params =
                ImmutableMap.of("since", String.valueOf(sinceId), "limit", String.valueOf(limit));
        new GetRequest(mContext, mUrl, GetRequest.URI_EVENTS, mApiKey, params, result -> {
            JsonArray jsonEvents = new JsonParser().parse(result).getAsJsonArray();
            long lastId = 0;

            for (int i = 0; i < jsonEvents.size(); i++) {
                JsonElement json = jsonEvents.get(i);
                try {
                    Event event = mGson.fromJson(json, Event.class);
                    if (lastId < event.id) {
                        lastId = event.id;
                    }
                    listener.onEvent(event, json);
                } catch (com.google.gson.JsonSyntaxException ex) {
                    Log.e(TAG, "getEvents: Skipping event due to JsonSyntaxException, raw=[" + json.toString() + "]");
                }
            }

            listener.onDone(lastId);
        }, error -> {
            listener.onError();
        });
    }

    /**
     * Returns status information about the folder with the given id from cache.
     */
    public final Map.Entry<FolderStatus, CachedFolderStatus> getFolderStatus (
            final String folderId) {
        final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = mLocalCompletion.getFolderStatus(folderId);
        if (cacheEntry.getKey().stateChanged.isEmpty()) {
            /**
             * Cache miss because we haven't received a "FolderSummary" event yet.
             * Query the required information so it will be available on a future call to this function.
             */
            LogV("getFolderStatus: Cache miss, folderId=\"" + folderId + "\". Performing query.");
            new GetRequest(mContext, mUrl, GetRequest.URI_DB_STATUS, mApiKey,
                    ImmutableMap.of("folder", folderId), result -> {
                final Folder folder = getFolderByID(folderId);
                if (folder == null) {
                    Log.e(TAG, "getFolderStatus#GetRequest#onResult: folderId == null");
                    return;
                }
                mLocalCompletion.setFolderStatus(
                        folderId,
                        folder.paused,
                        mGson.fromJson(result, FolderStatus.class)
                );
            }, error -> {});
        }
        return cacheEntry;
    }

    private void sendBroadcastToApps(Intent intent) {
        String[] packageIdList = {
            // "com.example.syncthingreceiver",
            "org.decsync.cc"
        };
        // intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        for (String packageId : packageIdList) {
            intent.setPackage(packageId);
            ((SyncthingApp) mContext.getApplicationContext()).sendBroadcast(intent, PERMISSION_RECEIVE_SYNC_STATUS);
        }
    }

    public void sendBroadcastFolderSyncComplete(String deviceId, 
                                                    final Folder folder, 
                                                    final String folderState) {
        Intent intent = new Intent();
        intent.setAction(ACTION_NOTIFY_FOLDER_SYNC_COMPLETE);
        intent.putExtra("deviceId", deviceId);
        intent.putExtra("folderId", folder.id);
        intent.putExtra("folderLabel", folder.label);
        intent.putExtra("folderPath", folder.path);
        intent.putExtra("folderState", folderState);
        sendBroadcastToApps(intent);
    }

    /**
     * Updates cached folder and device completion info according to event data.
     */
    public void setLocalFolderStatus(final String folderId,
                                            final FolderStatus folderStatus) {
        mLocalCompletion.setFolderStatus(folderId, folderStatus);
        onTotalSyncCompletionChange();
    }

    public void setLocalFolderLastItemFinished(final String folderId,
                                                    final String lastItemFinishedAction,
                                                    final String lastItemFinishedItem,
                                                    final String lastItemFinishedTime) {
        /**
         * lastItemFinishedAction RAW data from Syncthing
         *  update:     A file was changed or deleted
         */
        String realLastItemFinishedAction = lastItemFinishedAction;

        // Check if the file was updated or deleted in reality.
        if (lastItemFinishedAction.equals("update")) {
            Folder folder = getFolderByID(folderId);
            if (!(folder == null || folder.path == null)) {
                boolean fileExists = (new File (folder.path + "/" + lastItemFinishedItem)).exists();
                if (!fileExists) {
                    realLastItemFinishedAction = "delete";
                }
            }
        }
        mLocalCompletion.setLastItemFinished(
                folderId,
                realLastItemFinishedAction,
                lastItemFinishedItem,
                lastItemFinishedTime
        );
    }

    public void setRemoteCompletionInfo(final String deviceId,
                                            final String folderId,
                                            final Double needBytes,
                                            final Double completion) {
        final Folder folder = getFolderByID(folderId);
        if (folder == null) {
            Log.e(TAG, "setRemoteCompletionInfo: folderId == null");
            return;
        }
        RemoteCompletionInfo remoteCompletionInfo = new RemoteCompletionInfo();
        if (folder.paused) {
            /**
             * Fixes issue where device sync percentage is displayed 50% on wrapper UI
             * and 100% on Web UI if there are at least two folders syncing with the same device and
             * at least one of them is paused. This is caused by EventProcessor telling us a paused
             * to be 0% complete. To get consistent UI output, we assume 100% completion for paused
             * folders.
            **/
            LogV("setRemoteCompletionInfo: Paused folder \"" + folderId + "\" - got " +
                    remoteCompletionInfo.completion + "%, passing on 100%");
            remoteCompletionInfo.completion = 100;
            remoteCompletionInfo.needBytes = 0;
        } else {
            remoteCompletionInfo.completion = completion;
            remoteCompletionInfo.needBytes = needBytes;
        }
        mRemoteCompletion.setCompletionInfo(deviceId, folderId, remoteCompletionInfo);
        onTotalSyncCompletionChange();

        /**
         * Check if a folder completed synchronization on the local or a remote device.
         * Plan finisher workloads that need to run after folder completion.
         * They will be offloaded to a separate thread later.
        **/
        Boolean planGetSyncConflictFiles = false;
        Boolean planOnFolderSyncCompleted = false;

        final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = mLocalCompletion.getFolderStatus(folderId);
        final FolderStatus folderStatus =  cacheEntry.getKey();
        final Boolean folderIsSyncing = folderStatus.state.contains("sync");
        if (remoteCompletionInfo.completion == 100) {
            if (!folderIsSyncing) {
                planGetSyncConflictFiles = true;

                final CachedFolderStatus cachedFolderStatus = cacheEntry.getValue();
                if (cachedFolderStatus.remoteIndexUpdated) {
                    mLocalCompletion.setRemoteIndexUpdated(folderId, false);
                    planOnFolderSyncCompleted = true;
                }
            }
        }

        // Execute planned workloads.
        final Boolean finalPlanGetSyncConflictFiles = planGetSyncConflictFiles;
        final Boolean finalPlanOnFolderSyncCompleted = planOnFolderSyncCompleted;
        if (executorService.isShutdown()) {
            // We are on the way to shutdown SynchtingNative.
            return;
        }
        if (!finalPlanGetSyncConflictFiles && !finalPlanOnFolderSyncCompleted) {
            // No work to do.
            return;
        }

        executorService.execute(() -> {
            if (finalPlanGetSyncConflictFiles) {
                // Check for ".sync-conflict-YYYYMMDD-HHMMSS-DEVICEI*" files.
                mLocalCompletion.setDiscoveredConflictFiles(
                        folderId,
                        Util.getSyncConflictFiles(folder.path)
                );
            }

            if (finalPlanOnFolderSyncCompleted) {
                onFolderSyncCompleted(
                        folder, 
                        folderStatus.state, 
                        deviceId
                );
            }
        });
    }

    public void onFolderSyncCompleted(final Folder folder, 
                                            final String folderState, 
                                            final String deviceId) {
        Log.d(TAG, "onFolderSyncCompleted: Completed folder=[" + folder.id + "]");

        // Run folder script set if enabled by user pref.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        Boolean folderRunScriptEnabled = sharedPreferences.getBoolean(
            Constants.DYN_PREF_OBJECT_FOLDER_RUN_SCRIPT(folder.id), false
        );
        if (folderRunScriptEnabled) {
            Util.runScriptSet(
                    folder.path + "/" + Constants.FILENAME_STFOLDER, 
                    new String[]{
                            "sync_complete"
                    }
            );
        }

        // Notify listening third-party apps.
        sendBroadcastFolderSyncComplete(deviceId, folder, folderState);
    }

    public void setRemoteIndexUpdated(final String deviceId,
                                            final String folderId,
                                            final boolean remoteIndexUpdated) {
        mLocalCompletion.setRemoteIndexUpdated(folderId, remoteIndexUpdated);
    }

    public void updateLocalFolderPause(final String folderId, final Boolean newPaused) {
        // Clear status cache when pausing or resuming the folder.
        mLocalCompletion.setFolderStatus(folderId, newPaused, new FolderStatus());
    }

    public void updateLocalFolderState(final String folderId, final String newState) {
        final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = mLocalCompletion.getFolderStatus(folderId);
        cacheEntry.getKey().state = newState;
        mLocalCompletion.setFolderStatus(folderId, cacheEntry.getKey());
    }

    public void updateRemoteDeviceConnected(final String deviceId, final Boolean newConnected) {
        Connection cacheEntry = mRemoteCompletion.getDeviceStatus(deviceId);
        cacheEntry.connected = newConnected;
        mRemoteCompletion.setDeviceStatus(deviceId, cacheEntry);
        onTotalSyncCompletionChange();
    }

    public void updateRemoteDevicePaused(final String deviceId, final Boolean newPaused) {
        Connection cacheEntry = mRemoteCompletion.getDeviceStatus(deviceId);
        cacheEntry.connected = false;
        cacheEntry.paused = newPaused;
        mRemoteCompletion.setDeviceStatus(deviceId, cacheEntry);
        onTotalSyncCompletionChange();
    }

    /**
     * Returns prettyfied usage report.
     */
    public void getUsageReport(final OnResultListener1<String> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_REPORT, mApiKey, null, result -> {
            JsonElement json = new JsonParser().parse(result);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            listener.onResult(gson.toJson(json));
        }, error -> {});
    }

    public String getApiKey() {
        return mApiKey;
    }

    public URL getUrl() {
        return mUrl;
    }

    public Boolean isUsageReportingAccepted() {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "isUsageReportingAccepted called while options == null");
            return false;
        }
        return options.isUsageReportingAccepted(mUrVersionMax);
    }

    public Boolean isUsageReportingDecided() {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "isUsageReportingDecided called while options == null");
            return true;
        }
        return options.isUsageReportingDecided(mUrVersionMax);
    }

    public void setUsageReporting(Boolean acceptUsageReporting) {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "setUsageReporting called while options == null");
            return;
        }
        options.urAccepted = acceptUsageReporting ? mUrVersionMax : Options.USAGE_REPORTING_DENIED;
        synchronized (mConfigLock) {
            mConfig.options = options;
        }
    }

    public void downloadSupportBundle(File targetFile, final OnResultListener1<Boolean> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DEBUG_SUPPORT, mApiKey, null, result -> {
            Boolean failSuccess = true;
            LogV("downloadSupportBundle: Writing '" + targetFile.getPath() + "' ...");
            FileOutputStream fileOutputStream = null;
            try {
                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
                fileOutputStream = new FileOutputStream(targetFile);
                fileOutputStream.write(result.getBytes(StandardCharsets.ISO_8859_1));  // Do not use UTF-8 here because the ZIP would be corrupted.
                fileOutputStream.flush();
            } catch (IOException e) {
                Log.w(TAG, "downloadSupportBundle: Failed to write '" + targetFile.getPath() + "' #1", e);
                failSuccess = false;
            } finally {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "downloadSupportBundle: Failed to write '" + targetFile.getPath() + "' #2", e);
                    failSuccess = false;
                }
            }
            if (listener != null) {
                listener.onResult(failSuccess);
            }
        }, error -> {});
    }

    /**
     * Event triggered by {@link RunConditionMonitor} routed here through {@link SyncthingService}.
     */
    public void applyCustomRunConditions(RunConditionMonitor runConditionMonitor) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        synchronized (mConfigLock) {
            Boolean configChanged = false;

            // Check if the config has been loaded.
            if (mConfig == null) {
                Log.w(TAG, "applyCustomRunConditions: mConfig is not ready yet.");
                return;
            }

            // Check if the folders are available from config.
            if (mConfig.folders != null) {
                for (Folder folder : mConfig.folders) {
                    // LogV("applyCustomRunConditions: Processing config of folder(" + folder.label + ")");
                    Boolean folderCustomSyncConditionsEnabled = sharedPreferences.getBoolean(
                        Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id), false
                    );
                    if (folderCustomSyncConditionsEnabled) {
                        Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                            Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id
                        );
                        LogV("applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? "1" : "0"));
                        if (folder.paused != !syncConditionsMet) {
                            folder.paused = !syncConditionsMet;
                            Log.d(TAG, "applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? ">1" : ">0"));
                            configChanged = true;
                        }
                    }
                }
            } else {
                Log.d(TAG, "applyCustomRunConditions: mConfig.folders is not ready yet.");
                return;
            }

            // Check if the devices are available from config.
            if (mConfig.devices != null) {
                for (Device device : mConfig.devices) {
                    // LogV("applyCustomRunConditions: Processing config of device(" + device.name + ")");
                    Boolean deviceCustomSyncConditionsEnabled = sharedPreferences.getBoolean(
                        Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID), false
                    );
                    if (deviceCustomSyncConditionsEnabled) {
                        Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                            Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID
                        );
                        LogV("applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? "1" : "0"));
                        if (device.paused != !syncConditionsMet) {
                            device.paused = !syncConditionsMet;
                            Log.d(TAG, "applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? ">1" : ">0"));
                            configChanged = true;
                        }
                    }
                }
            } else {
                Log.d(TAG, "applyCustomRunConditions: mConfig.devices is not ready yet.");
                return;
            }

            if (configChanged) {
                LogV("applyCustomRunConditions: Sending changed config ...");
                sendConfig();
            } else {
                LogV("applyCustomRunConditions: No action was necessary.");
            }
        }
    }

    private void setVersioningCleanupIntervalS (Integer cleanupIntervalS) {
        synchronized (mConfigLock) {
            for (Folder folder : mConfig.folders) {
                folder.versioning.cleanupIntervalS = cleanupIntervalS;
            }
            LogV("Set VersioningCleanupIntervalS to " + cleanupIntervalS);
            sendConfig();
        }
        return;
    }

    private void onTotalSyncCompletionChange() {
        // LogV("onTotalSyncCompletionChange fired.");
        if (mNotificationHandler == null) {
            return;
        }

        int onlineDeviceCount = mRemoteCompletion.getOnlineDeviceCount();
        int totalSyncCompletion = getTotalSyncCompletion();
        if ((onlineDeviceCount == mLastOnlineDeviceCount) &&
                (totalSyncCompletion == mLastTotalSyncCompletion)) {
            return;
        }
        mNotificationHandler.updatePersistentNotification(
                (SyncthingService) mContext,
                false,                                              // Do not persist previous notification text.
                onlineDeviceCount,
                totalSyncCompletion
        );
        mLastOnlineDeviceCount = onlineDeviceCount;
        mLastTotalSyncCompletion = totalSyncCompletion;
    }

    private Gson getGson() {
        Gson gson = new GsonBuilder()
                .create();
        return gson;
    }

    private String jsonToPrettyFormat(String jsonString) {
        JsonObject json = (new JsonParser()).parse(jsonString).getAsJsonObject();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(json);
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

    private void LogVMultipleLines(String logMessage) {
        final int MAX_CHARS_PER_LOG_LINE = 4000;
        if (!ENABLE_VERBOSE_LOG) {
            return;
        }
        if (logMessage.length() <= MAX_CHARS_PER_LOG_LINE) {
            LogV(logMessage);
            return;
        }
        LogV("*** Multiple line log START ***");
        int chunkCount = logMessage.length() / MAX_CHARS_PER_LOG_LINE;
        for (int i = 0; i <= chunkCount; i++) {
            int max = MAX_CHARS_PER_LOG_LINE * (i + 1);
            if (max >= logMessage.length()) {
                LogV(logMessage.substring(MAX_CHARS_PER_LOG_LINE * i));
                continue;
            }
            LogV(logMessage.substring(MAX_CHARS_PER_LOG_LINE * i, max));
        }
        LogV("*** Multiple line log END ***");
    }
}
