package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.text.TextUtils;

import com.nutomic.syncthingandroid.util.FileUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Constants {

    // Always set ENABLE_TEST_DATA to false before building debug or release APK's.
    public static final Boolean ENABLE_TEST_DATA = false;

    public static final String FILENAME_SYNCTHING_BINARY        = "libsyncthingnative.so";
    public static final String FILENAME_STIGNORE                = ".stignore";
    public static final String FILENAME_STFOLDER                = ".stfolder";

    // Preferences - Run conditions
    public static final String PREF_START_SERVICE_ON_BOOT       = "always_run_in_background";
    public static final String PREF_RUN_ON_MOBILE_DATA          = "run_on_mobile_data";
    public static final String PREF_RUN_ON_ROAMING              = "run_on_roaming";
    public static final String PREF_RUN_ON_WIFI                 = "run_on_wifi";
    public static final String PREF_RUN_ON_METERED_WIFI         = "run_on_metered_wifi";
    public static final String PREF_USE_WIFI_SSID_WHITELIST     = "use_wifi_whitelist";
    public static final String PREF_WIFI_SSID_WHITELIST         = "wifi_ssid_whitelist";
    public static final String PREF_POWER_SOURCE                = "power_source";

    public final class PowerSource {
        public static final String CHARGER_BATTERY              = "ac_and_battery_power";
        public static final String CHARGER                      = "ac_power";
        public static final String BATTERY                      = "battery_power";

        private PowerSource() { }
    }

    public static final String PREF_RESPECT_BATTERY_SAVING      = "respect_battery_saving";
    public static final String PREF_RESPECT_MASTER_SYNC         = "respect_master_sync";
    public static final String PREF_RUN_IN_FLIGHT_MODE          = "run_in_flight_mode";
    public static final String PREF_RUN_ON_TIME_SCHEDULE        = "run_on_time_schedule";
    public static final String PREF_SYNC_DURATION_MINUTES       = "sync_duration_minutes";
    public static final String PREF_SLEEP_INTERVAL_MINUTES       = "sleep_interval_minutes";

    // Preferences - User Interface
    public static final String PREF_APP_THEME                   = "app_theme";
    public static final String PREF_EXPERT_MODE                 = "expert_mode";

    // Preferences - Behaviour
    public static final String PREF_USE_ROOT                    = "use_root";
    public static final String PREF_BIND_NETWORK                = "bind_network";

    // Preferences - Syncthing Options
    public static final String PREF_WEBUI_USERNAME              = "webui_username";
    public static final String PREF_WEBUI_PASSWORD              = "webui_password";

    // Preferences - Import and Export
    public static final String PREF_BACKUP_FOLDER_NAME          = "backup_folder_name";

    // Preferences - Troubleshooting
    public static final String PREF_VERBOSE_LOG                 = "verbose_log";
    public static final String PREF_LOG_TO_FILE                 = "log_to_file";
    public static final String PREF_ENVIRONMENT_VARIABLES       = "environment_variables";
    public static final String PREF_DEBUG_FACILITIES_ENABLED    = "debug_facilities_enabled";

    // Preferences - Experimental
    public static final String PREF_USE_TOR                     = "use_tor";
    public static final String PREF_SOCKS_PROXY_ADDRESS         = "socks_proxy_address";
    public static final String PREF_HTTP_PROXY_ADDRESS          = "http_proxy_address";
    public static final String PREF_BROADCAST_SERVICE_CONTROL   = "broadcast_service_control";
    public static final String PREF_USE_WAKE_LOCK               = "wakelock_while_binary_running";

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

    public static String DYN_PREF_OBJECT_SYNC_ON_POWER_SOURCE(String objectPrefixAndId) {
        return objectPrefixAndId + "_" + PREF_POWER_SOURCE;
    }

    /**
     * Cached information which is not available on SettingsActivity.
     */
    public static final String PREF_ENABLE_SYNCTHING_CAMERA     = "enableSyncthingCamera";
    public static final String PREF_KNOWN_WIFI_SSIDS            = "knownWifiSsids";
    public static final String PREF_LAST_BINARY_VERSION         = "lastBinaryVersion";
    public static final String PREF_LOCAL_DEVICE_ID             = "localDeviceID";
    // from SystemClock.elapsedRealtime()
    public static final String PREF_LAST_RUN_TIME               = "last_run_time";

    /**
     * Cached device stats.
     */
    public static final String PREF_CACHE_DEVICE_LASTSEEN_PREFIX        = "device_lastseen_";

    /**
     * {@link ConfigXml#addSyncthingCameraFolder}
     */
    public static final String syncthingCameraFolderId          = "syncthingAndroidCamera-52x89-60es4";

    /**
     * {@link RunConditionMonitor}
     * {@link StatusFragment}
     */
    public static final String PREF_BTNSTATE_FORCE_START_STOP   = "btnStateForceStartStop";

    public static final int BTNSTATE_NO_FORCE_START_STOP        = 0;
    public static final int BTNSTATE_FORCE_START                = 1;
    public static final int BTNSTATE_FORCE_STOP                 = 2;

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
     * Available app themes
     */
    public static final String APP_THEME_FOLLOW_SYSTEM          = "-1";
    public static final String APP_THEME_LIGHT                  = "1";
    public static final String APP_THEME_DARK                   = "2";

    /**
     * Available folder types.
     */
    public static final String FOLDER_TYPE_SEND_ONLY            = "sendonly";
    public static final String FOLDER_TYPE_SEND_RECEIVE         = "sendreceive";
    public static final String FOLDER_TYPE_RECEIVE_ONLY         = "receiveonly";
    public static final String FOLDER_TYPE_RECEIVE_ENCRYPTED    = "receiveencrypted";

    /**
     * Default listening ports.
     */
    public static final Integer DEFAULT_WEBGUI_TCP_PORT         = 8384;
    public static final Integer DEFAULT_DATA_TCP_PORT           = 22000;

    /**
     * On Android 8.1, ACCESS_COARSE_LOCATION is required to access WiFi SSID.
     * This is the request code used when requesting the permission.
     */
    public static final int PERM_REQ_ACCESS_LOCATION = 999; // for issue #999

    /**
     * Interval in ms at which RestAPI is polled.
     * As a rule of thumb: Poll faster on "modern" devices.
     */
    public static final long REST_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? 3
                    : 5
    );

    public static final long GUI_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(1);

    /**
     * If the user enabled hourly one-time shot sync, the following
     * parameters are effective.
     */
    public static final int WAIT_FOR_NEXT_SYNC_DELAY_SECS       = isRunningOnEmulator() ? 20 : 3600;        // "off" state duration

    /**
     * If user enabled "Ignore schedule when charging" the following
     * parameters are effective
     */
    public static final String PREF_IGNORE_SCHEDULE_ON_CHARGE = "pref_ignore_schedule_on_charge";

    /**
     * File in the config folder that contains configuration.
     */
    public static final String CONFIG_FILE = "config.xml";

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
    public static final String PUBLIC_KEY_FILE = "cert.pem";

    public static File getPublicKeyFile(Context context) {
        return new File(context.getFilesDir(), PUBLIC_KEY_FILE);
    }

    /**
     * Name of the private key file in the data directory.
     */
    public static final String PRIVATE_KEY_FILE = "key.pem";

    public static File getPrivateKeyFile(Context context) {
        return new File(context.getFilesDir(), PRIVATE_KEY_FILE);
    }

    /**
     * Name of the folder containing the index database.
     */
    public static final String INDEX_DB_FOLDER = "index-v0.14.0.db";

    /**
     * Name of the public HTTPS CA file in the data directory.
     */
    public static final String HTTPS_CERT_FILE = "https-cert.pem";

    public static File getHttpsCertFile(Context context) {
        return new File(context.getFilesDir(), HTTPS_CERT_FILE);
    }

    /**
     * Name of the HTTPS CA key file in the data directory.
     */
    public static final String HTTPS_KEY_FILE = "https-key.pem";

    public static File getHttpsKeyFile(Context context) {
        return new File(context.getFilesDir(), HTTPS_KEY_FILE);
    }

    /**
     * Name of the export file holding the SharedPreferences backup.
     */
    static final String SHARED_PREFS_EXPORT_FILE = "sharedpreferences.dat";

    static File getSyncthingBinary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, FILENAME_SYNCTHING_BINARY);
    }

    static File getLogFile(Context context) {
        return new File(context.getFilesDir(), "syncthing.log");
    }

    /**
     * Checks if the app is running on an Android emulator (AVD).
     */
    public static Boolean isRunningOnEmulator() {
        return !TextUtils.isEmpty(Build.MANUFACTURER) &&
                !TextUtils.isEmpty(Build.MODEL) &&
                        (
                            Build.MANUFACTURER.equals("Google") ||
                            Build.MANUFACTURER.equals("unknown")
                        ) && (
                                Build.MODEL.equals("Android SDK built for x86") ||
                                Build.MODEL.equals("sdk_gphone_x86_arm"
                        )
                );
    }

    public static Boolean isDebuggable(Context context) {
        return (0 != (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
    }

    /**
     * Decide if we should enforce HTTPS when accessing the Web UI and REST API.
     * Android 4.4 and earlier don't have support for TLS 1.2 requiring us to
     * fall back to an unencrypted HTTP connection to localhost. This applies
     * to syncthing core v0.14.53+.
     */
    public static Boolean osSupportsTLS12() {
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

    /**
     * Detect kernels with a bug causing kernel oops when
     * Syncthing v1.3.0+ attempts to enable the NAT feature.
     * See: https://github.com/Catfriend1/syncthing-android/issues/505
     */
    public static Boolean osHasKernelBugIssue505() {
        String kernelVersion = java.lang.System.getProperty("os.version");
        if (kernelVersion == null) {
            return false;
        }
        /**
         * Affected kernels:
         * Samsung Note N7000 - LOS 16 - 3.0.101-gf32669ee5be #1 Tue Apr 7 20:05:58 +08 2020
         */
        return kernelVersion.startsWith("3.0.") ||
                kernelVersion.startsWith("3.4.");
    }
}
