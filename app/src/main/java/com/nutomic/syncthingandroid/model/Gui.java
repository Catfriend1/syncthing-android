package com.nutomic.syncthingandroid.model;

public class Gui {
    public boolean enabled;
    public String address;
    public String user;
    public String password;
    public boolean useTLS;
    public String apiKey;
    public boolean insecureAdminAccess;
    public String theme;

    public String getBindAddress() {
        if (address == null) {
            return "";
        }
        String[] split = address.split(":");
        return split.length < 1 ? "" : split[0];
    }

    public String getBindPort() {
        if (address == null) {
            return "";
        }
        String[] split = address.split(":");
        return split.length < 2 ? "" : split[1];
    }
}
