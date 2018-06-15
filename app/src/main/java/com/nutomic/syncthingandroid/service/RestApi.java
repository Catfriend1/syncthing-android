package com.nutomic.syncthingandroid.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

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
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.http.GetRequest;
import com.nutomic.syncthingandroid.http.PostConfigRequest;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Completion;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.Constants;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi {

    private static final String TAG = "RestApi";

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

    public interface OnResultListener2<T, R> {
        void onResult(T t, R r);
    }

    private final Context mContext;
    private final URL mUrl;
    private final String mApiKey;

    private String mVersion;
    private Config mConfig;
    private String mLocalDeviceId;

    /**
     * Stores the result of the last successful request to {@link GetRequest#URI_CONNECTIONS},
     * or an empty Map.
     */
    private Optional<Connections> mPreviousConnections = Optional.absent();

    /**
     * Stores the timestamp of the last successful request to {@link GetRequest#URI_CONNECTIONS}.
     */
    private long mPreviousConnectionTime = 0;

    /**
     * In the last-finishing {@link readConfigFromRestApi} callback, we have to call
     * {@link SyncthingService#onApiAvailable} to indicate that the RestApi class is fully initialized.
     * We do this to avoid getting stuck with our main thread due to synchronous REST queries.
     * The correct indication of full initialisation is crucial to stability as other listeners of
     * {@link SettingsActivity#onServiceStateChange} needs cached config and system information available.
     * e.g. SettingsFragment need "mLocalDeviceId"
     */
    private Boolean asyncQueryConfigComplete = false;
    private Boolean asyncQueryVersionComplete = false;
    private Boolean asyncQuerySystemInfoComplete = false;

    /**
     * Object that must be locked upon accessing the following variables:
     * asyncQueryConfigComplete, asyncQueryVersionComplete, asyncQuerySystemInfoComplete
     */
    private final Object mAsyncQueryCompleteLock = new Object();

    /**
     * Stores the latest result of {@link #getFolderStatus} for each folder
     */
    private HashMap<String, FolderStatus> mCachedFolderStatuses = new HashMap<>();

    /**
     * Stores the latest result of device and folder completion events.
     */
    private Completion mCompletion = new Completion();

    @Inject NotificationHandler mNotificationHandler;

    public RestApi(Context context, URL url, String apiKey, OnApiAvailableListener apiListener,
                   OnConfigChangedListener configListener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mUrl = url;
        mApiKey = apiKey;
        mOnApiAvailableListener = apiListener;
        mOnConfigChangedListener = configListener;
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
        Log.v(TAG, "Reading config from REST ...");
        synchronized (mAsyncQueryCompleteLock) {
            asyncQueryVersionComplete = false;
            asyncQueryConfigComplete = false;
            asyncQuerySystemInfoComplete = false;
        }
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            mVersion = json.get("version").getAsString();
            Log.i(TAG, "Syncthing version is " + mVersion);
            updateDebugFacilitiesCache();
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryVersionComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, result -> {
            onReloadConfigComplete(result);
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryConfigComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
        getSystemInfo(info -> {
            mLocalDeviceId = info.myID;
            synchronized (mAsyncQueryCompleteLock) {
                asyncQuerySystemInfoComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
    }

    private void checkReadConfigFromRestApiCompleted() {
        if (asyncQueryVersionComplete && asyncQueryConfigComplete && asyncQuerySystemInfoComplete) {
            Log.v(TAG, "Reading config from REST completed.");
            mOnApiAvailableListener.onApiAvailable();
        }
    }

    public void reloadConfig() {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, this::onReloadConfigComplete);
    }

    private void onReloadConfigComplete(String result) {
        mConfig = new Gson().fromJson(result, Config.class);
        if (mConfig == null) {
            throw new RuntimeException("config is null: " + result);
        }
        Log.v(TAG, "onReloadConfigComplete: Successfully parsed configuration.");

        // Update cached device and folder information stored in the mCompletion model.
        mCompletion.updateFromConfig(getDevices(true), getFolders());
    }

    /**
     * Queries debug facilities available from the currently running syncthing binary
     * if the syncthing binary version changed. First launch of the binary is also
     * considered as a version change.
     * Precondition: {@link #mVersion} read from REST
     */
    private void updateDebugFacilitiesCache() {
        final String PREF_LAST_BINARY_VERSION = "lastBinaryVersion";
        if (!mVersion.equals(PreferenceManager.getDefaultSharedPreferences(mContext).getString(PREF_LAST_BINARY_VERSION, ""))) {
            // First binary launch or binary upgraded case.
            new GetRequest(mContext, mUrl, GetRequest.URI_DEBUG, mApiKey, null, result -> {
                try {
                    Set<String> facilitiesToStore = new HashSet<String>();
                    JsonObject json = new JsonParser().parse(result).getAsJsonObject();
                    JsonObject jsonFacilities = json.getAsJsonObject("facilities");
                    for (String facilityName : jsonFacilities.keySet()) {
                        facilitiesToStore.add(facilityName);
                    }
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putStringSet(Constants.PREF_STTRACE_AVAILABLE_OPTIONS, facilitiesToStore)
                        .apply();

                    // Store current binary version so we will only store this information again
                    // after a binary update.
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putString(PREF_LAST_BINARY_VERSION, mVersion)
                        .apply();
                } catch (Exception e) {
                    Log.w(TAG, "updateDebugFacilitiesCache: Failed to get debug facilities. result=" + result);
                }
            });
        }
    }

    /**
     * Sends current config to Syncthing.
     * Will result in a "ConfigSaved" event.
     * EventProcessor will trigger this.reloadConfig().
     */
    private void sendConfig() {
        new PostConfigRequest(mContext, mUrl, mApiKey, new Gson().toJson(mConfig), null);
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Sends current config and restarts Syncthing.
     */
    public void restart() {
        new PostConfigRequest(mContext, mUrl, mApiKey, new Gson().toJson(mConfig), result -> {
            Intent intent = new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART);
            mContext.startService(intent);
        });
        mOnConfigChangedListener.onConfigChanged();
    }

    public void shutdown() {
        mNotificationHandler.cancelRestartNotification();
    }

    /**
     * Returns the version name, or a (text) error message on failure.
     */
    public String getVersion() {
        return mVersion;
    }

    public List<Folder> getFolders() {
        List<Folder> folders = deepCopy(mConfig.folders, new TypeToken<List<Folder>>(){}.getType());
        Collections.sort(folders, FOLDERS_COMPARATOR);
        return folders;
    }

    public void addFolder(Folder folder) {
        mConfig.folders.add(folder);
        sendConfig();
    }

    public void editFolder(Folder newFolder) {
        removeFolderInternal(newFolder.id);
        addFolder(newFolder);
    }

    public void removeFolder(String id) {
        removeFolderInternal(id);
        // mCompletion will be updated after the ConfigSaved event.
        sendConfig();
        // Remove saved data from share activity for this folder.
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(ShareActivity.PREF_FOLDER_SAVED_SUBDIRECTORY+id)
                .apply();
    }

    private void removeFolderInternal(String id) {
        Iterator<Folder> it = mConfig.folders.iterator();
        while (it.hasNext()) {
            Folder f = it.next();
            if (f.id.equals(id)) {
                it.remove();
            }
        }
    }

    /**
     * Returns a list of all existing devices.
     *
     * @param includeLocal True if the local device should be included in the result.
     */
    public List<Device> getDevices(boolean includeLocal) {
        List<Device> devices = deepCopy(mConfig.devices, new TypeToken<List<Device>>(){}.getType());

        Iterator<Device> it = devices.iterator();
        while (it.hasNext()) {
            Device device = it.next();
            boolean isLocalDevice = Objects.equal(mLocalDeviceId, device.deviceID);
            if (!includeLocal && isLocalDevice)
                it.remove();
        }
        return devices;
    }

    public Device getLocalDevice() {
        List<Device> devices = getDevices(true);
        if (devices.isEmpty()) {
            throw new RuntimeException("RestApi.getLocalDevice: devices is empty.");
        }
        Log.v(TAG, "getLocalDevice: Looking for local device ID " + mLocalDeviceId);
        for (Device d : devices) {
            if (d.deviceID.equals(mLocalDeviceId)) {
                return deepCopy(d, Device.class);
            }
        }
        throw new RuntimeException("RestApi.getLocalDevice: Failed to get the local device crucial to continuing execution.");
    }

    public void addDevice(Device device, OnResultListener1<String> errorListener) {
        normalizeDeviceId(device.deviceID, normalizedId -> {
            mConfig.devices.add(device);
            sendConfig();
        }, errorListener);
    }

    public void editDevice(Device newDevice) {
        removeDeviceInternal(newDevice.deviceID);
        mConfig.devices.add(newDevice);
        sendConfig();
    }

    public void removeDevice(String deviceId) {
        removeDeviceInternal(deviceId);
        // mCompletion will be updated after the ConfigSaved event.
        sendConfig();
    }

    private void removeDeviceInternal(String deviceId) {
        Iterator<Device> it = mConfig.devices.iterator();
        while (it.hasNext()) {
            Device d = it.next();
            if (d.deviceID.equals(deviceId)) {
                it.remove();
            }
        }
    }

    public Options getOptions() {
        return deepCopy(mConfig.options, Options.class);
    }

    public Config.Gui getGui() {
        return deepCopy(mConfig.gui, Config.Gui.class);
    }

    public void editSettings(Config.Gui newGui, Options newOptions) {
        mConfig.gui = newGui;
        mConfig.options = newOptions;
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
    public void getSystemInfo(OnResultListener1<SystemInfo> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM, mApiKey, null, result ->
                listener.onResult(new Gson().fromJson(result, SystemInfo.class)));
    }

    public boolean isConfigLoaded() {
        return mConfig != null;
    }

    /**
     * Requests and parses system version information.
     */
    public void getSystemVersion(OnResultListener1<SystemVersion> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            SystemVersion systemVersion = new Gson().fromJson(result, SystemVersion.class);
            listener.onResult(systemVersion);
        });
    }

    /**
     * Returns connection info for the local device and all connected devices.
     */
    public void getConnections(final OnResultListener1<Connections> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONNECTIONS, mApiKey, null, result -> {
            Long now = System.currentTimeMillis();
            Long msElapsed = now - mPreviousConnectionTime;
            if (msElapsed < Constants.GUI_UPDATE_INTERVAL) {
                listener.onResult(deepCopy(mPreviousConnections.get(), Connections.class));
                return;
            }

            mPreviousConnectionTime = now;
            Connections connections = new Gson().fromJson(result, Connections.class);
            for (Map.Entry<String, Connections.Connection> e : connections.connections.entrySet()) {
                e.getValue().completion = mCompletion.getDeviceCompletion(e.getKey());

                Connections.Connection prev =
                        (mPreviousConnections.isPresent() && mPreviousConnections.get().connections.containsKey(e.getKey()))
                                ? mPreviousConnections.get().connections.get(e.getKey())
                                : new Connections.Connection();
                e.getValue().setTransferRate(prev, msElapsed);
            }
            Connections.Connection prev =
                    mPreviousConnections.transform(c -> c.total).or(new Connections.Connection());
            connections.total.setTransferRate(prev, msElapsed);
            mPreviousConnections = Optional.of(connections);
            listener.onResult(deepCopy(connections, Connections.class));
        });
    }

    /**
     * Returns status information about the folder with the given id.
     */
    public void getFolderStatus(final String folderId, final OnResultListener2<String, FolderStatus> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_STATUS, mApiKey,
                    ImmutableMap.of("folder", folderId), result -> {
            FolderStatus m = new Gson().fromJson(result, FolderStatus.class);
            mCachedFolderStatuses.put(folderId, m);
            listener.onResult(folderId, m);
        });
    }

    /**
     * Listener for {@link #getEvents}.
     */
    public interface OnReceiveEventListener {
        /**
         * Called for each event.
         */
        void onEvent(Event event);

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
                Event event = new Gson().fromJson(json, Event.class);

                if (lastId < event.id)
                    lastId = event.id;

                listener.onEvent(event);
            }

            listener.onDone(lastId);
        });
    }

    /**
     * Normalizes a given device ID.
     */
    private void normalizeDeviceId(String id, OnResultListener1<String> listener,
                                   OnResultListener1<String> errorListener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DEVICEID, mApiKey,
                ImmutableMap.of("id", id), result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            JsonElement normalizedId = json.get("id");
            JsonElement error = json.get("error");
            if (normalizedId != null)
                listener.onResult(normalizedId.getAsString());
            if (error != null)
                errorListener.onResult(error.getAsString());
        });
    }


    /**
     * Updates cached folder and device completion info according to event data.
     */
    public void setCompletionInfo(String deviceId, String folderId, CompletionInfo completionInfo) {
        mCompletion.setCompletionInfo(deviceId, folderId, completionInfo);
    }

    /**
     * Returns prettyfied usage report.
     */
    public void getUsageReport(final OnResultListener1<String> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_REPORT, mApiKey, null, result -> {
            JsonElement json = new JsonParser().parse(result);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            listener.onResult(gson.toJson(json));
        });
    }

    public URL getUrl() {
        return mUrl;
    }
}
