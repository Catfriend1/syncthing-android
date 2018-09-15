package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

public class RemoteIgnoredDevice {
    public String time = "";
    public String deviceID = "";
    public String name = "";
    public String address = "";

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(name))
                ? deviceID.substring(0, 7)
                : name;
    }
}
