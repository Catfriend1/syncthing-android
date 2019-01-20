package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Folder {

    // Folder Configuration
    public String id;
    public String label = "";
    public String filesystemType = "basic";
    public String path;
    public String type = Constants.FOLDER_TYPE_SEND_RECEIVE;
    public boolean fsWatcherEnabled = true;
    public int fsWatcherDelayS = 10;
    private List<Device> devices = new ArrayList<>();
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
    public boolean disableTempIndexes = false;
    public boolean paused = false;
    public boolean useLargeBlocks = false;
    public int weakHashThresholdPct = 25;
    public String markerName = ".stfolder";

    // Folder Status
    public String invalid;

    public static class Versioning implements Serializable {
        public String type;
        public Map<String, String> params = new HashMap<>();
    }

    public static class MinDiskFree {
        public float value;
        public String unit;
    }

    public void addDevice(final Device device) {
        Device d = new Device();
        d.deviceID = device.deviceID;
        d.introducedBy = device.introducedBy;
        devices.add(d);
    }

    public List<Device> getDevices() {
        return devices;
    }

    public Device getDevice(String deviceId) {
        for (Device d : devices) {
            if (d.deviceID.equals(deviceId)) {
                return d;
            }
        }
        return null;
    }

    public void removeDevice(String deviceId) {
        for (Iterator<Device> it = devices.iterator(); it.hasNext();) {
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
