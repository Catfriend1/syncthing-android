package com.nutomic.syncthingandroid.util;

import android.content.Context;
// import android.util.Log;

import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.util.List;

/**
 * Provides a transparent access to the config if ...
 * a) Syncthing is running and REST API is available.
 * b) Syncthing is NOT running and config.xml is accessed.
 */
public class ConfigRouter {

    private static final String TAG = "ConfigRouter";

    public interface OnResultListener1<T> {
        void onResult(T t);
    }

    private final Context mContext;

    private ConfigXml configXml;

    public ConfigRouter(Context context) {
        mContext = context;
        configXml = new ConfigXml(mContext);
    }

    public List<Folder> getFolders(RestApi restApi) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            return configXml.getFolders();
        }

        // Syncthing is running and REST API is available.
        return restApi.getFolders();
    }

    public void addFolder(RestApi restApi, Folder folder) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.addFolder(folder);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.addFolder(folder);       // This will send the config afterwards.
    }

    public void updateFolder(RestApi restApi, final Folder folder) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.updateFolder(folder);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.updateFolder(folder);       // This will send the config afterwards.
    }

    public void removeFolder(RestApi restApi, final String folderId) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.removeFolder(folderId);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.removeFolder(folderId);       // This will send the config afterwards.
    }

    /**
     * Gets ignore list for given folder.
     */
    public void getFolderIgnoreList(RestApi restApi, Folder folder, OnResultListener1<FolderIgnoreList> listener) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.getFolderIgnoreList(folder, folderIgnoreList -> listener.onResult(folderIgnoreList));
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.getFolderIgnoreList(folder.id, folderIgnoreList -> listener.onResult(folderIgnoreList));
    }

    /**
     * Stores ignore list for given folder.
     */
    public void postFolderIgnoreList(RestApi restApi, Folder folder, String[] ignore) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.postFolderIgnoreList(folder, ignore);
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.postFolderIgnoreList(folder.id, ignore);
    }

    public List<Device> getDevices(RestApi restApi, Boolean includeLocal) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            return configXml.getDevices(includeLocal);
        }

        // Syncthing is running and REST API is available.
        return restApi.getDevices(includeLocal);
    }

    public void addDevice(RestApi restApi, Device device) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.addDevice(device);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.addDevice(device);       // This will send the config afterwards.
    }

    public void updateDevice(RestApi restApi, final Device device) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.updateDevice(device);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.updateDevice(device);       // This will send the config afterwards.
    }

    public void removeDevice(RestApi restApi, final String deviceID) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.removeDevice(deviceID);
            configXml.saveChanges();
            return;
        }

        // Syncthing is running and REST API is available.
        restApi.removeDevice(deviceID);       // This will send the config afterwards.
    }

    public Options getOptions(RestApi restApi) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            return configXml.getOptions();
        }

        // Syncthing is running and REST API is available.
        return restApi.getOptions();
    }

}
