package com.nutomic.syncthingandroid.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Languages;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.WifiSsidPreference;

import java.security.InvalidParameterException;

import javax.inject.Inject;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends SyncthingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SyncthingActivity.OnServiceConnectedListener,
            SyncthingService.OnApiChangeListener, Preference.OnPreferenceChangeListener,
            Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

        private static final String TAG = "SettingsFragment";
        private static final String KEY_STTRACE = "sttrace";
        private static final String KEY_EXPORT_CONFIG = "export_config";
        private static final String KEY_IMPORT_CONFIG = "import_config";
        private static final String KEY_STRESET = "streset";

        @Inject NotificationHandler mNotificationHandler;
        @Inject SharedPreferences mPreferences;

        private CheckBoxPreference mAlwaysRunInBackground;
        private CheckBoxPreference mSyncOnlyCharging;
        private CheckBoxPreference mSyncOnlyWifi;
        private WifiSsidPreference mSyncOnlyOnSSIDs;

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
        private EditTextPreference mAddress;
        private CheckBoxPreference mUrAccepted;

        private Preference mCategoryBackup;

        private CheckBoxPreference mUseRoot;

        private Preference mSyncthingVersion;

        private SyncthingService mSyncthingService;
        private RestApi mApi;

        private Options mOptions;
        private Config.Gui mGui;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ((SyncthingApp) getActivity().getApplication()).component().inject(this);
            ((SyncthingActivity) getActivity()).registerOnServiceConnectedListener(this);
            mPreferences.registerOnSharedPreferenceChangeListener(this);
        }

        /**
         * Loads layout, sets version from Rest API.
         *
         * Manual target API as we manually check if ActionBar is available (for ActionBar back button).
         */
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            addPreferencesFromResource(R.xml.app_settings);
            PreferenceScreen screen = getPreferenceScreen();
            mAlwaysRunInBackground =
                    (CheckBoxPreference) findPreference(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND);
            mSyncOnlyCharging =
                    (CheckBoxPreference) findPreference(Constants.PREF_SYNC_ONLY_CHARGING);
            mSyncOnlyWifi =
                    (CheckBoxPreference) findPreference(Constants.PREF_SYNC_ONLY_WIFI);
            mSyncOnlyOnSSIDs =
                    (WifiSsidPreference) findPreference(Constants.PREF_SYNC_ONLY_WIFI_SSIDS);

            mSyncOnlyCharging.setEnabled(mAlwaysRunInBackground.isChecked());
            mSyncOnlyWifi.setEnabled(mAlwaysRunInBackground.isChecked());
            mSyncOnlyOnSSIDs.setEnabled(mSyncOnlyWifi.isChecked());

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                categoryBehaviour.removePreference(findPreference(Constants.PREF_NOTIFICATION_TYPE));
            }

            mDeviceName             = (EditTextPreference) findPreference("deviceName");
            mListenAddresses        = (EditTextPreference) findPreference("listenAddresses");
            mMaxRecvKbps            = (EditTextPreference) findPreference("maxRecvKbps");
            mMaxSendKbps            = (EditTextPreference) findPreference("maxSendKbps");
            mNatEnabled             = (CheckBoxPreference) findPreference("natEnabled");
            mLocalAnnounceEnabled   = (CheckBoxPreference) findPreference("localAnnounceEnabled");
            mGlobalAnnounceEnabled  = (CheckBoxPreference) findPreference("globalAnnounceEnabled");
            mRelaysEnabled          = (CheckBoxPreference) findPreference("relaysEnabled");
            mGlobalAnnounceServers  = (EditTextPreference) findPreference("globalAnnounceServers");
            mAddress                = (EditTextPreference) findPreference("address");
            mUrAccepted             = (CheckBoxPreference) findPreference("urAccepted");

            mCategoryBackup         = findPreference("category_backup");
            Preference exportConfig = findPreference("export_config");
            Preference importConfig = findPreference("import_config");

            Preference stTrace              = findPreference("sttrace");
            Preference environmentVariables = findPreference("environment_variables");
            Preference stReset              = findPreference("streset");

            mUseRoot                     = (CheckBoxPreference) findPreference(Constants.PREF_USE_ROOT);
            Preference useWakelock       = findPreference(Constants.PREF_USE_WAKE_LOCK);
            Preference useTor            = findPreference("use_tor");

            mSyncthingVersion       = findPreference("syncthing_version");
            Preference appVersion   = screen.findPreference("app_version");

            mSyncOnlyOnSSIDs.setEnabled(mSyncOnlyWifi.isChecked());
            setPreferenceCategoryChangeListener(findPreference("category_run_conditions"), this);

            mCategorySyncthingOptions = findPreference("category_syncthing_options");
            setPreferenceCategoryChangeListener(mCategorySyncthingOptions, this::onSyncthingPreferenceChange);

            exportConfig.setOnPreferenceClickListener(this);
            importConfig.setOnPreferenceClickListener(this);

            stTrace.setOnPreferenceChangeListener(this);
            environmentVariables.setOnPreferenceChangeListener(this);
            stReset.setOnPreferenceClickListener(this);

            mUseRoot.setOnPreferenceClickListener(this);
            useWakelock.setOnPreferenceChangeListener((p, o) -> requireRestart());
            useTor.setOnPreferenceChangeListener((p, o) -> requireRestart());

            try {
                appVersion.setSummary(getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Failed to get app version name");
            }
        }

        @Override
        public void onServiceConnected() {
            if (getActivity() == null)
                return;

            mSyncthingService = ((SyncthingActivity) getActivity()).getService();
            mSyncthingService.registerOnApiChangeListener(this);
            // Use callback to make sure getApi() doesn't return null.
            mSyncthingService.registerOnWebGuiAvailableListener(() -> {
                if (mSyncthingService.getApi().isConfigLoaded()) {
                    mGui = mSyncthingService.getApi().getGui();
                    mOptions = mSyncthingService.getApi().getOptions();
                }

            });
        }

        @Override
        public void onApiChange(SyncthingService.State currentState) {
            boolean syncthingActive = currentState == SyncthingService.State.ACTIVE;
            boolean isSyncthingRunning = syncthingActive && mSyncthingService.getApi().isConfigLoaded();
            mCategorySyncthingOptions.setEnabled(isSyncthingRunning);
            mCategoryBackup.setEnabled(isSyncthingRunning);

            if (!isSyncthingRunning)
                return;

            mApi = mSyncthingService.getApi();
            mSyncthingVersion.setSummary(mApi.getVersion());
            mOptions = mApi.getOptions();
            mGui = mApi.getGui();

            Joiner joiner = Joiner.on(", ");
            mDeviceName.setText(mApi.getLocalDevice().name);
            mListenAddresses.setText(joiner.join(mOptions.listenAddresses));
            mMaxRecvKbps.setText(Integer.toString(mOptions.maxRecvKbps));
            mMaxSendKbps.setText(Integer.toString(mOptions.maxSendKbps));
            mNatEnabled.setChecked(mOptions.natEnabled);
            mLocalAnnounceEnabled.setChecked(mOptions.localAnnounceEnabled);
            mGlobalAnnounceEnabled.setChecked(mOptions.globalAnnounceEnabled);
            mRelaysEnabled.setChecked(mOptions.relaysEnabled);
            mGlobalAnnounceServers.setText(joiner.join(mOptions.globalAnnounceServers));
            mAddress.setText(mGui.address);
            mUrAccepted.setChecked(mOptions.isUsageReportingAccepted());
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mPreferences.unregisterOnSharedPreferenceChangeListener(this);
            if (mSyncthingService != null)
                mSyncthingService.unregisterOnApiChangeListener(this);
        }

        private void setPreferenceCategoryChangeListener(
                Preference category, Preference.OnPreferenceChangeListener listener) {
            PreferenceScreen ps = (PreferenceScreen) category;
            for (int i = 0; i < ps.getPreferenceCount(); i++) {
                Preference p = ps.getPreference(i);
                p.setOnPreferenceChangeListener(listener);
            }
        }

        public boolean onSyncthingPreferenceChange(Preference preference, Object o) {
            Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
            switch (preference.getKey()) {
                case "deviceName":
                    Device localDevice = mApi.getLocalDevice();
                    localDevice.name = (String) o;
                    mApi.editDevice(localDevice);
                    break;
                case "listenAddresses":
                    mOptions.listenAddresses = Iterables.toArray(splitter.split((String) o), String.class);
                    break;
                case "maxRecvKbps":           mOptions.maxRecvKbps = Integer.parseInt((String) o); break;
                case "maxSendKbps":           mOptions.maxSendKbps = Integer.parseInt((String) o); break;
                case "natEnabled":            mOptions.natEnabled = (boolean) o;                   break;
                case "localAnnounceEnabled":  mOptions.localAnnounceEnabled = (boolean) o;         break;
                case "globalAnnounceEnabled": mOptions.globalAnnounceEnabled = (boolean) o;        break;
                case "relaysEnabled":         mOptions.relaysEnabled = (boolean) o;                break;
                case "globalAnnounceServers":
                    mOptions.globalAnnounceServers = Iterables.toArray(splitter.split((String) o), String.class);
                    break;
                case "address":               mGui.address = (String) o;  break;
                case "urAccepted":
                    mOptions.urAccepted = ((boolean) o)
                            ? mOptions.urVersionMax
                            : Options.USAGE_REPORTING_DENIED;
                    break;
                default: throw new InvalidParameterException();
            }

            mApi.editSettings(mGui, mOptions, getActivity());
            return true;
        }

        public boolean requireRestart() {
            if (mSyncthingService.getCurrentState() != SyncthingService.State.DISABLED &&
                    mSyncthingService.getApi() != null) {
                mSyncthingService.getApi().showRestartDialog(getActivity());
            }
            return true;
        }

        /**
         * Sends the updated value to {@link RestApi}, and sets it as the summary
         * for EditTextPreference.
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            switch (preference.getKey()) {
                case Constants.PREF_ALWAYS_RUN_IN_BACKGROUND:
                    boolean value = (Boolean) o;
                    mAlwaysRunInBackground.setSummary((value)
                            ? R.string.always_run_in_background_enabled
                            : R.string.always_run_in_background_disabled);
                    mSyncOnlyCharging.setEnabled(value);
                    mSyncOnlyWifi.setEnabled(value);
                    mSyncOnlyOnSSIDs.setEnabled(mSyncOnlyWifi.isChecked());
                    // Uncheck items when disabled, so it is clear they have no effect.
                    if (!value) {
                        mSyncOnlyCharging.setChecked(false);
                        mSyncOnlyWifi.setChecked(false);
                    }
                    break;
                case Constants.PREF_SYNC_ONLY_WIFI:
                    mSyncOnlyOnSSIDs.setEnabled((Boolean) o);
                    break;
                case KEY_STTRACE:
                    if (((String) o).matches("[0-9a-z, ]*"))
                        requireRestart();
                    else {
                        Toast.makeText(getActivity(), R.string.toast_invalid_sttrace, Toast.LENGTH_SHORT)
                                .show();
                        return false;
                    }
                    break;
                case "environment_variables":
                    if (((String) o).matches("^(\\w+=[\\w:/\\.]+)?( \\w+=[\\w:/\\.]+)*$")) {
                        requireRestart();
                    }
                    else {
                        Toast.makeText(getActivity(), R.string.toast_invalid_environment_variables, Toast.LENGTH_SHORT)
                                .show();
                        return false;
                    }
                    break;
            }

            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case Constants.PREF_USE_ROOT:
                    if (mUseRoot.isChecked()) {
                        // Only check preference after root was granted.
                        mUseRoot.setChecked(false);
                        new TestRootTask().execute();
                    } else {
                        new Thread(() -> Util.fixAppDataPermissions(getActivity())).start();
                        requireRestart();
                    }
                    return true;
                case KEY_EXPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_export)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                        mSyncthingService.exportConfig();
                                        Toast.makeText(getActivity(),
                                                getString(R.string.config_export_successful,
                                                Constants.EXPORT_PATH), Toast.LENGTH_LONG).show();
                                    })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_IMPORT_CONFIG:
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_confirm_import)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                        if (mSyncthingService.importConfig()) {
                                            Toast.makeText(getActivity(),
                                                    getString(R.string.config_imported_successful),
                                                    Toast.LENGTH_SHORT).show();
                                            // No need to restart, as we shutdown to import the config, and
                                            // then have to start Syncthing again.
                                        } else {
                                            Toast.makeText(getActivity(),
                                                    getString(R.string.config_import_failed,
                                                    Constants.EXPORT_PATH), Toast.LENGTH_LONG).show();
                                        }
                                    })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                case KEY_STRESET:
                    final Intent intent = new Intent(getActivity(), SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESET);

                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.streset_title)
                            .setMessage(R.string.streset_question)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                getActivity().startService(intent);
                                Toast.makeText(getActivity(), R.string.streset_done, Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
                            })
                            .show();
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Update notification after that preference changes. We can't use onPreferenceChange() as
         * the preference value isn't persisted there, and the NotificationHandler accesses the
         * preference directly.
         *
         * This function is called when the activity is opened, so we need to make sure the service
         * is connected.
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(Constants.PREF_NOTIFICATION_TYPE) && mSyncthingService != null) {
                mNotificationHandler.updatePersistentNotification(mSyncthingService);
            }
        }

        /**
         * Enables or disables {@link #mUseRoot} preference depending whether root is available.
         */
        private class TestRootTask extends AsyncTask<Void, Void, Boolean> {
            @Override
            protected Boolean doInBackground(Void... params) {
                return Shell.SU.available();
            }

            @Override
            protected void onPostExecute(Boolean haveRoot) {
                if (haveRoot) {
                    requireRestart();
                    mUseRoot.setChecked(true);
                } else {
                    Toast.makeText(getActivity(), R.string.toast_root_denied, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }
}
