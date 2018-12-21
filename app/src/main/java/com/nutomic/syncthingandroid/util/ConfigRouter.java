package com.nutomic.syncthingandroid.util;

import android.content.Context;
// import android.util.Log;

import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
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

    public void updateFolder(RestApi restApi, final Folder folder) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.updateFolder(folder);
            configXml.saveChanges();
        }

        // Syncthing is running and REST API is available.
        restApi.updateFolder(folder);       // This will send the config afterwards.
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

    public List<Device> getDevices(RestApi restApi, Boolean includeLocal) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            return configXml.getDevices(includeLocal);
        }

        // Syncthing is running and REST API is available.
        return restApi.getDevices(includeLocal);
    }

    public void updateDevice(RestApi restApi, final Device device) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running or REST API is not (yet) available.
            configXml.loadConfig();
            configXml.updateDevice(device);
            configXml.saveChanges();
        }

        // Syncthing is running and REST API is available.
        restApi.updateDevice(device);       // This will send the config afterwards.
    }

}
