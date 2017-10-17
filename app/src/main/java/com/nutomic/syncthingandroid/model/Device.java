package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import java.util.List;

public class Device {
    public String deviceID;
    public String name;
    public List<String> addresses;
    public String compression;
    public String certName;
    public boolean introducer;

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(name))
                ? deviceID.substring(0, 7)
                : name;
    }
}
