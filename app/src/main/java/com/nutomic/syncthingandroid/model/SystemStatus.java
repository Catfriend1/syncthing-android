package com.nutomic.syncthingandroid.model;

import java.util.Map;

/**
 * REST API endpoint "/rest/system/status"
 */
public class SystemStatus {
    public long alloc;
    public double cpuPercent;
    public Map<String, SystemStatusConnectionServiceStatusElement> connectionServiceStatus;
    public boolean discoveryEnabled;
    public Map<String, String> discoveryErrors;
    public int discoveryMethods;
    public int goroutines;
    public String myID;
    public String pathSeparator;
    public String startTime;
    public long sys;
    public String tilde;
    public long uptime;
    public int urVersionMax;
}
