package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import static androidx.core.view.MarginLayoutParamsCompat.setMarginEnd;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginStart;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Activity that allows selecting a directory in the local file system.
 */
public class SyncConditionsActivity extends SyncthingActivity {

    private static final String TAG = "SyncConditionsActivity";

    private static final String EXTRA_OBJECT_PREFIX_AND_ID =
            ".activities.SyncConditionsActivity.OBJECT_PREFIX_AND_ID";

    private static final String EXTRA_OBJECT_READABLE_NAME =
            ".activities.SyncConditionsActivity.OBJECT_READABLE_NAME";

    // UI elements
    private SwitchCompat mSyncOnWifi;
    private SwitchCompat mSyncOnWhitelistedWifi;
    private ViewGroup mWifiSsidContainer;
    private SwitchCompat mSyncOnMeteredWifi;
    private SwitchCompat mSyncOnMobileData;
    private AppCompatSpinner mSyncOnPowerSource;

    /**
     * Shared preferences contents for global conditions.
     */
    private Boolean mGlobalWhitelistEnabled;

    /**
     * Shared preferences names for custom object settings.
     * Object can e.g. be a folder or device.
     */
    private String mObjectPrefixAndId;
    private String mPrefSyncOnWifi;
    private String mPrefSyncOnWhitelistedWifi;
    private String mPrefSelectedWhitelistSsid;
    private String mPrefSyncOnMeteredWifi;
    private String mPrefSyncOnMobileData;
    private String mPrefSyncOnPowerSource;

    // UI information and state.
    private String mObjectReadableName;
    private boolean mUnsavedChanges = false;

    @Inject
    SharedPreferences mPreferences;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            setResult(Activity.RESULT_OK);
            finish();
        }
    };

    public static Intent createIntent(Context context, String objectPrefixAndId, String objectReadableName) {
        Intent intent = new Intent(context, SyncConditionsActivity.class);
        intent.putExtra(EXTRA_OBJECT_PREFIX_AND_ID, objectPrefixAndId);
        intent.putExtra(EXTRA_OBJECT_READABLE_NAME, objectReadableName);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_OBJECT_PREFIX_AND_ID) ||
                !intent.hasExtra(EXTRA_OBJECT_READABLE_NAME)) {
            Log.e(TAG, "onCreate extra missing");
            return;
        }
        mObjectReadableName = intent.getStringExtra(EXTRA_OBJECT_READABLE_NAME);

        // Display content and get views.
        setContentView(R.layout.activity_sync_conditions);
        mSyncOnWifi = findViewById(R.id.sync_on_wifi_title);
        mSyncOnWhitelistedWifi = findViewById(R.id.sync_on_whitelisted_wifi_title);
        mWifiSsidContainer = findViewById(R.id.wifiSsidContainer);
        mSyncOnMeteredWifi = findViewById(R.id.sync_on_metered_wifi_title);
        mSyncOnMobileData = findViewById(R.id.sync_on_mobile_data_title);
        mSyncOnPowerSource = findViewById(R.id.sync_on_power_source_title);

        // Generate shared preferences names.
        mObjectPrefixAndId = intent.getStringExtra(EXTRA_OBJECT_PREFIX_AND_ID);
        Log.v(TAG, "Prefix is \'" + mObjectPrefixAndId + "\' (" + mObjectReadableName + ")");
        mPrefSyncOnWifi = Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(mObjectPrefixAndId);
        mPrefSyncOnWhitelistedWifi = Constants.DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(mObjectPrefixAndId);
        mPrefSelectedWhitelistSsid = Constants.DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(mObjectPrefixAndId);
        mPrefSyncOnMeteredWifi = Constants.DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(mObjectPrefixAndId);
        mPrefSyncOnMobileData = Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(mObjectPrefixAndId);
        mPrefSyncOnPowerSource = Constants.DYN_PREF_OBJECT_SYNC_ON_POWER_SOURCE(mObjectPrefixAndId);

        /**
         * Load global run conditions.
         */
        Boolean globalRunOnWifiEnabled = mPreferences.getBoolean(Constants.PREF_RUN_ON_WIFI, true);
        Set<String> globalWhitelistedSsid = mPreferences.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>());
        mGlobalWhitelistEnabled = mPreferences.getBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, false);
        Boolean globalRunOnMeteredWifiEnabled = mPreferences.getBoolean(Constants.PREF_RUN_ON_METERED_WIFI, false);
        Boolean globalRunOnMobileDataEnabled = mPreferences.getBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, false);
        String globalRunOnPowerSource = mPreferences.getString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER_BATTERY);

        /**
         * Load custom object preferences. If unset, use global setting as default.
         */
        mSyncOnWifi.setChecked(globalRunOnWifiEnabled && mPreferences.getBoolean(mPrefSyncOnWifi, globalRunOnWifiEnabled));
        mSyncOnWifi.setEnabled(globalRunOnWifiEnabled);
        mSyncOnWifi.setOnCheckedChangeListener(mCheckedListener);

        mSyncOnWhitelistedWifi.setChecked(mGlobalWhitelistEnabled && mPreferences.getBoolean(mPrefSyncOnWhitelistedWifi, mGlobalWhitelistEnabled));
        mSyncOnWhitelistedWifi.setEnabled(mGlobalWhitelistEnabled && mSyncOnWifi.isChecked());
        mSyncOnWhitelistedWifi.setOnCheckedChangeListener(mCheckedListener);

        mSyncOnMeteredWifi.setChecked(globalRunOnMeteredWifiEnabled && mPreferences.getBoolean(mPrefSyncOnMeteredWifi, globalRunOnMeteredWifiEnabled));
        mSyncOnMeteredWifi.setEnabled(globalRunOnMeteredWifiEnabled);
        mSyncOnMeteredWifi.setOnCheckedChangeListener(mCheckedListener);

        mSyncOnMobileData.setChecked(globalRunOnMobileDataEnabled && mPreferences.getBoolean(mPrefSyncOnMobileData, globalRunOnMobileDataEnabled));
        mSyncOnMobileData.setEnabled(globalRunOnMobileDataEnabled);
        mSyncOnMobileData.setOnCheckedChangeListener(mCheckedListener);

        Boolean globalRunOnAnyPowerSource = globalRunOnPowerSource.equals(Constants.PowerSource.CHARGER_BATTERY);
        mSyncOnPowerSource.setSelection(Arrays.asList(getResources().getStringArray(R.array.power_source_values))
                .indexOf(globalRunOnAnyPowerSource ? mPreferences.getString(mPrefSyncOnPowerSource,Constants.PowerSource.CHARGER_BATTERY) : globalRunOnPowerSource));
        mSyncOnPowerSource.setEnabled(globalRunOnAnyPowerSource);

        // Read selected WiFi Ssid whitelist items.
        Set<String> selectedWhitelistedSsid = new HashSet<String>(mPreferences.getStringSet(mPrefSelectedWhitelistSsid, globalWhitelistedSsid));
        // Removes any network that is no longer part of the global WiFi Ssid whitelist.
        selectedWhitelistedSsid.retainAll(globalWhitelistedSsid);

        // Populate WiFi Ssid whitelist.
        mWifiSsidContainer.removeAllViews();
        // from JavaDoc: Note that you must not modify the set instance returned by this call.
        // therefore required to make a defensive copy of the elements
        globalWhitelistedSsid = new HashSet<>(globalWhitelistedSsid);
        if (!mGlobalWhitelistEnabled) {
            // Add empty WiFi Ssid ListView.
            int height = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, height);
            int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
            int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
            setMarginStart(params, dividerInset);
            setMarginEnd(params, contentInset);
            TextView emptyView = new TextView(mWifiSsidContainer.getContext());
            emptyView.setGravity(CENTER_VERTICAL);
            emptyView.setText(R.string.custom_wifi_ssid_whitelist_empty);
            emptyView.setEnabled(false);
            mWifiSsidContainer.addView(emptyView, params);
            mWifiSsidContainer.setEnabled(false);
        } else {
            for (String wifiSsid : globalWhitelistedSsid) {
                // Strip quotes and add WiFi Ssid to view.
                LayoutInflater layoutInflater = getLayoutInflater();
                layoutInflater.inflate(R.layout.item_wifi_ssid_form, mWifiSsidContainer);
                SwitchCompat wifiSsidView = (SwitchCompat) mWifiSsidContainer.getChildAt(mWifiSsidContainer.getChildCount()-1);
                wifiSsidView.setOnCheckedChangeListener(null);
                wifiSsidView.setChecked(selectedWhitelistedSsid.contains(wifiSsid));
                wifiSsidView.setEnabled(mSyncOnWifi.isChecked() && mSyncOnWhitelistedWifi.isChecked());
                wifiSsidView.setText(wifiSsid.replaceFirst("^\"", "").replaceFirst("\"$", ""));
                wifiSsidView.setTag(wifiSsid);
                wifiSsidView.setOnCheckedChangeListener(mCheckedListener);
            }
        }

        /**
         * We should always save until we abandoned the global sync conditions
         * as changes to the global run conditions resulting in force-disabling
         * the switches here would else not be saved back to the prefs.
         */
        mUnsavedChanges = true;

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            int id = view.getId();
            if (id == R.id.sync_on_wifi_title) {
                mSyncOnWhitelistedWifi.setEnabled(mGlobalWhitelistEnabled && isChecked);
                // Fall-through to dependent options.

                // Enable or disable WiFi Ssid switches according to parent switch.
                for (int i = 0; i < mWifiSsidContainer.getChildCount(); i++) {
                    mWifiSsidContainer.getChildAt(i).setEnabled(mGlobalWhitelistEnabled && isChecked);
                }
            } else if (id == R.id.sync_on_whitelisted_wifi_title) {// Enable or disable WiFi Ssid switches according to parent switch.
                for (int i = 0; i < mWifiSsidContainer.getChildCount(); i++) {
                    mWifiSsidContainer.getChildAt(i).setEnabled(mGlobalWhitelistEnabled && isChecked);
                }
            }
            mUnsavedChanges = true;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mUnsavedChanges) {
            Log.v(TAG, "onPause: mUnsavedChanges == true. Saving prefs ...");
            /**
             * Save custom object preferences.
             */
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putBoolean(mPrefSyncOnWifi, mSyncOnWifi.isChecked());
            editor.putBoolean(mPrefSyncOnWhitelistedWifi, mSyncOnWhitelistedWifi.isChecked());
            editor.putBoolean(mPrefSyncOnMeteredWifi, mSyncOnMeteredWifi.isChecked());
            editor.putBoolean(mPrefSyncOnMobileData, mSyncOnMobileData.isChecked());
            editor.putString(mPrefSyncOnPowerSource, getResources().getStringArray(R.array.power_source_values)[mSyncOnPowerSource.getSelectedItemPosition()]);

            // Save Selected WiFi Ssid's to mPrefSelectedWhitelistSsid.
            Set<String> selectedWhitelistedSsid = new HashSet<>();
            if (mSyncOnWhitelistedWifi.isChecked()) {
                for (int i = 0; i < mWifiSsidContainer.getChildCount(); i++) {
                    View view = mWifiSsidContainer.getChildAt(i);
                    if (view instanceof SwitchCompat) {
                        SwitchCompat wifiSsidSwitch = (SwitchCompat) view;
                        if (wifiSsidSwitch.isChecked()) {
                            selectedWhitelistedSsid.add((String) wifiSsidSwitch.getTag());
                            // Log.v(TAG, "onPause: +Ssid [" + (String) wifiSsidSwitch.getTag() + "]");
                        }
                    }
                }
            }
            editor.putStringSet(mPrefSelectedWhitelistSsid, selectedWhitelistedSsid);
            editor.apply();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mBackPressedCallback.handleOnBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
