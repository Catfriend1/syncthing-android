package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static android.support.v4.view.MarginLayoutParamsCompat.setMarginEnd;
import static android.support.v4.view.MarginLayoutParamsCompat.setMarginStart;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.nutomic.syncthingandroid.service.SyncthingService.State.ACTIVE;

/**
 * Shows folder details and allows changing them.
 */
public class FolderActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnServiceStateChangeListener {

    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.IS_CREATE";
    public static final String EXTRA_FOLDER_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_ID";
    public static final String EXTRA_FOLDER_LABEL =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_LABEL";
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.DEVICE_ID";

    private static final String TAG = "EditFolderFragment";

    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";
    private static final String IS_SHOW_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";

    private static final int FILE_VERSIONING_DIALOG_REQUEST = 3454;

    private static final String IGNORE_FILE_NAME = ".stignore";

    private Folder mFolder;

    private EditText mLabelView;
    private EditText mIdView;
    private TextView mPathView;
    private SwitchCompat mFolderMasterView;
    private SwitchCompat mFolderFileWatcher;
    private SwitchCompat mFolderPaused;
    private ViewGroup mDevicesContainer;
    private TextView mVersioningDescriptionView;
    private TextView mVersioningTypeView;
    private TextView mEditIgnores;

    private boolean mIsCreateMode;
    private boolean mFolderNeedsToUpdate = false;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;

    private Folder.Versioning mVersioning;

    private final TextWatcher mTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.label        = mLabelView.getText().toString();
            mFolder.id           = mIdView.getText().toString();
            mFolder.path         = mPathView.getText().toString();
            mFolderNeedsToUpdate = true;
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.master:
                    mFolder.type = (isChecked) ? "readonly" : "readwrite";
                    mFolderNeedsToUpdate = true;
                    break;
                case R.id.fileWatcher:
                    mFolder.fsWatcherEnabled = isChecked;
                    mFolderNeedsToUpdate = true;
                    break;
                case R.id.folderPause:
                    mFolder.paused = isChecked;
                    mFolderNeedsToUpdate = true;
                    break;
                case R.id.device_toggle:
                    Device device = (Device) view.getTag();
                    if (isChecked) {
                        mFolder.addDevice(device.deviceID);
                    } else {
                        mFolder.removeDevice(device.deviceID);
                    }
                    mFolderNeedsToUpdate = true;
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_folder);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);
        registerOnServiceConnectedListener(this);

        mLabelView = findViewById(R.id.label);
        mIdView = findViewById(R.id.id);
        mPathView = findViewById(R.id.directoryTextView);
        mFolderMasterView = findViewById(R.id.master);
        mFolderFileWatcher = findViewById(R.id.fileWatcher);
        mFolderPaused = findViewById(R.id.folderPause);
        mVersioningDescriptionView = findViewById(R.id.versioningDescription);
        mVersioningTypeView = findViewById(R.id.versioningType);
        mDevicesContainer = findViewById(R.id.devicesContainer);
        mEditIgnores = findViewById(R.id.edit_ignores);

        mPathView.setOnClickListener(view ->
                startActivityForResult(FolderPickerActivity.createIntent(this, mFolder.path, null), FolderPickerActivity.DIRECTORY_REQUEST_CODE));

        findViewById(R.id.versioningContainer).setOnClickListener(v -> showVersioningDialog());
        mEditIgnores.setOnClickListener(v -> editIgnores());

        if (mIsCreateMode) {
            if (savedInstanceState != null) {
                mFolder = new Gson().fromJson(savedInstanceState.getString("folder"), Folder.class);
                if (savedInstanceState.getBoolean(IS_SHOW_DISCARD_DIALOG)){
                    showDiscardDialog();
                }
            }
            if (mFolder == null) {
                initFolder();
            }
            // Open keyboard on label view in edit mode.
            mLabelView.requestFocus();
            mEditIgnores.setEnabled(false);
        }
        else {
            prepareEditMode();
        }

        if (savedInstanceState != null){
            if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
                showDeleteDialog();
            }
        }

        if (savedInstanceState != null){
            if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
                showDeleteDialog();
            }
        }
    }

    private void editIgnores() {
        try {
            File ignoreFile = new File(mFolder.path, IGNORE_FILE_NAME);
            if (!ignoreFile.exists() && !ignoreFile.createNewFile()) {
                Toast.makeText(this, R.string.create_ignore_file_error, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_EDIT);
            Uri uri = Uri.fromFile(ignoreFile);
            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            startActivity(intent);
        } catch (IOException e) {
            Log.w(TAG, e);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, e);
            Toast.makeText(this, R.string.edit_ignore_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showVersioningDialog() {
        Intent intent = new Intent(this, VersioningDialogActivity.class);
        intent.putExtras(getVersioningBundle());
        startActivityForResult(intent, FILE_VERSIONING_DIALOG_REQUEST);
    }

    private Bundle getVersioningBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry: mFolder.versioning.params.entrySet()){
            bundle.putString(entry.getKey(), entry.getValue());
        }

        if (TextUtils.isEmpty(mFolder.versioning.type)){
            bundle.putString("type", "none");
        } else{
            bundle.putString("type", mFolder.versioning.type);
        }

        return bundle;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService syncthingService = getService();
        if (syncthingService != null) {
            syncthingService.unregisterOnServiceStateChangeListener(this::onServiceStateChange);
        }
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mPathView.removeTextChangedListener(mTextWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();

        // We don't want to update every time a TextView's character changes,
        // so we hold off until the view stops being visible to the user.
        if (mFolderNeedsToUpdate) {
            updateFolder();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_SHOWING_DELETE_DIALOG, mDeleteDialog != null && mDeleteDialog.isShowing());
        Util.dismissDialogSafe(mDeleteDialog, this);

        if (mIsCreateMode){
            outState.putBoolean(IS_SHOW_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
            Util.dismissDialogSafe(mDiscardDialog, this);
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onServiceConnected() {
        getService().registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        if (currentState != ACTIVE) {
            finish();
            return;
        }

        if (!mIsCreateMode) {
            List<Folder> folders = getApi().getFolders();
            String passedId = getIntent().getStringExtra(EXTRA_FOLDER_ID);
            mFolder = null;
            for (Folder currentFolder : folders) {
                if (currentFolder.id.equals(passedId)) {
                    mFolder = currentFolder;
                    break;
                }
            }
            if (mFolder == null) {
                Log.w(TAG, "Folder not found in API update, maybe it was deleted?");
                finish();
                return;
            }
        }
        if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            mFolder.addDevice(getIntent().getStringExtra(EXTRA_DEVICE_ID));
            mFolderNeedsToUpdate = true;
        }

        attemptToApplyVersioningConfig();

        updateViewsAndSetListeners();
    }

    // If the FolderActivity gets recreated after the VersioningDialogActivity is closed, then the result from the VersioningDialogActivity will be received before
    // the mFolder variable has been recreated, so the versioning config will be stored in the mVersioning variable until the mFolder variable has been
    // recreated in the onServiceStateChange(). This has been observed to happen after the screen orientation has changed while the VersioningDialogActivity was open.
    private void attemptToApplyVersioningConfig() {
        if (mFolder != null && mVersioning != null){
            mFolder.versioning = mVersioning;
            mVersioning = null;
        }
    }

    private void updateViewsAndSetListeners() {
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mPathView.removeTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(null);
        mFolderFileWatcher.setOnCheckedChangeListener(null);
        mFolderPaused.setOnCheckedChangeListener(null);

        // Update views
        mLabelView.setText(mFolder.label);
        mIdView.setText(mFolder.id);
        mPathView.setText(mFolder.path);
        updateVersioningDescription();
        mFolderMasterView.setChecked(Objects.equal(mFolder.type, "readonly"));
        mFolderFileWatcher.setChecked(mFolder.fsWatcherEnabled);
        mFolderPaused.setChecked(mFolder.paused);
        List<Device> devicesList = getApi().getDevices(false);

        mDevicesContainer.removeAllViews();
        if (devicesList.isEmpty()) {
            addEmptyDeviceListView();
        } else {
            for (Device n : devicesList) {
                addDeviceViewAndSetListener(n, getLayoutInflater());
            }
        }

        // Keep state updated
        mLabelView.addTextChangedListener(mTextWatcher);
        mIdView.addTextChangedListener(mTextWatcher);
        mPathView.addTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(mCheckedListener);
        mFolderFileWatcher.setOnCheckedChangeListener(mCheckedListener);
        mFolderPaused.setOnCheckedChangeListener(mCheckedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.folder_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (TextUtils.isEmpty(mFolder.id)) {
                    Toast.makeText(this, R.string.folder_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (TextUtils.isEmpty(mFolder.path)) {
                    Toast.makeText(this, R.string.folder_path_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                getApi().addFolder(mFolder);
                finish();
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
                .setMessage(R.string.remove_folder_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    RestApi restApi = getApi();
                    if (restApi != null) {
                        restApi.removeFolder(mFolder.id);
                    }
                    mFolderNeedsToUpdate = false;
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE) {
            mFolder.path = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            mPathView.setText(mFolder.path);
            mFolderNeedsToUpdate = true;
            mEditIgnores.setEnabled(true);
        } else if (resultCode == Activity.RESULT_OK && requestCode == FILE_VERSIONING_DIALOG_REQUEST) {
            updateVersioning(data.getExtras());
        }
    }

    private String generateRandomFolderId() {
        char[] chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append("-");
            }
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }
    private void initFolder() {
        mFolder = new Folder();
        mFolder.id = (getIntent().hasExtra(EXTRA_FOLDER_ID))
                ? getIntent().getStringExtra(EXTRA_FOLDER_ID)
                : generateRandomFolderId();
        mFolder.label = getIntent().getStringExtra(EXTRA_FOLDER_LABEL);
        mFolder.fsWatcherEnabled = true;
        mFolder.fsWatcherDelayS = 10;
        /**
         * Folder rescan interval defaults to 3600s as it is the default in
         * syncthing when the file watcher is enabled and a new folder is created.
         */
        mFolder.rescanIntervalS = 3600;
        mFolder.paused = false;
        mFolder.versioning = new Folder.Versioning();
    }

    private void prepareEditMode() {
        mIdView.clearFocus();
        mIdView.setFocusable(false);
        mIdView.setEnabled(false);
        mPathView.setEnabled(false);
    }

    private void addEmptyDeviceListView() {
        int height = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, height);
        int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
        int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
        setMarginStart(params, dividerInset);
        setMarginEnd(params, contentInset);
        TextView emptyView = new TextView(mDevicesContainer.getContext());
        emptyView.setGravity(CENTER_VERTICAL);
        emptyView.setText(R.string.devices_list_empty);
        mDevicesContainer.addView(emptyView, params);
    }

    private void addDeviceViewAndSetListener(Device device, LayoutInflater inflater) {
        inflater.inflate(R.layout.item_device_form, mDevicesContainer);
        SwitchCompat deviceView = (SwitchCompat) mDevicesContainer.getChildAt(mDevicesContainer.getChildCount()-1);
        deviceView.setChecked(mFolder.getDevice(device.deviceID) != null);
        deviceView.setText(device.getDisplayName());
        deviceView.setTag(device);
        deviceView.setOnCheckedChangeListener(mCheckedListener);
    }

    private void updateFolder() {
        if (!mIsCreateMode) {
            getApi().editFolder(mFolder);
        }
    }

    @Override
    public void onBackPressed() {
        if (mIsCreateMode) {
            showDiscardDialog();
        }
        else {
            super.onBackPressed();
        }
    }

    private void showDiscardDialog(){
        mDiscardDialog = createDiscardDialog();
        mDiscardDialog.show();
    }

    private Dialog createDiscardDialog() {
        return new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_discard_changes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void updateVersioning(Bundle arguments) {
        if (mFolder != null){
            mVersioning = mFolder.versioning;
        } else {
            mVersioning = new Folder.Versioning();
        }

        String type = arguments.getString("type");
        arguments.remove("type");

        if (type.equals("none")){
            mVersioning = new Folder.Versioning();
        } else {
            for (String key : arguments.keySet()) {
                mVersioning.params.put(key, arguments.getString(key));
            }
            mVersioning.type = type;
        }

        attemptToApplyVersioningConfig();
        updateVersioningDescription();
        mFolderNeedsToUpdate = true;
    }

    private void updateVersioningDescription() {
        if (mFolder == null){
            return;
        }

        if (TextUtils.isEmpty(mFolder.versioning.type)) {
            setVersioningDescription(getString(R.string.none), "");
            return;
        }

        switch (mFolder.versioning.type) {
            case "simple":
                setVersioningDescription(getString(R.string.type_simple),
                        getString(R.string.simple_versioning_info, mFolder.versioning.params.get("keep")));
                break;
            case "trashcan":
                setVersioningDescription(getString(R.string.type_trashcan),
                        getString(R.string.trashcan_versioning_info, mFolder.versioning.params.get("cleanoutDays")));
                break;
            case "staggered":
                int maxAge = (int) TimeUnit.SECONDS.toDays(Long.valueOf(mFolder.versioning.params.get("maxAge")));
                setVersioningDescription(getString(R.string.type_staggered),
                        getString(R.string.staggered_versioning_info, maxAge, mFolder.versioning.params.get("versionsPath")));
                break;
            case "external":
                setVersioningDescription(getString(R.string.type_external),
                        getString(R.string.external_versioning_info, mFolder.versioning.params.get("command")));
                break;
        }
    }

    private void setVersioningDescription(String type, String description) {
        mVersioningTypeView.setText(type);
        mVersioningDescriptionView.setText(description);
    }
}
