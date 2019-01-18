package com.nutomic.syncthingandroid.model;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class caches remote folder and device synchronization
 * completion indicators defined in {@link CompletionInfo}
 * according to syncthing's REST "/completion" JSON result schema.
 * Completion model of syncthing's web UI is completion[deviceId][folderId]
 */
public class Completion {

    private static final String TAG = "Completion";

    private static final Boolean ENABLE_VERBOSE_LOG = false;

    HashMap<String, HashMap<String, CompletionInfo>> deviceFolderMap =
        new HashMap<String, HashMap<String, CompletionInfo>>();

    /**
     * Removes a folder from the cache model.
     */
    private void removeFolder(String folderId) {
        for (HashMap<String, CompletionInfo> folderMap : deviceFolderMap.values()) {
            if (folderMap.containsKey(folderId)) {
                folderMap.remove(folderId);
                break;
            }
        }
    }

    /**
     * Updates device and folder information in the cache model
     * after a config update.
     */
    public void updateFromConfig(List<Device> newDevices, List<Folder> newFolders) {
        HashMap<String, CompletionInfo> folderMap;

        // Handle devices that were removed from the config.
        List<String> removedDevices = new ArrayList<>();
        Boolean deviceFound;
        for (String deviceId : deviceFolderMap.keySet()) {
            deviceFound = false;
            for (Device device : newDevices) {
                if (device.deviceID.equals(deviceId)) {
                    deviceFound = true;
                    break;
                }
            }
            if (!deviceFound) {
                removedDevices.add(deviceId);
            }
        }
        for (String deviceId : removedDevices) {
            if (ENABLE_VERBOSE_LOG) {
                Log.v(TAG, "updateFromConfig: Remove device '" + deviceId + "' from cache model");
            }
            deviceFolderMap.remove(deviceId);
        }

        // Handle devices that were added to the config.
        for (Device device : newDevices) {
            if (!deviceFolderMap.containsKey(device.deviceID)) {
                if (ENABLE_VERBOSE_LOG) {
                    Log.v(TAG, "updateFromConfig: Add device '" + device.deviceID + "' to cache model");
                }
                deviceFolderMap.put(device.deviceID, new HashMap<String, CompletionInfo>());
            }
        }

        // Handle folders that were removed from the config.
        List<String> removedFolders = new ArrayList<>();
        Boolean folderFound;
        for (Map.Entry<String, HashMap<String, CompletionInfo>> device : deviceFolderMap.entrySet()) {
            for (String folderId : device.getValue().keySet()) {
                folderFound = false;
                for (Folder folder : newFolders) {
                    if (folder.id.equals(folderId)) {
                        folderFound = true;
                        break;
                    }
                }
                if (!folderFound) {
                    removedFolders.add(folderId);
                }
            }
        }
        for (String folderId : removedFolders) {
            if (ENABLE_VERBOSE_LOG) {
                Log.v(TAG, "updateFromConfig: Remove folder '" + folderId + "' from cache model");
            }
            removeFolder(folderId);
        }

        // Handle folders that were added to the config.
        for (Folder folder : newFolders) {
            for (Device device : newDevices) {
                if (folder.getDevice(device.deviceID) != null) {
                    // folder is shared with device.
                    folderMap = deviceFolderMap.get(device.deviceID);
                    if (!folderMap.containsKey(folder.id)) {
                        if (ENABLE_VERBOSE_LOG) {
                            Log.v(TAG, "updateFromConfig: Add folder '" + folder.id +
                                        "' shared with device '" + device.deviceID + "' to cache model.");
                        }
                        folderMap.put(folder.id, new CompletionInfo());
                    }
                }
            }
        }
    }

    /**
     * Calculates remote device sync completion percentage across all folders
     * shared with the device.
     */
    public int getDeviceCompletion(String deviceId) {
        int folderCount = 0;
        double sumCompletion = 0;
        HashMap<String, CompletionInfo> folderMap = deviceFolderMap.get(deviceId);
        if (folderMap != null) {
            for (Map.Entry<String, CompletionInfo> folder : folderMap.entrySet()) {
                sumCompletion += folder.getValue().completion;
                folderCount++;
            }
        }
        if (folderCount == 0) {
            return 100;
        } else {
            return (int) Math.floor(sumCompletion / folderCount);
        }
    }

    /**
     * Set completionInfo within the completion[deviceId][folderId] model.
     */
    public void setCompletionInfo(String deviceId, String folderId,
                                    CompletionInfo completionInfo) {
        // Add device parent node if it does not exist.
        if (!deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.put(deviceId, new HashMap<String, CompletionInfo>());
        }
        // Add folder or update existing folder entry.
        deviceFolderMap.get(deviceId).put(folderId, completionInfo);
    }
}
