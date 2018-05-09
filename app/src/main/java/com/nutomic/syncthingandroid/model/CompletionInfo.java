package com.nutomic.syncthingandroid.model;

/**
 * According to syncthing REST API
 * https://docs.syncthing.net/rest/db-completion-get.html
 *
 * completion is also returned by the events API
 * https://docs.syncthing.net/events/foldercompletion.html
 *
 */
public class CompletionInfo {
    public double completion = 100;

    /**
     * The following values are only returned by the REST API call
     * to ""/completion". We will need them in the future to show
     * more statistics in the device UI.
     */
    // public long globalBytes = 0;
    // public long needBytes = 0;
    // public long needDeletes = 0;
    // public long needItems = 0;
}
