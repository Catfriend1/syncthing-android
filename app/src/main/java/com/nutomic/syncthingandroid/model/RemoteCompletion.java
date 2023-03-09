package com.nutomic.syncthingandroid.model;

import android.util.Log;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.text.TextUtils;

/**
 * This class caches remote folder and device synchronization
 * completion indicators defined in {@link RemoteCompletionInfo}
 * according to syncthing's REST "/completion" JSON result schema.
 * Completion model of syncthing's web UI is completion[deviceId][folderId]
 */
public class RemoteCompletion {

    private static final String TAG = "RemoteCompletion";

    private Boolean ENABLE_DEBUG_LOG = false;
    private Boolean ENABLE_VERBOSE_LOG = false;

    HashMap<String, Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>>> mDeviceFolderMap =
        new HashMap<String, Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>>>();

    /**
     * Object that must be locked upon accessing mDeviceFolderMap.
     */
    private final Object mDeviceFolderMapLock = new Object();

    public RemoteCompletion(Boolean enableVerboseLog) {
        ENABLE_VERBOSE_LOG = enableVerboseLog;
    }

    /**
     * Removes a folder from the cache model.
     */
    private void removeFolder(String folderId) {
        synchronized(mDeviceFolderMapLock) {
            for (Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>> folderMapEntry : mDeviceFolderMap.values()) {
                HashMap<String, RemoteCompletionInfo> folderMap = folderMapEntry.getValue();
                if (folderMap.containsKey(folderId)) {
                    folderMap.remove(folderId);
                    break;
                }
            }
        }
    }

    /**
     * Updates device and folder information in the cache model
     * after a config update.
     */
    public void updateFromConfig(final List<Device> newDevices, final List<Folder> newFolders) {
        synchronized(mDeviceFolderMapLock) {
            HashMap<String, RemoteCompletionInfo> folderMap;

            // Handle devices that were removed from the config.
            List<String> removedDevices = new ArrayList<>();
            Boolean deviceFound;
            for (String deviceId : mDeviceFolderMap.keySet()) {
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
                LogV("updateFromConfig: Remove device '" + getShortenedDeviceId(deviceId) + "' from cache model");
                mDeviceFolderMap.remove(deviceId);
            }

            // Handle devices that were added to the config.
            for (Device device : newDevices) {
                if (!mDeviceFolderMap.containsKey(device.deviceID)) {
                    LogV("updateFromConfig: Add device '" + getShortenedDeviceId(device.deviceID) + "' to cache model");
                    mDeviceFolderMap.put(
                            device.deviceID,
                            new SimpleEntry(
                                    new Connection(),
                                    new HashMap<String, RemoteCompletionInfo>()
                            )
                    );
                }
            }

            // Handle folders that were removed from the config.
            List<String> removedFolders = new ArrayList<>();
            Boolean folderFound;
            for (Map.Entry<String, Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>>> device : mDeviceFolderMap.entrySet()) {
                //                            Map.Entry   HashMap    String
                for (String folderId : device.getValue().getValue().keySet()) {
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
                LogV("updateFromConfig: Remove folder '" + folderId + "' from cache model");
                removeFolder(folderId);
            }

            // Handle folders that were added to the config.
            for (Folder folder : newFolders) {
                for (Device device : newDevices) {
                    if (folder.getDevice(device.deviceID) != null) {
                        // folder is shared with device.
                        folderMap = mDeviceFolderMap.get(device.deviceID).getValue();
                        if (!folderMap.containsKey(folder.id)) {
                            LogV("updateFromConfig: Add folder '" + folder.id +
                                    "' shared with device '" + getShortenedDeviceId(device.deviceID) + "' to cache model.");
                            folderMap.put(folder.id, new RemoteCompletionInfo());
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculates remote device sync completion percentage across all connected devices.
     * Returns "-1" if sync completion is not applicable.
     */
    public int getTotalDeviceCompletion() {
        synchronized(mDeviceFolderMapLock) {
            int connectedDeviceCount = 0;
            int folderCount = 0;
            double sumCompletion = 0;
            for (Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>> device : mDeviceFolderMap.values()) {
                if (!device.getKey().connected) {
                    continue;
                }
                connectedDeviceCount++;

                //                                                 HashMap   RemoteCompletionInfo
                for (RemoteCompletionInfo completionInfo : device.getValue().values())
                {
                    double folderCompletion = completionInfo.completion;
                    if (folderCompletion < 0) {
                        folderCompletion = 0;
                    } else if (folderCompletion > 100) {
                        folderCompletion = 100;
                    }

                    // Syncthing's WebUI considers remote folders with 0% and 100% completion as up-to-date.
                    if (folderCompletion != 0 && folderCompletion != 100) {
                        sumCompletion += folderCompletion;
                        folderCount++;
                    }
                }
            }
            if (connectedDeviceCount == 0) {
                return -1;
            }
            if (folderCount == 0) {
                return 100;
            }
            int totalDeviceCompletion = (int) Math.floor(sumCompletion / folderCount);
            if (totalDeviceCompletion < 0) {
                totalDeviceCompletion = 0;
            } else if (totalDeviceCompletion > 100) {
                totalDeviceCompletion = 100;
            }
            return totalDeviceCompletion;
        }
    }

    /**
     * Calculates remote device sync completion percentage across all folders
     * shared with the device.
     */
    public int getDeviceCompletion(String deviceId) {
        synchronized(mDeviceFolderMapLock) {
            if (!mDeviceFolderMap.containsKey(deviceId)) {
                LogV("getDeviceCompletion: Cache miss for deviceId=[" + deviceId + "]");
                return 100;
            }

            int folderCount = 0;
            double sumCompletion = 0;
            HashMap<String, RemoteCompletionInfo> folderMap = mDeviceFolderMap.get(deviceId).getValue();
            if (folderMap != null) {
                for (Map.Entry<String, RemoteCompletionInfo> folder : folderMap.entrySet()) {
                    double folderCompletion = folder.getValue().completion;
                    if (folderCompletion < 0) {
                        folderCompletion = 0;
                    } else if (folderCompletion > 100) {
                        folderCompletion = 100;
                    }

                    // Syncthing's WebUI considers remote folders with 0% and 100% completion as up-to-date.
                    if (folderCompletion != 0 && folderCompletion != 100) {
                        sumCompletion += folderCompletion;
                        folderCount++;
                    }
                }
            }
            if (folderCount == 0) {
                return 100;
            }
            int deviceCompletion = (int) Math.floor(sumCompletion / folderCount);
            if (deviceCompletion < 0) {
                deviceCompletion = 0;
            } else if (deviceCompletion > 100) {
                deviceCompletion = 100;
            }
            return deviceCompletion;
        }
    }

    public double getDeviceNeedBytes(String deviceId) {
        synchronized(mDeviceFolderMapLock) {
            if (!mDeviceFolderMap.containsKey(deviceId)) {
                LogV("getDeviceNeedBytes: Cache miss for deviceId=[" + deviceId + "]");
                return 0;
            }

            double sumNeedBytes = 0;
            HashMap<String, RemoteCompletionInfo> folderMap = mDeviceFolderMap.get(deviceId).getValue();
            if (folderMap != null) {
                for (Map.Entry<String, RemoteCompletionInfo> folder : folderMap.entrySet()) {
                    sumNeedBytes += folder.getValue().needBytes;
                }
            }
            return sumNeedBytes;
        }
    }

    /**
     * Set completionInfo within the completion[deviceId][folderId] model.
     */
    public void setCompletionInfo(String deviceId, String folderId,
                                    final RemoteCompletionInfo completionInfo) {
        synchronized(mDeviceFolderMapLock) {
            // Add device parent node if it does not exist.
            if (!mDeviceFolderMap.containsKey(deviceId)) {
                mDeviceFolderMap.put(
                        deviceId,
                        new SimpleEntry(
                                new Connection(),
                                new HashMap<String, RemoteCompletionInfo>()
                        )
                );
            }
            LogV("setCompletionInfo: Storing " + completionInfo.completion + "% for folder \"" +
                    folderId + "\" at device \"" +
                    getShortenedDeviceId(deviceId) + "\".");
            // Add folder or update existing folder entry.
            mDeviceFolderMap.get(deviceId).getValue().put(folderId, completionInfo);
        }
    }

    /**
     * Returns remote device status.
     */
    public final Connection getDeviceStatus(final String deviceId) {
        synchronized(mDeviceFolderMapLock) {
            if (!mDeviceFolderMap.containsKey(deviceId)) {
                return new Connection();
            }
            //                                      Map.Entry     Connection
            Connection connection = mDeviceFolderMap.get(deviceId).getKey();
            return deepCopy(connection, new TypeToken<Connection>(){}.getType());
        }
    }

    public int getOnlineDeviceCount() {
        synchronized(mDeviceFolderMapLock) {
            int onlineDeviceCount = 0;
            for (Map.Entry<Connection, HashMap<String, RemoteCompletionInfo>> device : mDeviceFolderMap.values()) {
                if (device.getKey().connected) {
                    onlineDeviceCount++;
                }
            }
            return onlineDeviceCount;
        }
    }

    /**
     * Store remote device status for later when we need info for the UI.
     */
    public void setDeviceStatus(final String deviceId,
                                    final Connection connection) {
        synchronized(mDeviceFolderMapLock) {
            // Add device parent node if it does not exist.
            if (!mDeviceFolderMap.containsKey(deviceId)) {
                mDeviceFolderMap.put(
                        deviceId,
                        new SimpleEntry(
                                new Connection(),
                                new HashMap<String, RemoteCompletionInfo>()
                        )
                );
            }

            if (ENABLE_DEBUG_LOG) {
                Log.d(TAG, "setDeviceStatus: deviceId=\"" + deviceId + "\"" +
                        ", connected=" + Boolean.toString(connection.connected) +
                        ", paused=" + Boolean.toString(connection.paused)
                );
            }

            // Update device status information.
            Map.Entry updatedEntry = new SimpleEntry(
                    deepCopy(connection, new TypeToken<Connection>(){}.getType()),
                    deepCopy(
                            mDeviceFolderMap.get(deviceId).getValue(),
                            new TypeToken<HashMap<String, RemoteCompletionInfo>>(){}.getType()
                    )
            );
            mDeviceFolderMap.put(deviceId, updatedEntry);
        }
    }

    /**
     * Returns the first characters of the device ID for logging purposes.
     */
    public String getShortenedDeviceId(String deviceId) {
        return (TextUtils.isEmpty(deviceId) ? "" : deviceId.substring(0, 7));
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

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
