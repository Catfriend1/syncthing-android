package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

public class SharedWithDevice {
    public String deviceID;
    public String introducedBy = "";

    // Since v1.12.0
    // See https://github.com/syncthing/syncthing/pull/7055
    public String encryptionPassword = "";

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(deviceID) ? "" : deviceID.substring(0, 7));
    }
}
