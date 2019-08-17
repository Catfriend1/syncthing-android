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
}
