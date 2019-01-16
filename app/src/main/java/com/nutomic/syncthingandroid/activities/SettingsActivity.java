package com.nutomic.syncthingandroid.activities;

import android.Manifest;
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
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.Languages;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.WifiSsidPreference;

import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends SyncthingActivity {

    private static final String TAG = "SettingsActivity";

    private SettingsFragment mSettingsFragment;

    public static final String EXTRA_OPEN_SUB_PREF_SCREEN =
            "com.github.catfriend1.syncthingandroid.activities.SettingsActivity.OPEN_SUB_PREF_SCREEN";

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
        // On Android 8.1, ACCESS_COARSE_LOCATION is required, see issue #999
        if (requestCode == Constants.PERM_REQ_ACCESS_COARSE_LOCATION) {
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
        // Settings/Syncthing
        private static final String KEY_WEBUI_TCP_PORT = "webUITcpPort";
        private static final String KEY_WEBUI_REMOTE_ACCESS = "webUIRemoteAccess";
        private static final String KEY_UNDO_IGNORED_DEVICES_FOLDERS = "undo_ignored_devices_folders";
        // Settings/Import and Export
        private static final String KEY_EXPORT_CONFIG = "export_config";
        private static final String KEY_IMPORT_CONFIG = "import_config";
        // Settings/Debug
        private static final String KEY_ST_RESET_DATABASE = "st_reset_database";
        private static final String KEY_ST_RESET_DELTAS = "st_reset_deltas";
        // Settings/About
        private static final String KEY_SYNCTHING_API_KEY = "syncthing_api_key";
        private static final String KEY_SYNCTHING_DATABASE_SIZE = "syncthing_database_size";

        private static final String BIND_ALL = "0.0.0.0";
        private static final String BIND_LOCALHOST = "127.0.0.1";

        @Inject NotificationHandler mNotificationHandler;
        @Inject SharedPreferences mPreferences;

        private Dialog             mCurrentPrefScreenDialog = null;

        private Preference         mCategoryRunConditions;
        private CheckBoxPreference mStartServiceOnBoot;
        private ListPreference     mPowerSource;
        private CheckBoxPreference mRunOnMobileData;
        private CheckBoxPreference mRunOnWifi;
        private CheckBoxPreference mRunOnMeteredWifi;
        private CheckBoxPreference mUseWifiWhitelist;
        private WifiSsidPreference mWifiSsidWhitelist;
        private CheckBoxPreference mRunInFlightMode;

        private Preference         mCategorySyncthingOptions;
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
        private CheckBoxPreference mWebUIRemoteAccess;
        private CheckBoxPreference mRestartOnWakeup;
        private CheckBoxPreference mUrAccepted;

        /* Experimental options */
        private CheckBoxPreference mUseRoot;
        private CheckBoxPreference mUseWakelock;
        private CheckBoxPreference mUseTor;
        private EditTextPreference mSocksProxyAddress;
        private EditTextPreference mHttpProxyAddress;

        private Preference mSyncthingVersion;
        private Preference mSyncthingApiKey;

        private Context mContext;
        private SyncthingService mSyncthingService;
        private RestApi mRestApi;

        private Options mOptions;
        private Gui mGui;

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
         * The ActionBar overlaps the preferences view.
         * Move the preferences view below the ActionBar.
         */
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            int horizontalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            int verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            TypedValue tv = new TypedValue();
            if (container.getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
            {
                // Calculate ActionBar height
                int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
                view.setPadding(horizontalMargin, actionBarHeight, horizontalMargin, verticalMargin);
            }
            return view;
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

            ListPreference languagePref = (ListPreference) findPreference(Languages.PREFERENCE_LANGUAGE);
            PreferenceScreen categoryBehaviour = (PreferenceScreen) findPreference("category_behaviour");
            if (Build.VERSION.SDK_INT >= 24) {
                categoryBehaviour.removePreference(languagePref);
            } else {
                Languages languages = new Languages(getActivity());
                languagePref.setDefaultValue(Languages.USE_SYSTEM_DEFAULT);
                languagePref.setEntries(languages.getAllNames());
                languagePref.setEntryValues(languages.getSupportedLocales());
                languagePref.setOnPreferenceChangeListener((p, o) -> {
                    languages.forceChangeLanguage(getActivity(), (String) o);
                    return false;
                });
            }
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
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_ON_WIFI);
            mPowerSource =
                    (ListPreference) findPreference(Constants.PREF_POWER_SOURCE);
            mRunInFlightMode =
                    (CheckBoxPreference) findPreference(Constants.PREF_RUN_IN_FLIGHT_MODE);

            mRunOnMeteredWifi.setEnabled(mRunOnWifi.isChecked());
            mUseWifiWhitelist.setEnabled(mRunOnWifi.isChecked());
            mWifiSsidWhitelist.setEnabled(mRunOnWifi.isChecked() && mUseWifiWhitelist.isChecked());

            screen.findPreference(Constants.PREF_POWER_SOURCE).setSummary(mPowerSource.getEntry());
            String wifiSsidSummary = TextUtils.join(", ", mPreferences.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>()));
            screen.findPreference(Constants.PREF_WIFI_SSID_WHITELIST).setSummary(TextUtils.isEmpty(wifiSsidSummary) ?
                getString(R.string.wifi_ssid_whitelist_empty) :
                getString(R.string.run_on_whitelisted_wifi_networks, wifiSsidSummary)
            );

            mCategoryRunConditions = findPreference("category_run_conditions");
            setPreferenceCategoryChangeListener(mCategoryRunConditions, this::onRunConditionPreferenceChange);

            /* Behaviour */
            mStartServiceOnBoot =
                    (CheckBoxPreference) findPreference(Constants.PREF_START_SERVICE_ON_BOOT);
            mUseRoot =
                    (CheckBoxPreference) findPreference(Constants.PREF_USE_ROOT);

            /* Syncthing options */
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
            mWebUIRemoteAccess      = (CheckBoxPreference) findPreference(KEY_WEBUI_REMOTE_ACCESS);
            mSyncthingApiKey        = findPreference(KEY_SYNCTHING_API_KEY);
            mRestartOnWakeup        = (CheckBoxPreference) findPreference("restartOnWakeup");
            mUrAccepted             = (CheckBoxPreference) findPreference("urAccepted");
            Preference undoIgnoredDevicesFolders = findPreference(KEY_UNDO_IGNORED_DEVICES_FOLDERS);

            mCategorySyncthingOptions = findPreference("category_syncthing_options");
            setPreferenceCategoryChangeListener(mCategorySyncthingOptions, this::onSyncthingPreferenceChange);
            mSyncthingApiKey.setOnPreferenceClickListener(this);
            undoIgnoredDevicesFolders.setOnPreferenceClickListener(this);

            /* Import and Export */
            Preference exportConfig = findPreference("export_config");
            Preference importConfig = findPreference("import_config");
            exportConfig.setOnPreferenceClickListener(this);
            importConfig.setOnPreferenceClickListener(this);

            /* Debugging */
            Preference debugFacilitiesEnabled       = findPreference(Constants.PREF_DEBUG_FACILITIES_ENABLED);
            Preference environmentVariables         = findPreference("environment_variables");
            Preference stResetDatabase              = findPreference("st_reset_database");
            Preference stResetDeltas                = findPreference("st_reset_deltas");

            debugFacilitiesEnabled.setOnPreferenceChangeListener(this);
            environmentVariables.setOnPreferenceChangeListener(this);
            stResetDatabase.setOnPreferenceClickListener(this);
            stResetDeltas.setOnPreferenceClickListener(this);

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

            mUseRoot.setOnPreferenceClickListener(this);
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

            openSubPrefScreen(screen);
        }

        private void openSubPrefScreen(PreferenceScreen prefScreen) {
            Bundle bundle = getArguments();
            if (bundle == null) {
                return;
            }
            String openSubPrefScreen = bundle.getString(EXTRA_OPEN_SUB_PREF_SCREEN, "");
            // Open sub preferences screen if EXTRA_OPEN_SUB_PREF_SCREEN was passed in bundle.
            if (openSubPrefScreen != null && !TextUtils.isEmpty(openSubPrefScreen)) {
                Log.v(TAG, "Transitioning to pref screen " + openSubPrefScreen);
                PreferenceScreen categoryRunConditions = (PreferenceScreen) findPreference(openSubPrefScreen);
                final ListAdapter listAdapter = prefScreen.getRootAdapter();
                final int itemsCount = listAdapter.getCount();
                for (int itemNumber = 0; itemNumber < itemsCount; ++itemNumber) {
                    if (listAdapter.getItem(itemNumber).equals(categoryRunConditions)) {
                        // Simulates click on the sub-preference
                        prefScreen.onItemClick(null, null, itemNumber, 0);
                        break;
                    }
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
                    LinearLayout root = (LinearLayout) mCurrentPrefScreenDialog.findViewById(android.R.id.list).getParent().getParent();
                    SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
                    LayoutInflater layoutInflater = syncthingActivity.getLayoutInflater();
                    Toolbar toolbar = (Toolbar) layoutInflater.inflate(R.layout.widget_toolbar, root, false);
                    root.addView(toolbar, 0);
                    toolbar.setTitle(((PreferenceScreen) preference).getTitle());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        toolbar.setTouchscreenBlocksFocus(false);
                    }
                    syncthingActivity.setSupportActionBar(toolbar);
                    syncthingActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
                }
                return true;
            }
            return super.onOptionsItemSelected(item);
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

            // Web GUI tcp port and bind ip address.
            mGui = mRestApi.getGui();
            if (mGui != null) {
                mWebUITcpPort.setText(mGui.getBindPort());
                mWebUITcpPort.setSummary(mGui.getBindPort());
                mWebUIRemoteAccess.setChecked(!BIND_LOCALHOST.equals(mGui.getBindAddress()));
            }

            mRestartOnWakeup.setChecked(mOptions.restartOnWakeup);
            mUrAccepted.setChecked(mRestApi.isUsageReportingAccepted());
        }

        @Override
        public void onDestroy() {
            if (mSyncthingService != null) {
                mSyncthingService.unregisterOnServiceStateChangeListener(this);
            }
            super.onDestroy();
        }

        private void setPreferenceCategoryChangeListener(
                Preference category, Preference.OnPreferenceChangeListener listener) {
            PreferenceScreen ps = (PreferenceScreen) category;
            for (int i = 0; i < ps.getPreferenceCount(); i++) {
                Preference p = ps.getPreference(i);
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
            }
            mPendingRunConditions = true;
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
                case KEY_WEBUI_REMOTE_ACCESS:
                    mGui.address = ((boolean) o ? BIND_ALL : BIND_LOCALHOST) + ":" + mWebUITcpPort.getSummary();
                    break;
                case "restartOnWakeup":
                    mOptions.restartOnWakeup = (boolean) o;
                    break;
                case "urAccepted":
                    mRestApi.setUsageReporting((boolean) o);
                    mOptions = mRestApi.getOptions();
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
                        mRestApi.saveConfigAndRestart();
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
                case Constants.PREF_DEBUG_FACILITIES_ENABLED:
                    mPendingConfig = true;
                    break;
                case Constants.PREF_ENVIRONMENT_VARIABLES:
                    if (((String) o).matches("^(\\w+=[\\w:/\\.]+)?( \\w+=[\\w:/\\.]+)*$")) {
                        mPendingConfig = true;
                    }
                    else {
                        Toast.makeText(getActivity(), R.string.toast_invalid_environment_variables, Toast.LENGTH_SHORT)
                                .show();
                        return false;
                    }
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
                case Constants.PREF_USE_ROOT:
                    if (mUseRoot.isChecked()) {
                        // Only check preference after root was granted.
                        mUseRoot.setChecked(false);
                        new TestRootTask(this).execute();
                    } else {
                        new Thread(() -> Util.fixAppDataPermissions(getActivity())).start();
                        mPendingConfig = true;
                    }
                    return true;
                case KEY_EXPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_export)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                new ExportConfigTask((SettingsActivity) getActivity(), mSyncthingService)
                                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_IMPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_import)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
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
                case KEY_UNDO_IGNORED_DEVICES_FOLDERS:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.undo_ignored_devices_folders_question)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                if (mRestApi == null) {
                                    Toast.makeText(getActivity(),
                                            getString(R.string.generic_error) + getString(R.string.syncthing_disabled_title),
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
            }
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
                if (haveRoot) {
                    settingsFragment.mPendingConfig = true;
                    settingsFragment.mUseRoot.setChecked(true);
                } else {
                    Toast.makeText(settingsFragment.getActivity(), R.string.toast_root_denied, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }

        /**
         * Performs export of settings, config and database in the background.
         */
        private static class ExportConfigTask extends AsyncTask<Void, String, Void> {
            private WeakReference<SettingsActivity> refSettingsActivity;
            private WeakReference<SyncthingService> refSyncthingService;
            Boolean actionSucceeded = false;

            ExportConfigTask(SettingsActivity context, SyncthingService service) {
                refSettingsActivity = new WeakReference<>(context);
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
                // Get a reference to the activity if it is still there.
                SettingsActivity settingsActivity = refSettingsActivity.get();
                if (settingsActivity == null) {
                    return;
                }
                if (!actionSucceeded) {
                    Toast.makeText(settingsActivity,
                            settingsActivity.getString(R.string.config_export_failed),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(settingsActivity,
                        settingsActivity.getString(R.string.config_export_successful,
                        Constants.EXPORT_PATH_OBJ), Toast.LENGTH_LONG).show();
                settingsActivity.finish();
            }
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
         * Calley by {@link #ImportConfigTask} after config import.
         */
        private void afterConfigImport(Boolean actionSucceeded) {
            if (!actionSucceeded) {
                Toast.makeText(getActivity(),
                    getString(R.string.config_import_failed,
                    Constants.EXPORT_PATH_OBJ), Toast.LENGTH_LONG).show();
                    return;
            }
            Toast.makeText(getActivity(),
                getString(R.string.config_imported_successful), Toast.LENGTH_LONG).show();

            // We don't have to send the config via REST on leaving activity.
            mPendingConfig = false;

            // We have to evaluate run conditions, they may have changed by the imported prefs.
            mPendingRunConditions = true;
            getActivity().finish();
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
            } else if (newValue.matches("^http://.*:\\d{1,5}$")) {
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
            String dbPath = mContext.getFilesDir() + "/" + Constants.INDEX_DB_FOLDER;
            String result = Util.runShellCommandGetOutput("/system/bin/du -sh " + dbPath, false);
            if (TextUtils.isEmpty(result)) {
                return "N/A";
            }
            String resultParts[] = result.split("\\s+");
            if (resultParts.length == 0) {
                return "N/A";
            }
            return resultParts[0];
        }
    }
}
