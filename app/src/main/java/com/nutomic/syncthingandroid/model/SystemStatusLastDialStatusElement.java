package com.nutomic.syncthingandroid.model;

/**
 * REST API endpoint "/rest/system/status"
 * Part of JSON answer in field {@link SystemStatus#lastDialStatus}
 */
public class SystemStatusLastDialStatusElement {
    // Example: "dial tcp4 192.168.5.1:22000: i/o timeout"
    public String error;

    // Example: "2019-09-21T09:10:35Z"
    public String when;
}
