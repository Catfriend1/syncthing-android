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
import com.nutomic.syncthingandroid.activities.RestartActivity;
import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.http.GetRequest;
import com.nutomic.syncthingandroid.http.PostConfigRequest;
import com.nutomic.syncthingandroid.http.PostScanRequest;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.Model;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

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
    private boolean mRestartPostponed = false;

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
     * Stores the latest result of {@link #getModel} for each folder, for calculating device
     * percentage in {@link #getConnections}.
     */
    private final HashMap<String, Model> mCachedModelInfo = new HashMap<>();

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

    /**
     * Number of previous calls to {@link #tryIsAvailable()}.
     */
    private final AtomicInteger mAvailableCount = new AtomicInteger(0);

    /**
     * Number of asynchronous calls performed in {@link #onWebGuiAvailable()}.
     */
    private static final int TOTAL_STARTUP_CALLS = 3;

    public interface OnApiAvailableListener {
        void onApiAvailable();
    }

    private final OnApiAvailableListener mOnApiAvailableListener;

    private final OnConfigChangedListener mOnConfigChangedListener;

    /**
     * Gets local device ID, syncthing version and config, then calls all OnApiAvailableListeners.
     */
    @Override
    public void onWebGuiAvailable() {
        mAvailableCount.set(0);
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            mVersion = json.get("version").getAsString();
            Log.i(TAG, "Syncthing version is " + mVersion);
            tryIsAvailable();
        });
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, result -> {
            Log.v(TAG, "onWebGuiAvailable: " + result);
            mConfig = new Gson().fromJson(result, Config.class);
            if (mConfig == null) {
                throw new RuntimeException("config is null: " + result);
            }
            tryIsAvailable();
        });
        getSystemInfo(info -> {
            mLocalDeviceId = info.myID;
            tryIsAvailable();
        });
    }

    public void reloadConfig() {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, result -> {
            Log.v(TAG, "reloadConfig: " + result);
            mConfig = new Gson().fromJson(result, Config.class);
            if (mConfig == null) {
                throw new RuntimeException("config is null: " + result);
            }
        });
    }

    /**
     * Increments mAvailableCount by one, and, if it reached TOTAL_STARTUP_CALLS,
     * calls {@link SyncthingService#onApiChange}.
     */
    private void tryIsAvailable() {
        int value = mAvailableCount.incrementAndGet();
        if (BuildConfig.DEBUG && value > TOTAL_STARTUP_CALLS) {
            throw new AssertionError("Too many startup calls");
        }
        if (value == TOTAL_STARTUP_CALLS) {
            mOnApiAvailableListener.onApiAvailable();
        }
    }

    /**
     * Either shows a restart dialog, or only updates the config, depending on
     * {@link #mRestartPostponed}.
     */
    public void showRestartDialog(Activity activity) {
        if (mRestartPostponed) {
            sendConfig();
        } else {
            activity.startActivity(new Intent(mContext, RestartActivity.class));
        }
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Sends current config to Syncthing.
     */
    private void sendConfig() {
        new PostConfigRequest(mContext, mUrl, mApiKey, new Gson().toJson(mConfig), null);
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
    }

    public void shutdown() {
        mNotificationHandler.cancelRestartNotification();
        mRestartPostponed = false;
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
        for (Device d : getDevices(true)) {
            if (d.deviceID.equals(mLocalDeviceId)) {
                return deepCopy(d, Device.class);
            }
        }
        throw new RuntimeException();
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

    public void editSettings(Config.Gui newGui, Options newOptions, Activity activity) {
        mConfig.gui = newGui;
        mConfig.options = newOptions;
        showRestartDialog(activity);
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
                e.getValue().completion = getDeviceCompletion(e.getKey());

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
     * Calculates completion percentage for the given device using {@link #mCachedModelInfo}.
     */
    private int getDeviceCompletion(String deviceId) {
        int folderCount = 0;
        float percentageSum = 0;
        // Syncthing UI limits pending deletes to 95% completion of a device
        int maxPercentage = 100;
        for (Map.Entry<String, Model> modelInfo : mCachedModelInfo.entrySet()) {
            boolean isShared = false;
            for (Folder r : getFolders()) {
                if (r.getDevice(deviceId) != null) {
                    isShared = true;
                    break;
                }
            }
            if (isShared) {
                long global = modelInfo.getValue().globalBytes;
                long local = modelInfo.getValue().inSyncBytes;
                if (modelInfo.getValue().needFiles == 0 && modelInfo.getValue().needDeletes > 0)
                    maxPercentage = 95;
                percentageSum += (global != 0)
                        ? (local * 100f) / global
                        : 100f;
                folderCount++;
            }
        }
        return (folderCount != 0)
                ? Math.min(Math.round(percentageSum / folderCount), maxPercentage)
                : 100;
    }

    /**
     * Returns status information about the folder with the given id.
     */
    public void getModel(final String folderId, final OnResultListener2<String, Model> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_MODEL, mApiKey,
                    ImmutableMap.of("folder", folderId), result -> {
            Model m = new Gson().fromJson(result, Model.class);
            mCachedModelInfo.put(folderId, m);
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
     * Returns prettyfied usage report.
     */
    public void getUsageReport(final OnResultListener1<String> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_REPORT, mApiKey, null, result -> {
            JsonElement json = new JsonParser().parse(result);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            listener.onResult(gson.toJson(json));
        });
    }

    public void setRestartPostponed() {
        mRestartPostponed = true;
    }

    public URL getUrl() {
        return mUrl;
    }
}
