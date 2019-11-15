package com.nutomic.syncthingandroid.model;

/**
 * Caches information frequently needed by the wrapper
 * to save expensive calls to Syncthing's REST API.
 * Vars in class do not correspond to JSON results.
 */
public class CachedFolderStatus {
    public double completion = 100;
    public boolean paused = false;
}
