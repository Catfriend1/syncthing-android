package com.nutomic.syncthingandroid.model;

/**
 * /rest/db/completion?device=deviceId&folder=folderId
 */
public class CompletionInfo {
    public double completion = 0;
    public double globalBytes = 0;
    public double globalItems = 0;
    public double needBytes = 0;
    public double needDeletes = 0;
    public double needItems = 0;
    public String remoteState = "unknown";
    public long sequence = 0;
}
