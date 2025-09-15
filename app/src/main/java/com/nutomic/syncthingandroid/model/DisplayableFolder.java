package com.nutomic.syncthingandroid.model;

/**
 * Wrapper class to represent both accepted folders and pending folders for UI display.
 */
public class DisplayableFolder {
    private final Folder acceptedFolder;
    private final PendingFolder pendingFolder;
    private final String folderId;
    private final String offeredByDeviceId;
    private final boolean isPending;

    // Constructor for accepted folder
    public DisplayableFolder(Folder folder) {
        this.acceptedFolder = folder;
        this.pendingFolder = null;
        this.folderId = folder.id;
        this.offeredByDeviceId = null;
        this.isPending = false;
    }

    // Constructor for pending folder
    public DisplayableFolder(String folderId, PendingFolder pendingFolder, String offeredByDeviceId) {
        this.acceptedFolder = null;
        this.pendingFolder = pendingFolder;
        this.folderId = folderId;
        this.offeredByDeviceId = offeredByDeviceId;
        this.isPending = true;
    }

    public boolean isPending() {
        return isPending;
    }

    public String getFolderId() {
        return folderId;
    }

    public Folder getAcceptedFolder() {
        return acceptedFolder;
    }

    public PendingFolder getPendingFolder() {
        return pendingFolder;
    }

    public String getOfferedByDeviceId() {
        return offeredByDeviceId;
    }

    public String getDisplayLabel() {
        if (isPending) {
            return pendingFolder.label != null && !pendingFolder.label.isEmpty() 
                ? pendingFolder.label : folderId;
        } else {
            return acceptedFolder.label != null && !acceptedFolder.label.isEmpty() 
                ? acceptedFolder.label : acceptedFolder.id;
        }
    }

    public String getPath() {
        if (isPending) {
            return ""; // Pending folders don't have paths yet
        } else {
            return acceptedFolder.path != null ? acceptedFolder.path : "";
        }
    }
}