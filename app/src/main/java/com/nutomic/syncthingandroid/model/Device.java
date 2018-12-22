package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import java.util.List;

public class Device {
    public String deviceID;
    public String name = "";
    public List<String> addresses;
    public String compression;
    public String certName;
    public String introducedBy = "";
    public boolean introducer = false;
    public boolean paused;
    public List<PendingFolder> pendingFolders;
    public List<IgnoredFolder> ignoredFolders;

    /**
     * Relevant fields for Folder.List<Device> "shared-with-device" model,
     * handled by {@link ConfigRouter#updateFolder and ConfigXml#updateFolder}
     *  deviceID
     *  introducedBy
     * Example Tag
     *  <folder ...>
     *      <device id="[DEVICE_SHARING_THAT_FOLDER_WITH_US]" introducedBy="[INTRODUCER_DEVICE_THAT_TOLD_US_ABOUT_THE_FOLDER_OR_EMPTY_STRING]"></device>
     *  </folder>
     */

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(name))
                ? (TextUtils.isEmpty(deviceID) ? "" : deviceID.substring(0, 7))
                : name;
    }
}
