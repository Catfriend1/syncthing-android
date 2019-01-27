package com.nutomic.syncthingandroid.model;

public class Options {
    public String[] listenAddresses;
    public String[] globalAnnounceServers;
    public boolean globalAnnounceEnabled;
    public boolean localAnnounceEnabled;
    public int localAnnouncePort;
    public String localAnnounceMCAddr;
    public int maxSendKbps;
    public int maxRecvKbps;
    public int reconnectionIntervalS;
    public boolean relaysEnabled;
    public int relayReconnectIntervalM;
    public boolean startBrowser;
    public boolean natEnabled;
    public int natLeaseMinutes;
    public int natRenewalMinutes;
    public int natTimeoutSeconds;
    public int urAccepted;
    public String urUniqueId;
    public String urURL;
    public boolean urPostInsecurely;
    public int urInitialDelayS;
    public boolean restartOnWakeup;
    public int autoUpgradeIntervalH;
    public int keepTemporariesH;
    public boolean cacheIgnoredFiles;
    public int progressUpdateIntervalS;
    public boolean symlinksEnabled;
    public boolean limitBandwidthInLan;
    public String releasesURL;
    public String[] alwaysLocalNets;
    public boolean overwriteRemoteDeviceNamesOnConnect;
    public int tempIndexMinBlocks;
    public String defaultFolderPath;

    // Since v0.14.28, Issue #3307, PR #4087
    public MinHomeDiskFree minHomeDiskFree;

    // Since v1.0.0, see https://github.com/syncthing/syncthing/pull/4888
    public int maxConcurrentScans;

    public static class MinHomeDiskFree {
        public float value = 1;
        public String unit = "%";
    }

    public static final int USAGE_REPORTING_UNDECIDED = 0;
    public static final int USAGE_REPORTING_DENIED    = -1;

    public boolean isUsageReportingAccepted(int urVersionMax) {
        return urAccepted == urVersionMax;
    }

    public boolean isUsageReportingDecided(int urVersionMax) {
        return isUsageReportingAccepted(urVersionMax) || urAccepted == USAGE_REPORTING_DENIED;
    }
}
