package com.nutomic.syncthingandroid.model;

/**
 * Wrapper class to represent both accepted devices and pending devices for UI display.
 */
public class DisplayableDevice {
    private final Device acceptedDevice;
    private final PendingDevice pendingDevice;
    private final String deviceId;
    private final boolean isPending;

    // Constructor for accepted device
    public DisplayableDevice(Device device) {
        this.acceptedDevice = device;
        this.pendingDevice = null;
        this.deviceId = device.deviceID;
        this.isPending = false;
    }

    // Constructor for pending device
    public DisplayableDevice(String deviceId, PendingDevice pendingDevice) {
        this.acceptedDevice = null;
        this.pendingDevice = pendingDevice;
        this.deviceId = deviceId;
        this.isPending = true;
    }

    public boolean isPending() {
        return isPending;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Device getAcceptedDevice() {
        return acceptedDevice;
    }

    public PendingDevice getPendingDevice() {
        return pendingDevice;
    }

    public String getDisplayName() {
        if (isPending) {
            return pendingDevice.name != null && !pendingDevice.name.isEmpty() 
                ? pendingDevice.name : deviceId;
        } else {
            return acceptedDevice.getDisplayName();
        }
    }

    public String getAddress() {
        if (isPending) {
            return pendingDevice.address != null ? pendingDevice.address : "";
        } else {
            return ""; // Accepted devices don't expose address in the same way
        }
    }
}