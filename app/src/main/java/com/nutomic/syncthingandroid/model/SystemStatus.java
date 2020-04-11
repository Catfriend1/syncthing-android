package com.nutomic.syncthingandroid.model;

import java.util.Map;

/**
 * REST API endpoint "/rest/system/status"
 */
public class SystemStatus {
    // Example: 25857744
    public long alloc;

    /**
     * No longer supported since SyncthingNative v1.4.1
     * v1.4.1+ return "cpuPercent = 0"
     */
    // Example: 1.1183119275778985
    public double cpuPercent;

    // Example:
    //  connectionServiceStatus: {dynamic+https://relays.syncthing.net/endpoint: {error: null, lanAddresses: [], wanAddresses: []},…}
    //      dynamic+https://relays.syncthing.net/endpoint: {error: null, lanAddresses: [], wanAddresses: []}
    //      quic://0.0.0.0:22000: {error: null, lanAddresses: ["quic://0.0.0.0:22000"],…}
    //      tcp://0.0.0.0:22000: {error: null, lanAddresses: ["tcp://0.0.0.0:22000"], wanAddresses: ["tcp://0.0.0.0:22000"]}
    public Map<String, SystemStatusConnectionServiceStatusElement> connectionServiceStatus;

    // Example: true
    public boolean discoveryEnabled;

    // Example:
    //  discoveryErrors: {,…}
    //      IPv4 local: "listen udp4 :21027: bind: Normalerweise darf jede Socketadresse (Protokoll, Netzwerkadresse oder Anschluss) nur jeweils einmal verwendet werden."
    //      IPv6 local: "listen udp6 [ff12::8384]:21027: bind: Der Zugriff auf einen Socket war aufgrund der Zugriffsrechte des Sockets unzulässig."
    //      global@https://discovery-v6.syncthing.net/v2/: "Post https://discovery-v6.syncthing.net/v2/: dial tcp: lookup discovery-v6.syncthing.net: no such host"
    public Map<String, String> discoveryErrors;

    // Example: 5
    public int discoveryMethods;

    // Example: 77
    public int goroutines;

    // Example: "7LTUV3P-Y37HQXK-UUM7S5Q-2NDQT3B-SA4WAT4-T5ODX3V-XRXAF7Z-MXM7GAA"
    public String myID;

    // Example: "\"
    public String pathSeparator;

    // Example: "2019-09-21T10:59:47.1951229+02:00"
    public String startTime;

    // RAM usage, Example: 46476920
    public long sys;

    // Example: "C:\Users\Dev"
    public String tilde;

    // Example: 29
    public long uptime;

    // Example: 3
    public int urVersionMax;

    // Example:
    //  lastDialStatus: {,…}
    //      tcp4://192.168.5.1: {when: "2019-09-21T09:10:35Z", error: "dial tcp4 192.168.5.1:22000: i/o timeout"}
    public Map<String, SystemStatusLastDialStatusElement> lastDialStatus;

    // Example: false
    public boolean guiAddressOverridden;

    /**
     * Since SyncthingNative v1.3.0
     */
    // Example: "127.0.0.1:8384"
    public String guiAddressUsed;
}
