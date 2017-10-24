package com.nutomic.syncthingandroid.model;

import java.util.List;

public class Config {
    public int version;
    public String[] ignoredDevices;
    public List<Device> devices;
    public List<Folder> folders;
    public Gui gui;
    public Options options;

    public class Gui {
        public boolean enabled;
        public String address;
        public String user;
        public String password;
        public boolean useTLS;
        public String apiKey;
        public boolean insecureAdminAccess;
        public String theme;
    }
}
