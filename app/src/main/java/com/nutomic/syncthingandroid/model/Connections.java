package com.nutomic.syncthingandroid.model;

import java.util.Map;

public class Connections {

    public Connection total;
    public Map<String, Connection> connections;

    public static class Connection {
        public boolean paused;
        public String clientVersion;
        public String at;
        public boolean connected;
        public long inBytesTotal;
        public long outBytesTotal;
        public String type;
        public String address;

        // These fields are not sent from Syncthing, but are populated on the client side.
        public int completion;
        public long inBits;
        public long outBits;

        public void setTransferRate(Connection previous, long msElapsed) {
            long secondsElapsed = msElapsed / 1000;
            long inBytes = 8 * (inBytesTotal - previous.inBytesTotal) / secondsElapsed;
            long outBytes = 8 * (outBytesTotal - previous.outBytesTotal) / secondsElapsed;
            inBits = Math.max(0, inBytes);
            outBits = Math.max(0, outBytes);

        }
    }
}
