package com.nutomic.syncthingandroid.model;

public class Connection {

    public String address = "";
    public String at = "";                      // "0001-01-01T00:00:00Z"
    public String clientVersion = "";
    public boolean connected = false;
    public long inBytesTotal = 0;
    public long outBytesTotal = 0;
    public boolean paused = false;
    public String type = "";

    // These fields are not sent from Syncthing. They are populated by {@link #setTransferRate}.
    public long inBits = 0;
    public long outBits = 0;

    public void setTransferRate(Connection previous, long msElapsed) {
        long secondsElapsed = msElapsed / 1000;
        long inBytes = 8 * (inBytesTotal - previous.inBytesTotal) / secondsElapsed;
        long outBytes = 8 * (outBytesTotal - previous.outBytesTotal) / secondsElapsed;
        inBits = Math.max(0, inBytes);
        outBits = Math.max(0, outBytes);
    }

}
