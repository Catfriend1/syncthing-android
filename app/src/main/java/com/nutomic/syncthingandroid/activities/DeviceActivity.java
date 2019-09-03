package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DiscoveredDevice;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.service.TestData;
import com.nutomic.syncthingandroid.util.Compression;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static android.text.TextUtils.isEmpty;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginEnd;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginStart;
import static com.nutomic.syncthingandroid.service.Constants.ENABLE_TEST_DATA;
import static com.nutomic.syncthingandroid.util.Compression.METADATA;

/**
 * Shows device details and allows changing them.
 */
public class DeviceActivity extends SyncthingActivity {

    public static final String EXTRA_NOTIFICATION_ID =
            "com.github.catfriend1.syncthingandroid.activities.DeviceActivity.NOTIFICATION_ID";
    public static final String EXTRA_DEVICE_ID =
            "com.github.catfriend1.syncthingandroid.activities.DeviceActivity.DEVICE_ID";
    public static final String EXTRA_DEVICE_NAME =
            "com.github.catfriend1.syncthingandroid.activities.DeviceActivity.DEVICE_NAME";
    public static final String EXTRA_IS_CREATE =
            "com.github.catfriend1.syncthingandroid.activities.DeviceActivity.IS_CREATE";

    private static final String TAG = "DeviceActivity";
    private static final String IS_SHOWING_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";
    private static final String IS_SHOWING_COMPRESSION_DIALOG = "COMPRESSION_FOLDER_DIALOG_STATE";
    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";

    private static final List<String> DYNAMIC_ADDRESS = Collections.singletonList("dynamic");

    public static final int DEVICE_ADD_CODE = 401;

    private ConfigRouter mConfig;

    private Device mDevice;
    private EditText mEditDeviceId;
    private TextView mDiscoveredDevicesTitle;
    private ViewGroup mDiscoveredDevicesContainer;
    private View mShowDeviceIdContainer;
    private EditText mShowDeviceId;
    private View mQrButton;
    private EditText mNameView;
    private EditText mAddressesView;
    private TextView mCurrentAddressView;
    private View mCompressionContainer;
    private TextView mCompressionValueView;
    private SwitchCompat mIntroducerView;
    private SwitchCompat mDevicePaused;
    private SwitchCompat mCustomSyncConditionsSwitch;
    private TextView mCustomSyncConditionsDescription;
    private TextView mCustomSyncConditionsDialog;
    private TextView mSyncthingVersionView;

    @Inject
    SharedPreferences mPreferences;

    private boolean mIsCreateMode;

    private boolean mDeviceNeedsToUpdate;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;
    private Dialog mCompressionDialog;

    private final DialogInterface.OnClickListener mCompressionEntrySelectedListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            Compression compression = Compression.fromIndex(which);
            // Don't pop the restart dialog unless the value is actually different.
            if (compression != Compression.fromValue(DeviceActivity.this, mDevice.compression)) {
                mDeviceNeedsToUpdate = true;

                mDevice.compression = compression.getValue(DeviceActivity.this);
                mCompressionValueView.setText(compression.getTitle(DeviceActivity.this));
            }
        }
    };

    private final TextWatcher mIdTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(mDevice.deviceID)) {
                mDeviceNeedsToUpdate = true;
                mDevice.deviceID = s.toString();
            }
        }
    };

    private final TextWatcher mNameTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(mDevice.name)) {
                mDeviceNeedsToUpdate = true;
                mDevice.name = s.toString();
            }
        }
    };

    private final TextWatcher mAddressesTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(displayableAddresses())) {
                mDeviceNeedsToUpdate = true;
                mDevice.addresses = persistableAddresses(s);
            }
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.introducer:
                    mDevice.introducer = isChecked;
                    mDeviceNeedsToUpdate = true;
                    break;
                case R.id.devicePause:
                    mDevice.paused = isChecked;
                    mDeviceNeedsToUpdate = true;
                    break;
                case R.id.customSyncConditionsSwitch:
                    mCustomSyncConditionsDescription.setEnabled(isChecked);
                    mCustomSyncConditionsDialog.setFocusable(isChecked);
                    mCustomSyncConditionsDialog.setEnabled(isChecked);
                    // This is needed to display the "discard changes dialog".
                    mDeviceNeedsToUpdate = true;
                    break;
            }
        }
    };

    public static Intent createIntent(Context context) {
        Intent intent = new Intent(context, DeviceActivity.class);
        intent.putExtra(EXTRA_IS_CREATE, true);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mConfig = new ConfigRouter(DeviceActivity.this);

        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        setContentView(R.layout.activity_device);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.add_device : R.string.edit_device);

        mEditDeviceId = findViewById(R.id.editDeviceId);
        mDiscoveredDevicesTitle = findViewById(R.id.discoveredDevicesTitle);
        mDiscoveredDevicesContainer = findViewById(R.id.discoveredDevicesContainer);
        mShowDeviceIdContainer = findViewById(R.id.showDeviceIdContainer);
        mShowDeviceId = findViewById(R.id.showDeviceId);
        mQrButton = findViewById(R.id.qrButton);
        mNameView = findViewById(R.id.name);
        mAddressesView = findViewById(R.id.addresses);
        mCurrentAddressView = findViewById(R.id.currentAddress);
        mCompressionContainer = findViewById(R.id.compressionContainer);
        mCompressionValueView = findViewById(R.id.compressionValue);
        mIntroducerView = findViewById(R.id.introducer);
        mDevicePaused = findViewById(R.id.devicePause);
        mCustomSyncConditionsSwitch = findViewById(R.id.customSyncConditionsSwitch);
        mCustomSyncConditionsDescription = findViewById(R.id.customSyncConditionsDescription);
        mCustomSyncConditionsDialog = findViewById(R.id.customSyncConditionsDialog);
        mSyncthingVersionView = findViewById(R.id.syncthingVersion);

        if (Util.isRunningOnTV(this)) {
            mQrButton.setVisibility(View.GONE);
        }
        mQrButton.setOnClickListener(view -> onQrButtonClick());
        mShowDeviceIdContainer.setOnClickListener(view -> onCopyDeviceIdClick());
        mCompressionContainer.setOnClickListener(view -> onCompressionContainerClick());
        mCustomSyncConditionsDialog.setOnClickListener(view -> onCustomSyncConditionsDialogClick());

        findViewById(R.id.editDeviceIdContainer).setVisibility(mIsCreateMode ? View.VISIBLE : View.GONE);
        mShowDeviceIdContainer.setVisibility(!mIsCreateMode ? View.VISIBLE : View.GONE);

        if (savedInstanceState != null) {
            Log.d(TAG, "Retrieving state from savedInstanceState ...");
            mDevice = new Gson().fromJson(savedInstanceState.getString("device"), Device.class);
            mDeviceNeedsToUpdate = savedInstanceState.getBoolean("deviceNeedsToUpdate");
            restoreDialogStates(savedInstanceState);
        } else {
            // Fresh init of the edit or create mode.
            if (mIsCreateMode) {
                Log.d(TAG, "Initializing create mode ...");
                initDevice();
                mDeviceNeedsToUpdate = true;
            } else {
                // Edit mode.
                String passedId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
                Log.d(TAG, "Initializing edit mode: deviceID=" + passedId);
                // getApi() is unavailable (onCreate > onPostCreate > onServiceConnected)
                List<Device> devices = mConfig.getDevices(null, false);
                mDevice = null;
                for (Device currentDevice : devices) {
                    if (currentDevice.deviceID.equals(passedId)) {
                        mDevice = currentDevice;
                        break;
                    }
                }
                if (mDevice == null) {
                    Log.w(TAG, "Device not found in API update, maybe it was deleted?");
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                mDeviceNeedsToUpdate = false;
            }
        }
        updateViewsAndSetListeners();

        if (mIsCreateMode) {
            mEditDeviceId.requestFocus();
        } else {
            getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            mNameView.requestFocus();
        }

        // Show expert options conditionally.
        Boolean prefExpertMode = mPreferences.getBoolean(Constants.PREF_EXPERT_MODE, false);
        mCompressionContainer.setVisibility(prefExpertMode ? View.VISIBLE : View.GONE);
    }

    private void restoreDialogStates(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(IS_SHOWING_COMPRESSION_DIALOG)){
            showCompressionDialog();
        }

        if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
            showDeleteDialog();
        }

        if (savedInstanceState.getBoolean(IS_SHOWING_DISCARD_DIALOG)){
            showDiscardDialog();
        }
    }

    /**
     * Register for service state change events.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = (SyncthingService) syncthingServiceBinder.getService();
        syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        RestApi restApi = syncthingService.getApi();
        if (restApi != null) {
            restApi.getConnections(this::onReceiveConnections);
            if (mIsCreateMode) {
                mDiscoveredDevicesTitle.setOnClickListener(view -> {
                    if (restApi != null) {
                        asyncQueryDiscoveredDevices(restApi);
                    }
                });
                asyncQueryDiscoveredDevices(restApi);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mDeviceNeedsToUpdate) {
            showDiscardDialog();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService syncthingService = getService();
        if (syncthingService != null) {
            syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        }
        mEditDeviceId.removeTextChangedListener(mIdTextWatcher);
        mNameView.removeTextChangedListener(mNameTextWatcher);
        mAddressesView.removeTextChangedListener(mAddressesTextWatcher);
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("device", new Gson().toJson(mDevice));
        outState.putBoolean("deviceNeedsToUpdate", mDeviceNeedsToUpdate);

        outState.putBoolean(IS_SHOWING_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
        Util.dismissDialogSafe(mDiscardDialog, this);

        outState.putBoolean(IS_SHOWING_COMPRESSION_DIALOG, mCompressionDialog != null && mCompressionDialog.isShowing());
        Util.dismissDialogSafe(mCompressionDialog, this);

        outState.putBoolean(IS_SHOWING_DELETE_DIALOG, mDeleteDialog != null && mDeleteDialog.isShowing());
        Util.dismissDialogSafe(mDeleteDialog, this);
    }

    /**
     * Sets version and current address of the device.
     * NOTE: This is only called once on startup, should be called more often to properly display
     * version/address changes.
     */
    private void onReceiveConnections(Connections connections) {
        if (connections == null || connections.connections == null) {
            Log.e(TAG, "onReceiveConnections: connections == null || connections.connections == null");
            return;
        }
        if (mDevice == null) {
            Log.e(TAG, "onReceiveConnections: mDevice == null");
            return;
        }

        boolean viewsExist = mSyncthingVersionView != null && mCurrentAddressView != null;
        if (viewsExist && connections.connections.containsKey(mDevice.deviceID)) {
            mCurrentAddressView.setVisibility(VISIBLE);
            mSyncthingVersionView.setVisibility(VISIBLE);
            mCurrentAddressView.setText(connections.connections.get(mDevice.deviceID).address);
            mSyncthingVersionView.setText(connections.connections.get(mDevice.deviceID).clientVersion);
        }
    }

    private void updateViewsAndSetListeners() {
        mEditDeviceId.removeTextChangedListener(mIdTextWatcher);
        mNameView.removeTextChangedListener(mNameTextWatcher);
        mAddressesView.removeTextChangedListener(mAddressesTextWatcher);
        mIntroducerView.setOnCheckedChangeListener(null);
        mDevicePaused.setOnCheckedChangeListener(null);
        mCustomSyncConditionsSwitch.setOnCheckedChangeListener(null);

        // Update views
        mEditDeviceId.setText(mDevice.deviceID);
        mShowDeviceId.setText(mDevice.deviceID);
        mNameView.setText(mDevice.name);
        mAddressesView.setText(displayableAddresses());
        mCompressionValueView.setText(Compression.fromValue(this, mDevice.compression).getTitle(this));
        mIntroducerView.setChecked(mDevice.introducer);
        mDevicePaused.setChecked(mDevice.paused);

        // Update views - custom sync conditions.
        mCustomSyncConditionsSwitch.setChecked(false);
        if (mIsCreateMode) {
            findViewById(R.id.customSyncConditionsContainer).setVisibility(View.GONE);
        } else {
            mCustomSyncConditionsSwitch.setChecked(mPreferences.getBoolean(
                Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + mDevice.deviceID), false
            ));
        }
        mCustomSyncConditionsSwitch.setEnabled(!mIsCreateMode);
        mCustomSyncConditionsDescription.setEnabled(mCustomSyncConditionsSwitch.isChecked());
        mCustomSyncConditionsDialog.setFocusable(mCustomSyncConditionsSwitch.isChecked());
        mCustomSyncConditionsDialog.setEnabled(mCustomSyncConditionsSwitch.isChecked());

        // Keep state updated
        mEditDeviceId.addTextChangedListener(mIdTextWatcher);
        mNameView.addTextChangedListener(mNameTextWatcher);
        mAddressesView.addTextChangedListener(mAddressesTextWatcher);
        mIntroducerView.setOnCheckedChangeListener(mCheckedListener);
        mDevicePaused.setOnCheckedChangeListener(mCheckedListener);
        mCustomSyncConditionsSwitch.setOnCheckedChangeListener(mCheckedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.save).setTitle(mIsCreateMode ? R.string.create : R.string.save_title);
        menu.findItem(R.id.share_device_id).setVisible(!mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                onSave();
                return true;
            case R.id.share_device_id:
                shareDeviceId(this, mDevice.deviceID);
                return true;
            case R.id.remove:
               showDeleteDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void showDeleteDialog(){
        mDeleteDialog = createDeleteDialog();
        mDeleteDialog.show();
    }

    private Dialog createDeleteDialog(){
        return new AlertDialog.Builder(this)
                .setMessage(R.string.remove_device_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    mConfig.removeDevice(getApi(), mDevice.deviceID);
                    mDeviceNeedsToUpdate = false;
                    setResult(AppCompatActivity.RESULT_OK);
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    /**
     * Receives value of scanned QR code and sets it as device ID.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            mDevice.deviceID = scanResult.getContents();
            mEditDeviceId.setText(mDevice.deviceID);
            if (ENABLE_TEST_DATA) {
                mEditDeviceId.setText(TestData.DEVICE_A_ID);
            }
        }
    }

    /**
     * Used in mIsCreateMode.
     */
    private void initDevice() {
        mDevice = new Device();
        mDevice.name = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        mDevice.deviceID = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        mDevice.addresses = DYNAMIC_ADDRESS;
        mDevice.compression = METADATA.getValue(this);
        mDevice.introducer = false;
        mDevice.paused = false;
        mDevice.introducedBy = "";
    }

    private void onSave() {
        if (mDevice == null) {
            Log.e(TAG, "onSave: mDevice == null");
            return;
        }

        // Validate fields.
        if (isEmpty(mDevice.deviceID)) {
            Toast.makeText(this, R.string.device_id_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!mDevice.checkDeviceID()) {
            Toast.makeText(this, R.string.device_id_invalid, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (isEmpty(mDevice.name)) {
            Toast.makeText(this, R.string.device_name_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!mDevice.checkDeviceAddresses()) {
            Toast.makeText(this, R.string.device_addresses_invalid, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        if (mIsCreateMode) {
            Log.v(TAG, "onSave: Adding device with ID = \'" + mDevice.deviceID + "\'");
            mConfig.addDevice(getApi(), mDevice);
            setResult(AppCompatActivity.RESULT_OK);
            finish();
            return;
        }

        // Edit mode.
        if (!mDeviceNeedsToUpdate) {
            // We've got nothing to save.
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
            return;
        }
        // Log.v(TAG, "deviceID=" + mDevice.deviceID + ", introducedBy=" + mDevice.introducedBy);

        // Save device specific preferences.
        Log.v(TAG, "onSave: Updating device with ID = \'" + mDevice.deviceID + "\'");
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(
            Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + mDevice.deviceID),
            mCustomSyncConditionsSwitch.isChecked()
        );
        editor.apply();

        // Update device using RestApi or ConfigXml.
        mConfig.updateDevice(getApi(), mDevice);
        setResult(AppCompatActivity.RESULT_OK);
        finish();
        return;
    }

    /**
     * Converts text line to addresses array.
     */
    private List<String> persistableAddresses(CharSequence userInput) {
        if (isEmpty(userInput)) {
            return DYNAMIC_ADDRESS;
        }

        /**
         * Be fault-tolerant here.
         * The user can write like this:
         * tcp4://192.168.1.67:2222, dynamic
         * tcp4://192.168.1.67:2222; dynamic
         * tcp4://192.168.1.67:2222,dynamic
         * tcp4://192.168.1.67:2222;dynamic
         * tcp4://192.168.1.67:2222 dynamic
         */
        String input = userInput.toString();
        input = input.replace(",", " ");
        input = input.replace(";", " ");
        input = input.replaceAll("\\s+", ", ");
        // Log.v(TAG, "persistableAddresses: Cleaned user input=" + input);

        // Split and return the addresses as String[].
        return Arrays.asList(input.split(", "));
    }

    /**
     * Converts addresses array to a text line.
     */
    private String displayableAddresses() {
        if (mDevice.addresses == null) {
            return "";
        }
        List<String> list = DYNAMIC_ADDRESS.equals(mDevice.addresses)
                ? DYNAMIC_ADDRESS
                : mDevice.addresses;
        return TextUtils.join(", ", list);
    }

    private void onCompressionContainerClick() {
        showCompressionDialog();
    }

    /**
     * Invoked after user clicked on the {@link #mCustomSyncConditionsDialog} label.
     */
    private void onCustomSyncConditionsDialogClick() {
        startActivityForResult(
            SyncConditionsActivity.createIntent(
                this, Constants.PREF_OBJECT_PREFIX_DEVICE + mDevice.deviceID, mDevice.name
            ),
            0
        );
    }

    private void onCopyDeviceIdClick() {
        Util.copyDeviceId(this, mDevice.deviceID);
    }

    private void onQrButtonClick() {
        final List<String> targetApplications = list(
            "de.markusfisch.android.binaryeye",                 // Binary Eye
            "de.t_dankworth.secscanqr",                         // SecScanQR
            "com.srowen.bs.android",                            // Barcode Scanner+
            "com.srowen.bs.android.simple"                      // Barcode Scanner+ Simple
            // "com.google.zxing.client.android"                // Barcode Scanner (2019-02-24: no longer on GPlay)
        );
        IntentIntegrator integrator = new IntentIntegrator(DeviceActivity.this);
        integrator.setTargetApplications(targetApplications);
        integrator.setMessage(getString(R.string.install_barcode_scanner_app_message));
        integrator.initiateScan();
    }

    private void showCompressionDialog(){
        mCompressionDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.compression)
                .setSingleChoiceItems(R.array.compress_entries,
                        Compression.fromValue(this, mDevice.compression).getIndex(),
                        mCompressionEntrySelectedListener)
                .create();
        mCompressionDialog.show();
    }

    /**
     * Shares the given device ID via Intent. Must be called from an Activity.
     */
    private void shareDeviceId(Context context, String id) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, id);
        context.startActivity(Intent.createChooser(
                shareIntent, context.getString(R.string.send_device_id_to)));
    }

    private void showDiscardDialog(){
        mDiscardDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_discard_changes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        setResult(AppCompatActivity.RESULT_CANCELED);
                        finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mDiscardDialog.show();
    }

    /**
     * Perform asynchronous query via REST to retrieve locally discovered devices.
     * Precondition:
     *      restApi != null
     *      mIsCreateMode == true
     */
    private void asyncQueryDiscoveredDevices(RestApi restApi) {
        if (!restApi.isConfigLoaded()) {
            return;
        }
        restApi.getDiscoveredDevices(this::onReceiveDiscoveredDevices);
    }

    /**
     * Callback after {@link asyncQueryDiscoveredDevices}.
     * Precondition:
     *      mIsCreateMode == true
     */
    private void onReceiveDiscoveredDevices(Map<String, DiscoveredDevice> discoveredDevices) {
        if (discoveredDevices == null) {
            Log.e(TAG, "onReceiveDiscoveredDevices: discoveredDevices == null");
            return;
        }

        /**
         * If "mEditDeviceId" already contains content, don't show local discovery results.
         * This also suppresses the results being shown a second time after the user chose a
         * deviceId from the list and rotated the screen.
         */
        mDiscoveredDevicesTitle.setVisibility(TextUtils.isEmpty(mEditDeviceId.getText()) ? View.VISIBLE : View.GONE);
        mDiscoveredDevicesContainer.setVisibility(TextUtils.isEmpty(mEditDeviceId.getText()) ? View.VISIBLE : View.GONE);

        mDiscoveredDevicesContainer.removeAllViews();
        if (discoveredDevices.size() == 0) {
            // No discovered devices. Determine if local discovery is enabled.
            Options options = mConfig.getOptions(null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
            int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
            setMarginStart(params, dividerInset);
            setMarginEnd(params, contentInset);
            TextView emptyView = new TextView(mDiscoveredDevicesContainer.getContext());
            emptyView.setGravity(CENTER_VERTICAL);
            if (options.localAnnounceEnabled) {
                emptyView.setText(getString(R.string.discovered_device_list_empty, getString(R.string.url_syncthing_homepage)));
            } else {
                emptyView.setText(R.string.local_discovery_disabled);
            }
            mDiscoveredDevicesContainer.addView(emptyView, params);
            return;
        }

        for (String deviceId : discoveredDevices.keySet()) {
            if (deviceId != null) {
                // Get device address.
                String readableAddresses = "";
                DiscoveredDevice discoveredDevice = discoveredDevices.get(deviceId);
                if (discoveredDevice != null && discoveredDevice.addresses != null) {
                    readableAddresses = TextUtils.join(", ", discoveredDevice.addresses);
                }
                // Log.v(TAG, "onReceiveDiscoveredDevices: deviceID = '" + deviceId + "' has addresses '" + readableAddresses + "'");
                String caption = deviceId + (TextUtils.isEmpty(readableAddresses) ? "" : " (" + readableAddresses + ")");
                LayoutInflater inflater = getLayoutInflater();
                inflater.inflate(R.layout.item_discovered_device_form, mDiscoveredDevicesContainer);
                TextView deviceIdView = (TextView) mDiscoveredDevicesContainer.getChildAt(mDiscoveredDevicesContainer.getChildCount()-1);
                deviceIdView.setOnClickListener(null);
                deviceIdView.setText(caption);
                deviceIdView.setTag(deviceId);
                deviceIdView.setOnClickListener(v -> onDeviceIdViewClick(v));
            }
        }
    }

    /**
     * Copies the deviceId from TextView to "device_id" EditText.
     * Hides the "mDiscoveredDevicesContainer" view afterwards.
     */
    private void onDeviceIdViewClick(View view) {
        mEditDeviceId.setText((String) view.getTag());
        mDiscoveredDevicesTitle.setVisibility(View.GONE);
        mDiscoveredDevicesContainer.setVisibility(View.GONE);
    }

    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
