package com.nutomic.syncthingandroid.model;

/**
 * Caches information frequently needed by the wrapper
 * to save expensive calls to Syncthing's REST API.
 * Vars in class do not correspond to JSON results.
 */
public class CachedFolderStatus {
    /**
     * Calculated
     */
    public double completion = 100;

    /**
     * Accessed by setters
     */
    // Example: "Test1.sync-conflict-20250514-140912-XF3FVSV", "subfolder/Test2.sync-conflict-20250514-140912-XF3FVSV"
    public String[] discoveredConflictFiles = new String[]{};

    // Example: "update"
    public String lastItemFinishedAction = "";

    // Example: "test.doc"
    public String lastItemFinishedItem = "";

    // Example: "2019-11-19T23:28:55.7479276+01:00"
    public String lastItemFinishedTime = "";

    // Indicates if a folder - device pair has received updates from the remote side.
    public boolean remoteIndexUpdated = false;

    public boolean paused = false;
}
