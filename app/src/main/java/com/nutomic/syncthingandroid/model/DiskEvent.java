package com.nutomic.syncthingandroid.model;

/**
 * REST API endpoint "/rest/events/disk"
 */
public class DiskEvent {
    public long id = 0;
    public long globalID = 0;
    public String time = "";

    // type = {"LocalChangeDetected", "RemoteChangeDetected"}
    public String type = "";

    public DiskEventData data = new DiskEventData();
}
