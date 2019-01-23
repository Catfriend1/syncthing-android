package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Constants {

    // Always set ENABLE_TEST_DATA to false before building debug or release APK's.
    public static final Boolean ENABLE_TEST_DATA = false;

    public static final String FILENAME_SYNCTHING_BINARY        = "libsyncthing.so";

    // Preferences - Run conditions
    public static final String PREF_START_SERVICE_ON_BOOT       = "always_run_in_background";
    public static final String PREF_RUN_ON_MOBILE_DATA          = "run_on_mobile_data";
    public static final String PREF_RUN_ON_WIFI                 = "run_on_wifi";
    public static final String PREF_RUN_ON_METERED_WIFI         = "run_on_metered_wifi";
    public static final String PREF_USE_WIFI_SSID_WHITELIST     = "use_wifi_whitelist";
    public static final String PREF_WIFI_SSID_WHITELIST         = "wifi_ssid_whitelist";
    public static final String PREF_POWER_SOURCE                = "power_source";
    public static final String PREF_RESPECT_BATTERY_SAVING      = "respect_battery_saving";
    public static final String PREF_RESPECT_MASTER_SYNC         = "respect_master_sync";
    public static final String PREF_RUN_IN_FLIGHT_MODE          = "run_in_flight_mode";

    // Preferences - Behaviour
    public static final String PREF_USE_ROOT                    = "use_root";
    public static final String PREF_ENVIRONMENT_VARIABLES       = "environment_variables";
    public static final String PREF_DEBUG_FACILITIES_ENABLED    = "debug_facilities_enabled";
    public static final String PREF_USE_WAKE_LOCK               = "wakelock_while_binary_running";
    public static final String PREF_USE_TOR                     = "use_tor";
    public static final String PREF_SOCKS_PROXY_ADDRESS         = "socks_proxy_address";
    public static final String PREF_HTTP_PROXY_ADDRESS          = "http_proxy_address";
    public static final String PREF_BROADCAST_SERVICE_CONTROL   = "broadcast_service_control";

    // Preferences - per Folder and Device Sync Conditions
    public static final String PREF_OBJECT_PREFIX_FOLDER        = "sc_folder_";
    public static final String PREF_OBJECT_PREFIX_DEVICE        = "sc_device_";

    public static String DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + "custom_sync_conditions";
    }

    public static String DYN_PREF_OBJECT_SYNC_ON_WIFI(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_RUN_ON_WIFI;
    }

    public static String DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_USE_WIFI_SSID_WHITELIST;
    }

    public static String DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_WIFI_SSID_WHITELIST;
    }

    public static String DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_RUN_ON_METERED_WIFI;
    }

    public static String DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_RUN_ON_MOBILE_DATA;
    }

    /**
     * Cached information which is not available on SettingsActivity.
     */
    public static final String PREF_LAST_BINARY_VERSION         = "lastBinaryVersion";
    public static final String PREF_LOCAL_DEVICE_ID             = "localDeviceID";

    /**
     * {@link EventProcessor}
     */
    public static final String PREF_EVENT_PROCESSOR_LAST_SYNC_ID = "last_sync_id";

    /**
     * Available options cache for preference {@link com.nutomic.syncthingandroid.R.xml#app_settings#debug_facilities_enabled}
     * Read via REST API call in {@link RestApi#updateDebugFacilitiesCache} after first successful binary startup.
     */
    public static final String PREF_DEBUG_FACILITIES_AVAILABLE  = "debug_facilities_available";

    /**
     * Available folder types.
     */
    public static final String FOLDER_TYPE_SEND_ONLY            = "sendonly";
    public static final String FOLDER_TYPE_SEND_RECEIVE         = "sendreceive";
    public static final String FOLDER_TYPE_RECEIVE_ONLY         = "receiveonly";

    /**
     * Default listening ports.
     */
    public static final Integer DEFAULT_WEBGUI_TCP_PORT         = 8384;
    public static final Integer DEFAULT_DATA_TCP_PORT           = 22000;

    /**
     * On Android 8.1, ACCESS_COARSE_LOCATION is required to access WiFi SSID.
     * This is the request code used when requesting the permission.
     */
    public static final int PERM_REQ_ACCESS_COARSE_LOCATION = 999; // for issue #999

    /**
     * Interval in ms at which the GUI is updated (eg {@link com.nutomic.syncthingandroid.fragments.DrawerFragment}).
     */
    public static final long GUI_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    /**
     * Directory where config is exported to and imported from.
     */
    public static final String EXPORT_PATH = Environment.getExternalStorageDirectory() + "/backups/syncthing";

    public static final File EXPORT_PATH_OBJ = new File(EXPORT_PATH);

    /**
     * File in the config folder that contains configuration.
     */
    static final String CONFIG_FILE = "config.xml";

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    /**
     * File in the config folder we write to temporarily before renaming to CONFIG_FILE.
     */
    static final String CONFIG_TEMP_FILE = "config.xml.tmp";

    public static File getConfigTempFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_TEMP_FILE);
    }

    /**
     * Name of the public key file in the data directory.
     */
    static final String PUBLIC_KEY_FILE = "cert.pem";

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
     * Name of the folder containing the index database.
     */
    public static final String INDEX_DB_FOLDER = "index-v0.14.0.db";

    /**
     * Name of the public HTTPS CA file in the data directory.
     */
    public static File getHttpsCertFile(Context context) {
        return new File(context.getFilesDir(), "https-cert.pem");
    }

    /**
     * Name of the export file holding the SharedPreferences backup.
     */
    static final String SHARED_PREFS_EXPORT_FILE = "sharedpreferences.dat";

    static File getSyncthingBinary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, FILENAME_SYNCTHING_BINARY);
    }

    static File getLogFile(Context context) {
        return new File(context.getExternalFilesDir(null), "syncthing.log");
    }

    /**
     * Decide if we should enforce HTTPS when accessing the Web UI and REST API.
     * Android 4.4 and earlier don't have support for TLS 1.2 requiring us to
     * fall back to an unencrypted HTTP connection to localhost. This applies
     * to syncthing core v0.14.53+.
     */
    public static Boolean osSupportsTLS12() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Pre-Lollipop devices don't support TLS 1.2
            return false;
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N) {
            /**
             * SSLProtocolException: SSL handshake failed on Android N/7.0,
             * missing support for elliptic curves.
             * See https://issuetracker.google.com/issues/37122132
             */
            return false;
        }

        return true;
    }
}
