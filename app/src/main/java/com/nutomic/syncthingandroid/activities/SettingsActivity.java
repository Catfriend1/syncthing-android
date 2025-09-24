package com.nutomic.syncthingandroid.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.PasswordPreference;
import com.nutomic.syncthingandroid.views.WifiSsidPreference;

import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends SyncthingActivity {

    private static final String TAG = "SettingsActivity";

    private SettingsFragment mSettingsFragment;

    public static final int RESULT_RESTART_APP = 3461;

    public static final String EXTRA_OPEN_SUB_PREF_SCREEN =
            ".activities.SettingsActivity.OPEN_SUB_PREF_SCREEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mSettingsFragment = new SettingsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_OPEN_SUB_PREF_SCREEN, getIntent().getStringExtra(EXTRA_OPEN_SUB_PREF_SCREEN));
        mSettingsFragment.setArguments(bundle);
        getFragmentManager().beginTransaction()
                .replace(R.id.prefFragmentContainer, mSettingsFragment)
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // On Android 8.1, ACCESS_COARSE_LOCATION is required
        if (requestCode == Constants.PERM_REQ_ACCESS_LOCATION) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        this.startService(new Intent(this, SyncthingService.class)
                                .setAction(SyncthingService.ACTION_REFRESH_NETWORK_INFO));
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.sync_only_wifi_ssids_location_permission_rejected_dialog_title)
                                .setMessage(R.string.sync_only_wifi_ssids_location_permission_rejected_dialog_content)
                                .setPositiveButton(android.R.string.ok, null).show();
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = (SyncthingService) syncthingServiceBinder.getService();
        mSettingsFragment.setService(syncthingService);
        syncthingService.registerOnServiceStateChangeListener(mSettingsFragment);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SyncthingService.OnServiceStateChangeListener,
                Preference.OnPreferenceChangeListener,
                Preference.OnPreferenceClickListener {

        private static final String TAG = "SettingsFragment";
        // Settings/Syncthing Options
        private static final String KEY_WEBUI_TCP_PORT = "webUITcpPort";
        private static final String KEY_WEBUI_REMOTE_ACCESS = "webUIRemoteAccess";
        private static final String KEY_CLEAR_STVERSIONS = "clearStVersions";
        private static final String KEY_DOWNLOAD_SUPPORT_BUNDLE = "downloadSupportBundle";
        private static final String KEY_UNDO_IGNORED_DEVICES_FOLDERS = "undo_ignored_devices_folders";
        // Settings/Import and Export
        private static final String KEY_EXPORT_CONFIG = "export_config";
        private static final String KEY_IMPORT_CONFIG = "import_config";
        // Settings/Debug
        private static final String KEY_OPEN_ISSUE_TRACKER = "open_issue_tracker";
        private static final String KEY_ST_RESET_DATABASE = "st_reset_database";
        private static final String KEY_ST_RESET_DELTAS = "st_reset_deltas";
        // Settings/About
        private static final String KEY_SYNCTHING_API_KEY = "syncthing_api_key";
        private static final String KEY_SYNCTHING_DATABASE_SIZE = "syncthing_database_size";
        private static final String KEY_OS_OPEN_FILE_LIMIT = "os_open_file_limit";
        private static final String KEY_OPEN_SOURCE_LICENSES = "open_source_licenses";

        private static final String BIND_ALL = "0.0.0.0";
        private static final String BIND_LOCALHOST = "127.0.0.1";

        @Inject NotificationHandler mNotificationHandler;
        @Inject SharedPreferences mPreferences;

        private Dialog             mCurrentPrefScreenDialog = null;

        /* Run conditions */
        private PreferenceScreen   mCategoryRunConditions;
        private ListPreference     mPowerSource;
        private CheckBoxPreference mRunOnMobileData;
        private CheckBoxPreference mRunOnRoaming;
        private CheckBoxPreference mRunOnWifi;
        private CheckBoxPreference mRunOnMeteredWifi;
        private CheckBoxPreference mUseWifiWhitelist;
        private WifiSsidPreference mWifiSsidWhitelist;
        private CheckBoxPreference mRunInFlightMode;
        private EditTextPreference mSyncDurationMinutes;
        private EditTextPreference mSleepIntervalMinutes;

        /* Behaviour */
        private CheckBoxPreference mStartServiceOnBoot;
        private CheckBoxPreference mUseRoot;

        /* Syncthing Options */
        private PreferenceScreen   mCategorySyncthingOptions;
        private EditTextPreference mDeviceName;
        private EditTextPreference mListenAddresses;
        private EditTextPreference mMaxRecvKbps;
        private EditTextPreference mMaxSendKbps;
        private CheckBoxPreference mNatEnabled;
        private CheckBoxPreference mLocalAnnounceEnabled;
        private CheckBoxPreference mGlobalAnnounceEnabled;
        private CheckBoxPreference mRelaysEnabled;
        private EditTextPreference mGlobalAnnounceServers;
        private EditTextPreference mWebUITcpPort;
        private EditTextPreference mWebUIUsername;
        private EditTextPreference mWebUIPassword;
        private Preference mSyncthingApiKey;
        private CheckBoxPreference mWebUIRemoteAccess;
        private CheckBoxPreference mUrAccepted;
        private CheckBoxPreference mCrashReportingEnabled;
        private Preference mClearStVersions;
        private Preference mDownloadSupportBundle;

        /* Import and Export */
        private EditTextPreference mBackupRelPathToZip;
        private PasswordPreference mBackupPassword;

        /* Experimental options */
        private CheckBoxPreference mUseWakelock;
        private CheckBoxPreference mUseTor;
        private EditTextPreference mSocksProxyAddress;
        private EditTextPreference mHttpProxyAddress;

        /* About */
        private Preference mSyncthingVersion;
        private Preference mLicensePref;

        /* Context */
        private Context mContext;
        private SyncthingService mSyncthingService;
        private RestApi mRestApi;

        private Options mOptions;
        private Gui mGui;

        private Handler mHandler;
        private Boolean mPendingConfig = false;

        /**
         * Indicates if run conditions were changed and need to be
         * re-evaluated when the user leaves the preferences screen.
         */
        private Boolean mPendingRunConditions = false;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ((SyncthingApp) getActivity().getApplication()).component().inject(this);
            setHasOptionsMenu(true);
        }

        /**
         * Loads layout, sets version from Rest API.
         *
         * Manual target API as we manually check if ActionBar is available (for ActionBar back button).
         */
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            mContext = getActivity().getApplicationContext();
            super.onActivityCreated(savedInstanceState);

            addPreferencesFromResource(R.xml.app_settings);
            mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            PreferenceScreen categoryUserInterface = (PreferenceScreen) findPreference("category_user_interface");
            PreferenceScreen screen = getPreferenceScreen();

            /* Run conditions */
            mRunOnWifi =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_ON_WIFI);
            mRunOnMeteredWifi =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_ON_METERED_WIFI);
            mUseWifiWhitelist =
                    (CheckBoxPreference) findPreference(Constants.PREF_USE_WIFI_SSID_WHITELIST);
            mWifiSsidWhitelist =
                    (WifiSsidPreference) findPreference(Constants.PREF_WIFI_SSID_WHITELIST);
            mRunOnMobileData =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_ON_MOBILE_DATA);
            mRunOnRoaming =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_ON_ROAMING);
            mPowerSource =
                    (ListPreference) findPreference(Constants.PREF_POWER_SOURCE);
            mRunInFlightMode =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_IN_FLIGHT_MODE);
            mSyncDurationMinutes =
                    (EditTextPreference) findPreference(Constants.PREF_SYNC_DURATION_MINUTES);
            mSleepIntervalMinutes =
                    (EditTextPreference) findPreference(Constants.PREF_SLEEP_INTERVAL_MINUTES);

            mRunOnMeteredWifi.setEnabled(mRunOnWifi.isChecked());
            mUseWifiWhitelist.setEnabled(mRunOnWifi.isChecked());
            mWifiSsidWhitelist.setEnabled(mRunOnWifi.isChecked() && mUseWifiWhitelist.isChecked());

            mRunOnRoaming.setEnabled(mRunOnMobileData.isChecked());

            screen.findPreference(Constants.PREF_POWER_SOURCE).setSummary(mPowerSource.getEntry());
            String wifiSsidSummary = TextUtils.join(", ", mPreferences.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>()));
            screen.findPreference(Constants.PREF_WIFI_SSID_WHITELIST).setSummary(TextUtils.isEmpty(wifiSsidSummary) ?
                getString(R.string.wifi_ssid_whitelist_empty) :
                getString(R.string.run_on_whitelisted_wifi_networks, wifiSsidSummary)
            );

            mSyncDurationMinutes.setSummary(
                    getString(R.string.sync_duration_minutes_summary, mSyncDurationMinutes.getText())
            );

            mSleepIntervalMinutes.setSummary(
                    getString(R.string.sync_duration_minutes_summary, mSleepIntervalMinutes.getText())
            );

            mCategoryRunConditions = (PreferenceScreen) findPreference("category_run_conditions");
            setPreferenceCategoryChangeListener(mCategoryRunConditions, this::onRunConditionPreferenceChange);

            /* User Interface */
            setPreferenceCategoryChangeListener(categoryUserInterface, this::onUserInterfacePreferenceChange);

            /* Behaviour */
            PreferenceScreen categoryBehaviour = (PreferenceScreen) findPreference("category_behaviour");
            mStartServiceOnBoot =
                    (CheckBoxPreference) findPreference(Constants.PREF_START_SERVICE_ON_BOOT);
            mUseRoot =
                    (CheckBoxPreference) findPreference(Constants.PREF_USE_ROOT);
            setPreferenceCategoryChangeListener(categoryBehaviour, this::onBehaviourPreferenceChange);

            /* Syncthing Options */
            mDeviceName             = (EditTextPreference) findPreference("deviceName");
            mListenAddresses        = (EditTextPreference) findPreference("listenAddresses");
            mMaxRecvKbps            = (EditTextPreference) findPreference("maxRecvKbps");
            mMaxSendKbps            = (EditTextPreference) findPreference("maxSendKbps");
            mNatEnabled             = (CheckBoxPreference) findPreference("natEnabled");
            mLocalAnnounceEnabled   = (CheckBoxPreference) findPreference("localAnnounceEnabled");
            mGlobalAnnounceEnabled  = (CheckBoxPreference) findPreference("globalAnnounceEnabled");
            mRelaysEnabled          = (CheckBoxPreference) findPreference("relaysEnabled");
            mGlobalAnnounceServers  = (EditTextPreference) findPreference("globalAnnounceServers");
            mWebUITcpPort           = (EditTextPreference) findPreference(KEY_WEBUI_TCP_PORT);
            mWebUIUsername          = (EditTextPreference) findPreference(Constants.PREF_WEBUI_USERNAME);
            mWebUIPassword          = (EditTextPreference) findPreference(Constants.PREF_WEBUI_PASSWORD);
            mWebUIRemoteAccess      = (CheckBoxPreference) findPreference(KEY_WEBUI_REMOTE_ACCESS);
            mSyncthingApiKey        = findPreference(KEY_SYNCTHING_API_KEY);
            mUrAccepted             = (CheckBoxPreference) findPreference("urAccepted");
            mCrashReportingEnabled  = (CheckBoxPreference) findPreference("crashReportingEnabled");
            mClearStVersions        = findPreference(KEY_CLEAR_STVERSIONS);
            mDownloadSupportBundle  = findPreference(KEY_DOWNLOAD_SUPPORT_BUNDLE);
            Preference undoIgnoredDevicesFolders = findPreference(KEY_UNDO_IGNORED_DEVICES_FOLDERS);

            mCategorySyncthingOptions = (PreferenceScreen) findPreference("category_syncthing_options");
            setPreferenceCategoryChangeListener(mCategorySyncthingOptions, this::onSyncthingPreferenceChange);
            mSyncthingApiKey.setOnPreferenceClickListener(this);
            mClearStVersions.setOnPreferenceClickListener(this);
            mDownloadSupportBundle.setOnPreferenceClickListener(this);
            undoIgnoredDevicesFolders.setOnPreferenceClickListener(this);

            /* Import and Export */
            Preference exportConfig = findPreference("export_config");
            exportConfig.setOnPreferenceClickListener(this);

            Preference importConfig = findPreference("import_config");
            importConfig.setOnPreferenceClickListener(this);

            mBackupRelPathToZip = (EditTextPreference) findPreference(Constants.PREF_BACKUP_REL_PATH_TO_ZIP);
            mBackupRelPathToZip.setSummary(mBackupRelPathToZip.getText());
            mBackupRelPathToZip.setOnPreferenceChangeListener(this);

            mBackupPassword = (PasswordPreference) findPreference(Constants.PREF_BACKUP_PASSWORD);
            mBackupPassword.setText(mBackupPassword.getText());
            mBackupPassword.setOnPreferenceChangeListener(this);

            /* Troubleshooting */
            Preference verboseLog                   = findPreference(Constants.PREF_VERBOSE_LOG);
            Preference openIssueTracker             = findPreference(KEY_OPEN_ISSUE_TRACKER);
            Preference debugFacilitiesEnabled       = findPreference(Constants.PREF_DEBUG_FACILITIES_ENABLED);
            Preference environmentVariables         = findPreference(Constants.PREF_ENVIRONMENT_VARIABLES);
            Preference stResetDatabase              = findPreference("st_reset_database");
            Preference stResetDeltas                = findPreference("st_reset_deltas");

            verboseLog.setOnPreferenceClickListener(this);
            openIssueTracker.setOnPreferenceClickListener(this);
            debugFacilitiesEnabled.setOnPreferenceChangeListener(this);
            environmentVariables.setOnPreferenceChangeListener(this);
            stResetDatabase.setOnPreferenceClickListener(this);
            stResetDeltas.setOnPreferenceClickListener(this);

            screen.findPreference(KEY_OPEN_ISSUE_TRACKER).setSummary(getString(R.string.open_issue_tracker_summary, getString(R.string.issue_tracker_url)));

            /* Experimental options */
            mUseTor                         = (CheckBoxPreference) findPreference(Constants.PREF_USE_TOR);
            mSocksProxyAddress              = (EditTextPreference) findPreference(Constants.PREF_SOCKS_PROXY_ADDRESS);
            mHttpProxyAddress               = (EditTextPreference) findPreference(Constants.PREF_HTTP_PROXY_ADDRESS);
            mUseWakelock                    = (CheckBoxPreference) findPreference(Constants.PREF_USE_WAKE_LOCK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                /* Wakelocks are only valid on Android 5 or lower. */
                mUseWakelock.setEnabled(false);
                mUseWakelock.setChecked(false);
            }

            mUseWakelock.setOnPreferenceChangeListener(this);
            mUseTor.setOnPreferenceChangeListener(this);

            mSocksProxyAddress.setEnabled(!(Boolean) mUseTor.isChecked());
            mSocksProxyAddress.setOnPreferenceChangeListener(this);
            handleSocksProxyPreferenceChange(screen.findPreference(Constants.PREF_SOCKS_PROXY_ADDRESS),  mPreferences.getString(Constants.PREF_SOCKS_PROXY_ADDRESS, ""));
            mHttpProxyAddress.setEnabled(!(Boolean) mUseTor.isChecked());
            mHttpProxyAddress.setOnPreferenceChangeListener(this);
            handleHttpProxyPreferenceChange(screen.findPreference(Constants.PREF_HTTP_PROXY_ADDRESS), mPreferences.getString(Constants.PREF_HTTP_PROXY_ADDRESS, ""));

            /* About */
            Preference appVersion   = findPreference("app_version");
            mSyncthingVersion       = findPreference("syncthing_version");
            try {
                String versionName = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                appVersion.setSummary("v" + versionName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Failed to get app version name");
            }
            screen.findPreference(KEY_SYNCTHING_DATABASE_SIZE).setSummary(getDatabaseSize());
            screen.findPreference(KEY_OS_OPEN_FILE_LIMIT).setSummary(getOpenFileLimit());
            mLicensePref            = findPreference(KEY_OPEN_SOURCE_LICENSES);
            mLicensePref.setOnPreferenceClickListener(this);

            // Check if we should directly show a sub preference screen.
            Bundle bundle = getArguments();
            if (bundle != null) {
                // Fix issue #247: "Calling sub pref screen directly won't show toolbar on top"
                mHandler =  new Handler();
                mHandler.post(() -> {
                    // Open sub preferences screen if EXTRA_OPEN_SUB_PREF_SCREEN was passed in bundle.
                    openSubPrefScreen(screen, bundle.getString(EXTRA_OPEN_SUB_PREF_SCREEN, ""));
                });
            }
        }

        private void openSubPrefScreen(PreferenceScreen parentPrefScreen, String subPrefScreenId) {
            if (parentPrefScreen == null ||
                    subPrefScreenId == null ||
                    TextUtils.isEmpty(subPrefScreenId)) {
                return;
            }
            Log.v(TAG, "Transitioning to pref screen " + subPrefScreenId);
            PreferenceScreen desiredSubPrefScreen = (PreferenceScreen) findPreference(subPrefScreenId);
            final ListAdapter listAdapter = parentPrefScreen.getRootAdapter();
            final int itemsCount = listAdapter.getCount();
            for (int itemNumber = 0; itemNumber < itemsCount; ++itemNumber) {
                if (listAdapter.getItem(itemNumber).equals(desiredSubPrefScreen)) {
                    // Simulates click on the sub-preference. This will invoke {@link #onPreferenceTreeClick} subsequently.
                    parentPrefScreen.onItemClick(null, null, itemNumber, 0);
                    break;
                }
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            super.onPreferenceTreeClick(preferenceScreen, preference);
            if (preference instanceof PreferenceScreen) {
                // User has clicked on a sub-preferences screen.
                try {
                    mCurrentPrefScreenDialog = ((PreferenceScreen) preference).getDialog();
                    LinearLayout root;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        root = (LinearLayout) mCurrentPrefScreenDialog.findViewById(android.R.id.list).getParent().getParent();
                    } else {
                        root = (LinearLayout) mCurrentPrefScreenDialog.findViewById(android.R.id.list).getParent();
                    }
                    SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
                    LayoutInflater layoutInflater = syncthingActivity.getLayoutInflater();
                    Toolbar toolbar = (Toolbar) layoutInflater.inflate(R.layout.widget_toolbar, root, false);
                    root.addView(toolbar, 0);
                    toolbar.setTitle(((PreferenceScreen) preference).getTitle());
                    registerActionBar(toolbar);
                } catch (Exception e) {
                    /**
                     * The above code has been verified working but due to known bugs in the
                     * support library on different Android versions better be safe in case
                     * it breaks.
                     */
                    Log.e(TAG, "onPreferenceTreeClick", e);
                }
            }
            return false;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                if (mCurrentPrefScreenDialog == null) {
                    // User is on the top preferences screen.
                    getActivity().onBackPressed();
                } else {
                    // User is on a sub-preferences screen.
                    mCurrentPrefScreenDialog.dismiss();
                    mCurrentPrefScreenDialog = null;

                    // We need to re-register the action bar.
                    registerActionBar(null);
                }
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private void registerActionBar(Toolbar toolbar) {
            SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
            if (toolbar == null) {
                toolbar = (Toolbar) syncthingActivity.findViewById(R.id.toolbar);
            }
            if (toolbar == null) {
                Log.w(TAG, "registerActionBar: toolbar == null");
                return;
            }
            toolbar.setTouchscreenBlocksFocus(false);
            syncthingActivity.setSupportActionBar(toolbar);
            syncthingActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        public void setService(SyncthingService syncthingService) {
            mSyncthingService = syncthingService;
        }

        @Override
        public void onServiceStateChange(SyncthingService.State currentState) {
            mRestApi = mSyncthingService.getApi();
            boolean isSyncthingRunning = (mRestApi != null) &&
                        mRestApi.isConfigLoaded() &&
                        (currentState == SyncthingService.State.ACTIVE);
            mCategorySyncthingOptions.setEnabled(isSyncthingRunning);

            if (!isSyncthingRunning) {
                return;
            }

            mSyncthingVersion.setSummary(mRestApi.getVersion());
            mSyncthingApiKey.setSummary(mRestApi.getApiKey());
            mOptions = mRestApi.getOptions();
            if (mOptions != null) {
                Joiner joiner = Joiner.on(", ");
                mDeviceName.setText(mRestApi.getLocalDevice().name);
                mListenAddresses.setText(joiner.join(mOptions.listenAddresses));
                mMaxRecvKbps.setText(Integer.toString(mOptions.maxRecvKbps));
                mMaxSendKbps.setText(Integer.toString(mOptions.maxSendKbps));
                mNatEnabled.setChecked(mOptions.natEnabled);
                mLocalAnnounceEnabled.setChecked(mOptions.localAnnounceEnabled);
                mGlobalAnnounceEnabled.setChecked(mOptions.globalAnnounceEnabled);
                mRelaysEnabled.setChecked(mOptions.relaysEnabled);
                mGlobalAnnounceServers.setText(joiner.join(mOptions.globalAnnounceServers));
                mUrAccepted.setChecked(mRestApi.isUsageReportingAccepted());
                mCrashReportingEnabled.setChecked(mOptions.crashReportingEnabled);
            }

            // Web GUI tcp port and bind ip address.
            mGui = mRestApi.getGui();
            if (mGui != null) {
                mWebUITcpPort.setText(mGui.getBindPort());
                mWebUITcpPort.setSummary(mGui.getBindPort());

                mWebUIUsername.setText(mGui.user);
                mWebUIUsername.setSummary(mGui.user);

                mWebUIRemoteAccess.setChecked(!BIND_LOCALHOST.equals(mGui.getBindAddress()));
                mDownloadSupportBundle.setEnabled(true);
            }
        }

        @Override
        public void onDestroy() {
            if (mSyncthingService != null) {
                mSyncthingService.unregisterOnServiceStateChangeListener(this);
            }
            super.onDestroy();
        }

        private void setPreferenceCategoryChangeListener(
                PreferenceScreen category, Preference.OnPreferenceChangeListener listener) {
            for (int i = 0; i < category.getPreferenceCount(); i++) {
                Preference p = category.getPreference(i);
                p.setOnPreferenceChangeListener(listener);
            }
        }

        public boolean onRunConditionPreferenceChange(Preference preference, Object o) {
            switch (preference.getKey()) {
                case Constants.PREF_POWER_SOURCE:
                    mPowerSource.setValue(o.toString());
                    preference.setSummary(mPowerSource.getEntry());
                    break;
                case Constants.PREF_RUN_ON_WIFI:
                    mRunOnMeteredWifi.setEnabled((Boolean) o);
                    mUseWifiWhitelist.setEnabled((Boolean) o);
                    mWifiSsidWhitelist.setEnabled((Boolean) o && mUseWifiWhitelist.isChecked());
                    break;
                case Constants.PREF_USE_WIFI_SSID_WHITELIST:
                    mWifiSsidWhitelist.setEnabled((Boolean) o);
                    break;
                case Constants.PREF_WIFI_SSID_WHITELIST:
                    String wifiSsidSummary = TextUtils.join(", ", (Set<String>) o);
                    preference.setSummary(TextUtils.isEmpty(wifiSsidSummary) ?
                        getString(R.string.wifi_ssid_whitelist_empty) :
                        getString(R.string.run_on_whitelisted_wifi_networks, wifiSsidSummary)
                    );
                    break;
                case Constants.PREF_RUN_ON_MOBILE_DATA:
                    mRunOnRoaming.setEnabled((Boolean) o);
                    break;
                case Constants.PREF_SYNC_DURATION_MINUTES:
                    String durationMinutes = o.toString();
                    if (TextUtils.isEmpty(durationMinutes)) {
                        return false;
                    }
                    preference.setSummary(getString(R.string.sync_duration_minutes_summary, durationMinutes));
                    break;
                case Constants.PREF_SLEEP_INTERVAL_MINUTES:
                    String intervalMinutes = o.toString();
                    if (TextUtils.isEmpty(intervalMinutes)) {
                        return false;
                    }
                    if (Integer.parseInt(intervalMinutes) <= 0) {
                        return false;
                    }
                    preference.setSummary(getString(R.string.sync_duration_minutes_summary, intervalMinutes));
                    break;
            }
            mPendingRunConditions = true;
            return true;
        }

        public boolean onUserInterfacePreferenceChange(Preference preference, Object o) {
            switch (preference.getKey()) {
                case Constants.PREF_APP_THEME:
                    String newTheme = (String) o;
                    String prevTheme = mPreferences.getString(Constants.PREF_APP_THEME, Constants.APP_THEME_FOLLOW_SYSTEM);
                    if (!newTheme.equals(prevTheme)) {
                        ConfigRouter config = new ConfigRouter(getActivity());
                        Gui gui = config.getGui(mRestApi);
                        gui.theme = newTheme.equals(Constants.APP_THEME_DARK) ? "dark" : "default";
                        config.updateGui(mRestApi, gui);
                        getAppRestartConfirmationDialog(getActivity())
                                .show();
                    }
                    break;
            }
            return true;
        }

        public boolean onBehaviourPreferenceChange(Preference preference, Object o) {
            switch (preference.getKey()) {
                case Constants.PREF_USE_ROOT:
                    if ((Boolean) o) {
                        new TestRootTask(this).execute();
                    } else {
                        new Thread(() -> Util.fixAppDataPermissions(getActivity())).start();
                        mPendingConfig = true;
                    }
                    break;
            }
            return true;
        }

        public boolean onSyncthingPreferenceChange(Preference preference, Object o) {
            Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
            switch (preference.getKey()) {
                case "deviceName":
                    Device localDevice = mRestApi.getLocalDevice();
                    localDevice.name = (String) o;
                    mRestApi.updateDevice(localDevice);
                    break;
                case "listenAddresses":
                    mOptions.listenAddresses = Iterables.toArray(splitter.split((String) o), String.class);
                    break;
                case "maxRecvKbps":
                    int maxRecvKbps = 0;
                    try {
                        maxRecvKbps = Integer.parseInt((String) o);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.invalid_integer_value, 0, Integer.MAX_VALUE), Toast.LENGTH_LONG)
                                .show();
                        return false;
                    }
                    mOptions.maxRecvKbps = maxRecvKbps;
                    break;
                case "maxSendKbps":
                    int maxSendKbps = 0;
                    try {
                        maxSendKbps = Integer.parseInt((String) o);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.invalid_integer_value, 0, Integer.MAX_VALUE), Toast.LENGTH_LONG)
                                .show();
                        return false;
                    }
                    mOptions.maxSendKbps = maxSendKbps;
                    break;
                case "natEnabled":
                    mOptions.natEnabled = (boolean) o;
                    break;
                case "localAnnounceEnabled":
                    mOptions.localAnnounceEnabled = (boolean) o;
                    break;
                case "globalAnnounceEnabled":
                    mOptions.globalAnnounceEnabled = (boolean) o;
                    break;
                case "relaysEnabled":
                    mOptions.relaysEnabled = (boolean) o;
                    break;
                case "globalAnnounceServers":
                    mOptions.globalAnnounceServers = Iterables.toArray(splitter.split((String) o), String.class);
                    break;
                case KEY_WEBUI_TCP_PORT:
                    Integer webUITcpPort = 0;
                    try {
                        webUITcpPort = Integer.parseInt((String) o);
                    } catch (Exception e) {
                    }
                    if (webUITcpPort < 1024 || webUITcpPort > 65535) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.invalid_port_number, 1024, 65535), Toast.LENGTH_LONG)
                                .show();
                        return false;
                    }
                    mWebUITcpPort.setSummary(Integer.toString(webUITcpPort));
                    mGui.address = mGui.getBindAddress() + ":" + Integer.toString(webUITcpPort);
                    break;
                case Constants.PREF_WEBUI_USERNAME:
                    mWebUIUsername.setSummary((String) o);
                    mGui.user = (String) o;
                    break;
                case Constants.PREF_WEBUI_PASSWORD:
                    mGui.password = BCrypt.hashpw((String) o, BCrypt.gensalt(4));
                    break;
                case KEY_WEBUI_REMOTE_ACCESS:
                    mGui.address = ((boolean) o ? BIND_ALL : BIND_LOCALHOST) + ":" + mWebUITcpPort.getSummary();
                    break;
                case "urAccepted":
                    mRestApi.setUsageReporting((boolean) o);
                    mOptions = mRestApi.getOptions();
                    break;
                case "crashReportingEnabled":
                    mOptions.crashReportingEnabled = (boolean) o;
                    break;
                default: throw new InvalidParameterException();
            }

            mRestApi.editSettings(mGui, mOptions);
            mPendingConfig = true;
            return true;
        }

        @Override
        public void onStop() {
            if (mSyncthingService != null) {
                mNotificationHandler.updatePersistentNotification(mSyncthingService);
                if (mPendingConfig) {
                    if (mRestApi != null &&
                            mSyncthingService.getCurrentState() != SyncthingService.State.DISABLED) {
                        mRestApi.sendConfig();
                        mPendingConfig = false;
                    }
                }
                if (mPendingRunConditions) {
                    mSyncthingService.evaluateRunConditions();
                }
            }
            super.onStop();
        }

        /**
         * Sends the updated value to {@link RestApi}, and sets it as the summary
         * for EditTextPreference.
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            switch (preference.getKey()) {
                case Constants.PREF_BACKUP_REL_PATH_TO_ZIP:
                    preference.setSummary((String) o);
                    break;
                case Constants.PREF_DEBUG_FACILITIES_ENABLED:
                    mPendingConfig = true;
                    break;
                case Constants.PREF_ENVIRONMENT_VARIABLES:
                    // Verify if valid environment VAR=VALUE pairs were given as text string.
                    if (!((String) o).isEmpty()) {
                        for (String e : ((String) o).split(" ")) {
                            if (e.split("=", 2).length != 2) {
                                // Found an invalid "VAR=VALUE" pair.
                                Toast.makeText(getActivity(), R.string.toast_invalid_environment_variables, Toast.LENGTH_SHORT)
                                        .show();
                                return false;
                            }
                        }
                    }
                    mPendingConfig = true;
                    break;
                case Constants.PREF_USE_WAKE_LOCK:
                    mPendingConfig = true;
                    break;
                case Constants.PREF_USE_TOR:
                    mSocksProxyAddress.setEnabled(!(Boolean) o);
                    mHttpProxyAddress.setEnabled(!(Boolean) o);
                    mPendingConfig = true;
                    break;
                case Constants.PREF_SOCKS_PROXY_ADDRESS:
                    if (o.toString().trim().equals(mPreferences.getString(Constants.PREF_SOCKS_PROXY_ADDRESS, "")))
                        return false;
                    if (handleSocksProxyPreferenceChange(preference, o.toString().trim())) {
                        mPendingConfig = true;
                    } else {
                        return false;
                    }
                    break;
                case Constants.PREF_HTTP_PROXY_ADDRESS:
                    if (o.toString().trim().equals(mPreferences.getString(Constants.PREF_HTTP_PROXY_ADDRESS, "")))
                        return false;
                    if (handleHttpProxyPreferenceChange(preference, o.toString().trim())) {
                        mPendingConfig = true;
                    } else {
                        return false;
                    }
                    break;
            }

            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final Intent intent;
            switch (preference.getKey()) {
                case Constants.PREF_VERBOSE_LOG:
                    getAppRestartConfirmationDialog(getActivity())
                            .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
                                // Revert.
                                ((CheckBoxPreference) preference).setChecked(!((CheckBoxPreference) preference).isChecked());
                            })
                            .show();
                    return true;
                case KEY_OPEN_ISSUE_TRACKER:
                    intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra(WebViewActivity.EXTRA_WEB_URL, getString(R.string.issue_tracker_url));
                    startActivity(intent);
                    return true;
                case KEY_EXPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_export)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                new ExportConfigTask(this, mSyncthingService)
                                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_IMPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_import)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                // Import will discard our pending config changes.
                                mPendingConfig = false;
                                new ImportConfigTask(this, mSyncthingService)
                                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_SYNCTHING_API_KEY:
                        // Copy syncthing's API key to clipboard.
                        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(getString(R.string.syncthing_api_key), mSyncthingApiKey.getSummary());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getActivity(), R.string.api_key_copied_to_clipboard, Toast.LENGTH_SHORT)
                                .show();
                        return true;
                    default:
                        return false;
                case KEY_CLEAR_STVERSIONS:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.clear_stversions_question)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                ConfigRouter config = new ConfigRouter(getActivity());
                                if (clearStVersions(config.getFolders(null))) {
                                    Toast.makeText(getActivity(),
                                            getString(R.string.clear_stversions_done),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_DOWNLOAD_SUPPORT_BUNDLE:
                    onDownloadSupportBundleClick();
                    return true;
                case KEY_UNDO_IGNORED_DEVICES_FOLDERS:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.undo_ignored_devices_folders_question)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                if (mRestApi == null) {
                                    Toast.makeText(getActivity(),
                                            getString(R.string.generic_error) + getString(R.string.syncthing_disabled),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                mRestApi.undoIgnoredDevicesAndFolders();
                                mPendingConfig = true;
                                Toast.makeText(getActivity(),
                                        getString(R.string.undo_ignored_devices_folders_done),
                                        Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_ST_RESET_DATABASE:
                    intent = new Intent(getActivity(), SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESET_DATABASE);

                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.st_reset_database_title)
                            .setMessage(R.string.st_reset_database_question)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                getActivity().startService(intent);
                                Toast.makeText(getActivity(), R.string.st_reset_database_done, Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
                            })
                            .show();
                    return true;
                case KEY_ST_RESET_DELTAS:
                    intent = new Intent(getActivity(), SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESET_DELTAS);

                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.st_reset_deltas_title)
                            .setMessage(R.string.st_reset_deltas_question)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                getActivity().startService(intent);
                                Toast.makeText(getActivity(), R.string.st_reset_deltas_done, Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
                            })
                            .show();
                    return true;
                case KEY_OPEN_SOURCE_LICENSES:
                    startActivity(new Intent(getActivity(), LicenseActivity.class));
                    return true;
            }
        }

        private void onDownloadSupportBundleClick() {
            if (mRestApi == null) {
                Toast.makeText(mContext,
                        getString(R.string.generic_error) + getString(R.string.syncthing_disabled),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mDownloadSupportBundle.setEnabled(false);
            mDownloadSupportBundle.setSummary(R.string.download_support_bundle_in_progress);
            String localDeviceName = mRestApi.getLocalDevice().getDisplayName();
            String targetFileFullFN = FileUtils.getExternalStorageDownloadsDirectory() + "/" +
                    "syncthing-support-bundle_" + localDeviceName + ".zip";
            File targetFile = new File(targetFileFullFN);

            mRestApi.downloadSupportBundle(targetFile, failSuccess -> {
                mDownloadSupportBundle.setEnabled(true);
                if (!failSuccess) {
                    mDownloadSupportBundle.setSummary(R.string.download_support_bundle_failed);
                    return;
                }
                mDownloadSupportBundle.setSummary(getString(R.string.download_support_bundle_succeeded, targetFileFullFN));
            });
        }

        /**
         * Provides a template for an AlertDialog which quits and restarts the
         * whole app including all of its activities and services.
         * Use rarely as it's annoying for a user having to restart the whole app.
         */
        private static AlertDialog.Builder getAppRestartConfirmationDialog(Activity activity) {
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_settings_restart_app_title)
                    .setMessage(R.string.dialog_settings_restart_app_question)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        activity.setResult(RESULT_RESTART_APP);
                        activity.finish();
                    })
                    .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {});
        }

        /**
         * Enables or disables {@link #mUseRoot} preference depending whether root is available.
         */
        private static class TestRootTask extends AsyncTask<Void, Void, Boolean> {
            private WeakReference<SettingsFragment> refSettingsFragment;

            TestRootTask(SettingsFragment context) {
                refSettingsFragment = new WeakReference<>(context);
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                return Shell.SU.available();
            }

            @Override
            protected void onPostExecute(Boolean haveRoot) {
                // Get a reference to the fragment if it is still there.
                SettingsFragment settingsFragment = refSettingsFragment.get();
                if (settingsFragment == null) {
                    return;
                }
                settingsFragment.mUseRoot.setOnPreferenceChangeListener(null);
                settingsFragment.mUseRoot.setChecked(haveRoot);
                if (haveRoot) {
                    settingsFragment.mPendingConfig = true;
                } else {
                    SyncthingActivity syncthingActivity = (SyncthingActivity) settingsFragment.getActivity();
                    if (syncthingActivity != null && !syncthingActivity.isFinishing()) {
                        Toast.makeText(syncthingActivity, R.string.toast_root_denied, Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                settingsFragment.mUseRoot.setOnPreferenceChangeListener(settingsFragment::onBehaviourPreferenceChange);
            }
        }

        /**
         * Performs export of settings, config and database in the background.
         */
        private static class ExportConfigTask extends AsyncTask<Void, String, Void> {
            private WeakReference<SettingsFragment> refSettingsFragment;
            private WeakReference<SyncthingService> refSyncthingService;
            Boolean actionSucceeded = false;

            ExportConfigTask(SettingsFragment context, SyncthingService service) {
                refSettingsFragment = new WeakReference<>(context);
                refSyncthingService = new WeakReference<>(service);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                SyncthingService syncthingService = refSyncthingService.get();
                if (syncthingService == null) {
                    cancel(true);
                    return null;
                }
                actionSucceeded = syncthingService.exportConfig();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                SettingsFragment settingsFragment = refSettingsFragment.get();
                if (settingsFragment == null) {
                    return;
                }
                settingsFragment.afterConfigExport(actionSucceeded);
            }
        }

        /**
         * Called by {@link SyncthingService#exportConfig} after config export.
         */
        private void afterConfigExport(Boolean actionSucceeded) {
            SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
            if (syncthingActivity == null || syncthingActivity.isFinishing()) {
                return;
            }

            if (!actionSucceeded) {
                Toast.makeText(syncthingActivity,
                        getString(R.string.config_export_failed),
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(syncthingActivity, LogActivity.class));
                return;
            }
            Toast.makeText(
                    syncthingActivity,
                    getString(R.string.config_export_successful_no_path),
                    Toast.LENGTH_LONG
            ).show();
            syncthingActivity.finish();
        }

        /**
         * Performs import of settings, config and database in the background.
         */
        private static class ImportConfigTask extends AsyncTask<Void, String, Void> {
            private WeakReference<SettingsFragment> refSettingsFragment;
            private WeakReference<SyncthingService> refSyncthingService;
            Boolean actionSucceeded = false;

            ImportConfigTask(SettingsFragment context, SyncthingService service) {
                refSettingsFragment = new WeakReference<>(context);
                refSyncthingService = new WeakReference<>(service);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                SyncthingService syncthingService = refSyncthingService.get();
                if (syncthingService == null) {
                    cancel(true);
                    return null;
                }
                actionSucceeded = syncthingService.importConfig();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                // Get a reference to the activity if it is still there.
                SettingsFragment settingsFragment = refSettingsFragment.get();
                if (settingsFragment == null) {
                    return;
                }
                settingsFragment.afterConfigImport(actionSucceeded);
            }
        }

        /**
         * Called by {@link SyncthingService#importConfig} after config import.
         */
        private void afterConfigImport(Boolean actionSucceeded) {
            SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
            if (syncthingActivity == null || syncthingActivity.isFinishing()) {
                return;
            }

            if (!actionSucceeded) {
                Toast.makeText(syncthingActivity,
                        getString(R.string.config_import_failed_no_path),
                        Toast.LENGTH_LONG
                ).show();
                startActivity(new Intent(syncthingActivity, LogActivity.class));
                return;
            }
            Toast.makeText(syncthingActivity,
                getString(R.string.config_imported_successful), Toast.LENGTH_LONG).show();

            // Apply theme from restored config.
            Integer prefAppTheme = Integer.parseInt(mPreferences.getString(Constants.PREF_APP_THEME, Constants.APP_THEME_FOLLOW_SYSTEM));
            AppCompatDelegate.setDefaultNightMode(prefAppTheme);

            // We don't have to send the config via REST on leaving activity.
            mPendingConfig = false;

            // We have to evaluate run conditions, they may have changed by the imported prefs.
            mPendingRunConditions = true;
            syncthingActivity.finish();
        }

        /**
         * Handles a new user input for the SOCKS proxy preference.
         * Returns if the changed setting requires a restart.
         */
        private boolean handleSocksProxyPreferenceChange(Preference preference, String newValue) {
            // Valid input is either a proxy address or an empty field to disable the proxy.
            if (newValue.equals("")) {
                preference.setSummary(getString(R.string.do_not_use_proxy) + " " + getString(R.string.generic_example) + ": " + getString(R.string.socks_proxy_address_example));
                return true;
            } else if (newValue.matches("^socks5://.*:\\d{1,5}$")) {
                preference.setSummary(getString(R.string.use_proxy) + " " + newValue);
                return true;
            } else {
                Toast.makeText(getActivity(), R.string.toast_invalid_socks_proxy_address, Toast.LENGTH_SHORT)
                        .show();
                return false;
            }
        }

        /**
         * Handles a new user input for the HTTP(S) proxy preference.
         * Returns if the changed setting requires a restart.
         */
        private boolean handleHttpProxyPreferenceChange(Preference preference, String newValue) {
            // Valid input is either a proxy address or an empty field to disable the proxy.
            if (newValue.equals("")) {
                preference.setSummary(getString(R.string.do_not_use_proxy) + " " + getString(R.string.generic_example) + ": " + getString(R.string.http_proxy_address_example));
                return true;
            } else if (newValue.matches("^https?://.*:\\d{1,5}$")) {
                preference.setSummary(getString(R.string.use_proxy) + " " + newValue);
                return true;
            } else {
                Toast.makeText(getActivity(), R.string.toast_invalid_http_proxy_address, Toast.LENGTH_SHORT)
                        .show();
                return false;
            }
        }

        /**
         * Calculates the size of the syncthing database on disk.
         */
        private String getDatabaseSize() {
            String dbPath = Constants.getIndexDbFolder(mContext).getAbsolutePath();
            String result = Util.runShellCommandGetOutput("/system/bin/du -sh " + dbPath, false);
            if (TextUtils.isEmpty(result)) {
                return "N/A";
            }
            String[] resultParts = result.split("\\s+");
            if (resultParts.length == 0) {
                return "N/A";
            }
            return resultParts[0];
        }

        /**
         * Get current open file limit enforced by the Android OS.
         */
        private String getOpenFileLimit() {
            String shellCommand = "ulimit -n";
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                shellCommand = "/system/bin/" + shellCommand;
            }
            String result = Util.runShellCommandGetOutput(shellCommand, false);
            if (TextUtils.isEmpty(result)) {
                return "N/A";
            }
            return result;
        }

        public static boolean clearStVersions(List<Folder> folders) {
            for (Folder folder : folders) {
                File dir = new File(folder.path + "/" + Constants.FOLDER_NAME_STVERSIONS);
                if (dir.exists() && dir.isDirectory()) {
                    Log.d(TAG, "Delete dir: " + dir);
                    deleteContents(dir);
                }
            }
            return true;
        }

        private static void deleteContents(File dir) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteContents(file);
                    }
                    file.delete();
                }
            }
        }

    }
}
