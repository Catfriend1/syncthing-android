package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sources:
 * - https://github.com/syncthing/syncthing/tree/master/lib/config
 * - https://github.com/syncthing/syncthing/blob/master/lib/config/folderconfiguration.go
 */
public class Folder {

    // Folder Configuration
    public String id;
    public String label = "";
    public String filesystemType = "basic";
    public String path;
    public String type = Constants.FOLDER_TYPE_SEND_RECEIVE;
    public boolean fsWatcherEnabled = true;
    public float fsWatcherDelayS = 10;
    private List<SharedWithDevice> devices = new ArrayList<>();
    /**
     * Folder rescan interval defaults to 3600s as it is the default in
     * syncthing when the file watcher is enabled and a new folder is created.
     */
    public int rescanIntervalS = 3600;
    public boolean ignorePerms = true;
    public boolean autoNormalize = true;
    public MinDiskFree minDiskFree;
    public Versioning versioning;
    public int copiers = 0;
    public int pullerMaxPendingKiB;
    public int hashers = 0;
    public String order = "random";
    public boolean ignoreDelete = false;
    public int scanProgressIntervalS = 0;
    public int pullerPauseS = 0;
    public int maxConflicts = 10;
    public boolean disableSparseFiles = false;
    public boolean paused = false;
    public String markerName = Constants.FILENAME_STFOLDER;

    // Since v1.1.0
    public Boolean copyOwnershipFromParent = false;

    // Since v1.2.1, see PR #5852
    public int modTimeWindowS = 0;

    // Since v1.6.0
    // see PR #6587: "inorder", "random", "standard"
    public String blockPullOrder = "standard";
    // see PR #6588
    public Boolean disableFsync = false;
    // see PR #6573, #10200
    public int maxConcurrentWrites = 0;

    // Since v1.8.0
    // see PR #6746: "all", "copy_file_range", "duplicate_extents", "ioctl", "sendfile", "standard"
    public String copyRangeMethod = "standard";

    // Since v1.9.0
    // see https://github.com/syncthing/syncthing/commit/932d8c69de9e34824ecc4d5de0a482dfdb71936e
    public Boolean caseSensitiveFS = false;

    // Since v1.21.0
    public Boolean syncOwnership = false;
    public Boolean sendOwnership = false;

    // Since v1.22.0
    public Boolean syncXattrs = false;
    public Boolean sendXattrs = false;

    // Folder Status
    public String invalid;

    public static class Versioning implements Serializable {
        public String type;
        public int cleanupIntervalS;
        public Map<String, String> params = new HashMap<>();
        // Since v1.14.0
        public String fsPath;
        public String fsType;           // default: "basic"
    }

    public static class MinDiskFree {
        public float value = 1;
        public String unit = "%";
    }

    public void addDevice(final Device device) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(device.deviceID);

        SharedWithDevice d = new SharedWithDevice();
        d.deviceID = device.deviceID;
        d.introducedBy = device.introducedBy;
        devices.add(d);
    }

    public void addDevice(final SharedWithDevice sharedWithDevice) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(sharedWithDevice.deviceID);

        SharedWithDevice d = new SharedWithDevice();
        d.deviceID = sharedWithDevice.deviceID;
        d.encryptionPassword = sharedWithDevice.encryptionPassword;
        d.introducedBy = sharedWithDevice.introducedBy;
        devices.add(d);
    }

    public List<SharedWithDevice> getSharedWithDevices() {
        return devices;
    }

    /**
     * Note: This is expected to return "1" if a folder is not shared with any
     * other device. Syncthing's config will list ourself as the only device
     * sub node which is associated with the folder. This will return >= 2
     * if the folder is shared with other devices.
     */
    public int getDeviceCount() {
        if (devices == null) {
            return 1;
        }
        return devices.size();
    }

    public SharedWithDevice getDevice(String deviceId) {
        for (SharedWithDevice d : devices) {
            if (d.deviceID.equals(deviceId)) {
                return d;
            }
        }
        return null;
    }

    public void removeDevice(String deviceId) {
        for (Iterator<SharedWithDevice> it = devices.iterator(); it.hasNext();) {
            String currentId = it.next().deviceID;
            if (currentId.equals(deviceId)) {
                it.remove();
            }
        }
    }

    @Override
    public String toString() {
        return (TextUtils.isEmpty(label))
                ? (TextUtils.isEmpty(id) ? "" : id)
                : label;
    }
}
