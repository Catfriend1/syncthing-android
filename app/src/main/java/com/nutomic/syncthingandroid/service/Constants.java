package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Constants {

    public static final String PREF_ALWAYS_RUN_IN_BACKGROUND = "always_run_in_background";
    public static final String PREF_SYNC_ONLY_WIFI           = "sync_only_wifi";
    public static final String PREF_SYNC_ONLY_WIFI_SSIDS     = "sync_only_wifi_ssids_set";
    public static final String PREF_SYNC_ONLY_CHARGING       = "sync_only_charging";
    public static final String PREF_RESPECT_BATTERY_SAVING   = "respect_battery_saving";
    public static final String PREF_USE_ROOT                 = "use_root";
    public static final String PREF_NOTIFICATION_TYPE        = "notification_type";
    public static final String PREF_USE_WAKE_LOCK            = "wakelock_while_binary_running";
    public static final String PREF_FOREGROUND_SERVICE       = "run_as_foreground_service";

    /**
     * Interval in ms at which the GUI is updated (eg {@link com.nutomic.syncthingandroid.fragments.DrawerFragment}).
     */
    public static final long GUI_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    /**
     * Directory where config is exported to and imported from.
     */
    public static final File EXPORT_PATH =
            new File(Environment.getExternalStorageDirectory(), "backups/syncthing");

    /**
     * File in the config folder that contains configuration.
     */
    static String CONFIG_FILE = "config.xml";

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    /**
     * Name of the public key file in the data directory.
     */
    static String PUBLIC_KEY_FILE = "cert.pem";

    static File getPublicKeyFile(Context context) {
        return new File(context.getFilesDir(), PUBLIC_KEY_FILE);
    }

    /**
     * Name of the private key file in the data directory.
     */
    static final String PRIVATE_KEY_FILE = "key.pem";

    static File getPrivateKeyFile(Context context) {
        return new File(context.getFilesDir(), PRIVATE_KEY_FILE);
    }

    /**
     * Name of the public HTTPS CA file in the data directory.
     */
    public static File getHttpsCertFile(Context context) {
        return new File(context.getFilesDir(), "https-cert.pem");
    }

    static File getSyncthingBinary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libsyncthing.so");
    }

    static File getLogFile(Context context) {
        return new File(context.getExternalFilesDir(null), "syncthing.log");
    }
}
