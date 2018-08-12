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

    public String id;
    public String label;
    public String filesystemType = "basic";
    public String path;
    public String type = Constants.FOLDER_TYPE_SEND_RECEIVE;
    public boolean fsWatcherEnabled = true;
    public int fsWatcherDelayS = 10;
    private List<Device> devices = new ArrayList<>();
    public int rescanIntervalS;
    public final boolean ignorePerms = true;
    public boolean autoNormalize = true;
    public MinDiskFree minDiskFree;
    public Versioning versioning;
    public int copiers;
    public int pullerMaxPendingKiB;
    public int hashers;
    public String order;
    public boolean ignoreDelete;
    public int scanProgressIntervalS;
    public int pullerPauseS;
    public int maxConflicts = 10;
    public boolean disableSparseFiles;
    public boolean disableTempIndexes;
    public boolean paused;
    public boolean useLargeBlocks;
    public int weakHashThresholdPct = 25;
    public String markerName = ".stfolder";
    public String invalid;

    public static class Versioning implements Serializable {
        public String type;
        public Map<String, String> params = new HashMap<>();
    }

    public static class MinDiskFree {
        public float value;
        public String unit;
    }

    public void addDevice(String deviceId) {
        Device d = new Device();
        d.deviceID = deviceId;
        devices.add(d);
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
        return !TextUtils.isEmpty(label) ? label : id;
    }
}
