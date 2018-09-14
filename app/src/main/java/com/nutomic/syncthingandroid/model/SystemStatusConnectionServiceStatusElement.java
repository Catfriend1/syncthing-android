package com.nutomic.syncthingandroid.model;

import java.util.List;

/**
 * REST API endpoint "/rest/system/status"
 * Part of JSON answer in field {@link SystemStatus#connectionServiceStatus}
 */
public class SystemStatusConnectionServiceStatusElement {
    public String error;
    public List<String> lanAddresses;
    public List<String> wanAddresses;
}
