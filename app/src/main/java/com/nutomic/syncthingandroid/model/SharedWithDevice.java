package com.nutomic.syncthingandroid.model;

public class SharedWithDevice {
    public String deviceID;
    public String introducedBy = "";

    // Since v1.12.0
    // See https://github.com/syncthing/syncthing/pull/7055
    public String encryptionPassword = "";
}
