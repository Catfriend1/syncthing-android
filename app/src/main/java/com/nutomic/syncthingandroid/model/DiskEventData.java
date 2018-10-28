package com.nutomic.syncthingandroid.model;

/**
 * REST API endpoint "/rest/events/disk"
 */
public class DiskEventData {
    // action = {"added", "deleted", "modified"}
    public String action = "";

    public String folder = "";
    public String folderID = "";
    public String label = "";
    public String modifiedBy = "";
    public String path = "";

    // type = {"file", "dir"}
    public String type = "";
}
