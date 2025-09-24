package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.SharedWithDevice;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.FileUtils.ExternalStorageDirType;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginEnd;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginStart;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED;
import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

/**
 * Shows folder details and allows changing them.
 */
public class FolderActivity extends SyncthingActivity {
    public static final String EXTRA_DEVICE_ID =
            ".activities.FolderActivity.DEVICE_ID";

    public static final String EXTRA_FOLDER_ID =
            ".activities.FolderActivity.FOLDER_ID";
    public static final String EXTRA_FOLDER_LABEL =
            ".activities.FolderActivity.FOLDER_LABEL";
    public static final String EXTRA_IS_CREATE =
            ".activities.FolderActivity.IS_CREATE";
    public static final String EXTRA_NOTIFICATION_ID =
            ".activities.FolderActivity.NOTIFICATION_ID";
    public static final String EXTRA_RECEIVE_ENCRYPTED =
            ".activities.FolderActivity.RECEIVE_ENCRYPTED";
    public static final String EXTRA_REMOTE_ENCRYPTED =
            ".activities.FolderActivity.REMOTE_ENCRYPTED";

    private static final String TAG = "FolderActivity";

    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";
    private static final String IS_SHOW_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";

    private static final int FILE_VERSIONING_DIALOG_REQUEST = 3454;
    private static final int PULL_ORDER_DIALOG_REQUEST = 3455;
    private static final int FOLDER_TYPE_DIALOG_REQUEST =3456;
    private static final int CHOOSE_FOLDER_REQUEST = 3459;

    public static final int FOLDER_ADD_CODE = 402;

    private ConfigRouter mConfig;
    private Folder mFolder;
    // Contains SAF readwrite access URI on API level >= Build.VERSION_CODES.LOLLIPOP (21)
    private Uri mFolderUri = null;
    // Indicates the result of the write test to mFolder.path on dialog init or after a path change.
    Boolean mCanWriteToPath = false;

    private EditText mLabelView;
    private EditText mIdView;
    private TextView mPathView;
    private View mSelectAdvancedDirectory;
    private TextView mAccessExplanationView;
    private TextView mFolderTypeView;
    private TextView mFolderTypeDescriptionView;
    private ViewGroup mDevicesContainer;
    private SwitchCompat mFolderFileWatcher;
    private SwitchCompat mFolderPaused;
    private SwitchCompat mCustomSyncConditionsSwitch;
    private TextView mCustomSyncConditionsDescription;
    private TextView mCustomSyncConditionsDialog;
    private ViewGroup mPullOrderContainer;
    private TextView mPullOrderTypeView;
    private TextView mPullOrderDescriptionView;
    private TextView mVersioningDescriptionView;
    private TextView mVersioningTypeView;
    private ViewGroup mIgnoreDeleteContainer;
    private SwitchCompat mRunScriptSwitch;
    private ViewGroup mRunScriptContainer;
    private SwitchCompat mIgnoreDelete;
    private TextView mEditIgnoreListTitle;
    private EditText mEditIgnoreListContent;

    @Inject
    SharedPreferences mPreferences;

    private boolean mPrefExpertMode = false;

    private boolean mIsCreateMode;
    private boolean mFolderNeedsToUpdate = false;
    private boolean mIgnoreListNeedsToUpdate = false;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mFolderNeedsToUpdate) {
                showDiscardDialog();
            } else {
                // Let default behavior handle it
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        }
    };

    private final TextWatcher mTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.label        = mLabelView.getText().toString().trim();
            mFolder.id           = mIdView.getText().toString();

            // Loop through devices the folder is shared to and update encryptionPassword property.
            for (int i = 0; i < mDevicesContainer.getChildCount(); i++) {
                if (mDevicesContainer.getChildAt(i) instanceof TextView) {
                    continue;
                }
                LinearLayout deviceView = (LinearLayout) mDevicesContainer.getChildAt(i);

                SwitchCompat switchView = (SwitchCompat) deviceView.getChildAt(0);
                SharedWithDevice device = mFolder.getDevice(((SharedWithDevice) switchView.getTag()).deviceID);
                if (device != null) {
                    EditText encryptPassView = (EditText) deviceView.getChildAt(1);
                    String newEncryptionPassword = encryptPassView.getText().toString();
                    if (!device.encryptionPassword.equals(newEncryptionPassword)) {
                        device.encryptionPassword = newEncryptionPassword;
                        mFolderNeedsToUpdate = true;
                    }
                }
            }

            // mPathView must not be handled here as it's handled by {@link onActivityResult}
            // mEditIgnoreListContent must not be handled here as it's written back when the dialog ends.
            mFolderNeedsToUpdate = true;
        }
    };

    private final TextWatcher mIgnoreListContentTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mIgnoreListNeedsToUpdate = true;
            mFolderNeedsToUpdate = true;
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            int id = view.getId();
            if (id == R.id.fileWatcher) {
                mFolder.fsWatcherEnabled = isChecked;
                mFolderNeedsToUpdate = true;
            } else if (id == R.id.folderPause) {
                mFolder.paused = isChecked;
                mFolderNeedsToUpdate = true;
            } else if (id == R.id.customSyncConditionsSwitch) {
                mCustomSyncConditionsDescription.setEnabled(isChecked);
                mCustomSyncConditionsDialog.setFocusable(isChecked);
                mCustomSyncConditionsDialog.setEnabled(isChecked);
                // This is needed to display the "discard changes dialog".
                mFolderNeedsToUpdate = true;
            } else if (id == R.id.device_toggle) {
                SharedWithDevice device = (SharedWithDevice) view.getTag();

                // Loop through devices the folder is shared to and show/hide encryptionPassword UI.
                for (int i = 0; i < mDevicesContainer.getChildCount(); i++) {
                    LinearLayout deviceView = (LinearLayout) mDevicesContainer.getChildAt(i);
                    SwitchCompat switchView = (SwitchCompat) deviceView.getChildAt(0);
                    if (device == ((SharedWithDevice) switchView.getTag())) {
                        EditText encryptPassView = (EditText) deviceView.getChildAt(1);
                        encryptPassView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                        break;
                    }
                }

                if (isChecked) {
                    mFolder.addDevice(device);
                } else {
                    mFolder.removeDevice(device.deviceID);
                }
                mFolderNeedsToUpdate = true;
            } else if (id == R.id.runScriptSwitch) {
                // Stored in pref.
                mFolderNeedsToUpdate = true;
            } else if (id == R.id.ignoreDelete) {
                mFolder.ignoreDelete = isChecked;
                mFolderNeedsToUpdate = true;
            }
        }
    };

    public static Intent createIntent(Context context) {
        Intent intent = new Intent(context, FolderActivity.class);
        intent.putExtra(EXTRA_IS_CREATE, true);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mConfig = new ConfigRouter(FolderActivity.this);

        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        setContentView(R.layout.activity_folder);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);

        mPrefExpertMode = mPreferences.getBoolean(Constants.PREF_EXPERT_MODE, false);

        mLabelView = findViewById(R.id.label);
        mIdView = findViewById(R.id.id);
        mPathView = findViewById(R.id.directoryTextView);
        mSelectAdvancedDirectory = findViewById(R.id.selectAdvancedDirectory);
        mAccessExplanationView = findViewById(R.id.accessExplanationView);
        mFolderTypeView = findViewById(R.id.folderType);
        mFolderTypeDescriptionView = findViewById(R.id.folderTypeDescription);
        mFolderFileWatcher = findViewById(R.id.fileWatcher);
        mFolderPaused = findViewById(R.id.folderPause);
        mCustomSyncConditionsSwitch = findViewById(R.id.customSyncConditionsSwitch);
        mCustomSyncConditionsDescription = findViewById(R.id.customSyncConditionsDescription);
        mCustomSyncConditionsDialog = findViewById(R.id.customSyncConditionsDialog);
        mPullOrderContainer = findViewById(R.id.pullOrderContainer);
        mPullOrderTypeView = findViewById(R.id.pullOrderType);
        mPullOrderDescriptionView = findViewById(R.id.pullOrderDescription);
        mVersioningDescriptionView = findViewById(R.id.versioningDescription);
        mVersioningTypeView = findViewById(R.id.versioningType);
        mIgnoreDeleteContainer = findViewById(R.id.ignoreDeleteContainer);
        mRunScriptContainer = findViewById(R.id.runScriptContainer);
        mRunScriptSwitch = findViewById(R.id.runScriptSwitch);
        mIgnoreDelete = findViewById(R.id.ignoreDelete);
        mDevicesContainer = findViewById(R.id.devicesContainer);
        mEditIgnoreListTitle = findViewById(R.id.edit_ignore_list_title);
        mEditIgnoreListContent = findViewById(R.id.edit_ignore_list_content);

        // Android 11 disallows selecting the "Downloads" and the emulated storage root directory.
        mSelectAdvancedDirectory.setVisibility(
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? View.VISIBLE : View.GONE
        );
        mSelectAdvancedDirectory.setOnClickListener(view -> onSelectAdvancedDirectoryClick());

        mPathView.setOnClickListener(view -> onPathViewClick());
        mCustomSyncConditionsDialog.setOnClickListener(view -> onCustomSyncConditionsDialogClick());

        ViewGroup folderTypeContainer = findViewById(R.id.folderTypeContainer);
        folderTypeContainer.setOnClickListener(v -> showFolderTypeDialog());
        mPullOrderContainer.setOnClickListener(v -> showPullOrderDialog());
        findViewById(R.id.versioningContainer).setOnClickListener(v -> showVersioningDialog());

        if (savedInstanceState != null) {
            Log.d(TAG, "Retrieving state from savedInstanceState ...");
            mFolder = new Gson().fromJson(savedInstanceState.getString("mFolder"), Folder.class);
            mFolderNeedsToUpdate = savedInstanceState.getBoolean("mFolderNeedsToUpdate");
            mIgnoreListNeedsToUpdate = savedInstanceState.getBoolean("mIgnoreListNeedsToUpdate");
            mFolderUri = savedInstanceState.getParcelable("mFolderUri");
            restoreDialogStates(savedInstanceState);
        } else {
            // Fresh init of the edit or create mode.
            if (mIsCreateMode) {
                Log.d(TAG, "Initializing create mode ...");
                initFolder();
                mFolderNeedsToUpdate = true;
            } else {
                // Edit mode.
                String passedId = getIntent().getStringExtra(EXTRA_FOLDER_ID);
                Log.d(TAG, "Initializing edit mode: folder.id=" + passedId);
                // getApi() is unavailable (onCreate > onPostCreate > onServiceConnected)
                List<Folder> folders = mConfig.getFolders(null);
                mFolder = null;
                for (Folder currentFolder : folders) {
                    if (currentFolder.id.equals(passedId)) {
                        mFolder = currentFolder;
                        break;
                    }
                }
                if (mFolder == null) {
                    Log.w(TAG, "Folder not found in API update, maybe it was deleted?");
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                mConfig.getFolderIgnoreList(null, mFolder, this::onReceiveFolderIgnoreList);
                mFolderNeedsToUpdate = false;
            }

            // If the extra is set, we should automatically share the current folder with the given device.
            if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
                SharedWithDevice device = new SharedWithDevice();
                device.deviceID = getIntent().getStringExtra(EXTRA_DEVICE_ID);
                mFolder.addDevice(device);
                mFolderNeedsToUpdate = true;
            }
        }

        if (mIsCreateMode) {
            mEditIgnoreListTitle.setEnabled(false);
            mEditIgnoreListContent.setEnabled(false);
        } else {
            // Edit mode.
            mIdView.setFocusable(false);
            mIdView.setEnabled(false);
            mPathView.setFocusable(false);
            mPathView.setEnabled(false);
            mSelectAdvancedDirectory.setVisibility(View.GONE);
        }
        folderTypeContainer.setEnabled(!mFolder.type.equals(Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED));
        checkWriteAndUpdateUI();
        updateViewsAndSetListeners();

        // Show expert options conditionally.
        mIgnoreDeleteContainer.setVisibility(mPrefExpertMode ? View.VISIBLE : View.GONE);
        mRunScriptContainer.setVisibility(mPrefExpertMode ? View.VISIBLE : View.GONE);

        // Open keyboard on label view in edit mode.
        mLabelView.requestFocus();

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    private void restoreDialogStates(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)) {
            showDeleteDialog();
        } else if (savedInstanceState.getBoolean(IS_SHOW_DISCARD_DIALOG)) {
            showDiscardDialog();
        }
    }

    /**
     * Invoked after user clicked on the {@link #mPathView} label.
     */
    @SuppressLint("InlinedAPI")
    private void onPathViewClick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        
        // Determine directory initialUri for SAF file picker dialog.
        // This has to be android.net.Uri as it implements a Parcelable.
        android.net.Uri initialUri = null;
        android.net.Uri externalFilesDirUri = FileUtils.getExternalFilesDirUri(FolderActivity.this, ExternalStorageDirType.INT_MEDIA);
        if (FileUtils.directoryUriExists(FolderActivity.this, externalFilesDirUri)) {
            initialUri = externalFilesDirUri;
        } else {
            android.net.Uri internalFilesDirUri = FileUtils.getInternalStorageRootUri();
            if (FileUtils.directoryUriExists(FolderActivity.this, internalFilesDirUri)) {
                initialUri = internalFilesDirUri;
            }
        }
        if (initialUri != null) {
            Log.v(TAG, "onPathViewClick: INITIAL_URI = " + initialUri);
            intent.putExtra("android.provider.extra.INITIAL_URI", initialUri);
        }

        // Display storage access framework directory picker UI.
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        try {
            startActivityForResult(intent, CHOOSE_FOLDER_REQUEST);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "onPathViewClick exception, falling back to built-in FolderPickerActivity.", e);
            startActivityForResult(FolderPickerActivity.createIntent(this, mFolder.path, null),
                FolderPickerActivity.DIRECTORY_REQUEST_CODE);
        }
    }

    /**
     * Open dialog if the user clicked on empty device list view.
     */
    private void showAddDeviceDialog() {
        startActivityForResult(DeviceActivity.createIntent(this), DeviceActivity.DEVICE_ADD_CODE);
    }

    /**
     * Invoked after user clicked on the {@link #mCustomSyncConditionsDialog} label.
     */
    private void onCustomSyncConditionsDialogClick() {
        startActivityForResult(
            SyncConditionsActivity.createIntent(
                this, Constants.PREF_OBJECT_PREFIX_FOLDER + mFolder.id, mFolder.label
            ),
            0
        );
    }

    /**
     * Invoked after user clicked on the select advanced directory button.
     */
    private void onSelectAdvancedDirectoryClick() {
        startActivityForResult(FolderPickerActivity.createIntent(this, mFolder.path, null),
            FolderPickerActivity.DIRECTORY_REQUEST_CODE);
    }

    private void showFolderTypeDialog() {
        if (TextUtils.isEmpty(mFolder.path)) {
            Toast.makeText(this, R.string.folder_path_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!mCanWriteToPath) {
            /**
             * Do not handle the click as the children in the folder type layout are disabled
             * and an explanation is already given on the UI why the only allowed folder type
             * is "sendonly".
            */
            Toast.makeText(this, R.string.folder_path_readonly, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        // The user selected folder path is writeable, offer to choose from all available folder types.
        Intent intent = new Intent(this, FolderTypeDialogActivity.class);
        intent.putExtra(FolderTypeDialogActivity.EXTRA_FOLDER_TYPE, mFolder.type);
        startActivityForResult(intent, FOLDER_TYPE_DIALOG_REQUEST);
    }

    private void showPullOrderDialog() {
        Intent intent = new Intent(this, PullOrderDialogActivity.class);
        intent.putExtra(PullOrderDialogActivity.EXTRA_PULL_ORDER, mFolder.order);
        startActivityForResult(intent, PULL_ORDER_DIALOG_REQUEST);
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
            syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        }
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mEditIgnoreListContent.removeTextChangedListener(mIgnoreListContentTextWatcher);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("mFolder", new Gson().toJson(mFolder));
        outState.putBoolean("mFolderNeedsToUpdate", mFolderNeedsToUpdate);
        outState.putBoolean("mIgnoreListNeedsToUpdate", mIgnoreListNeedsToUpdate);
        outState.putParcelable("mFolderUri", mFolderUri);

        outState.putBoolean(IS_SHOWING_DELETE_DIALOG, mDeleteDialog != null && mDeleteDialog.isShowing());
        Util.dismissDialogSafe(mDeleteDialog, this);

        outState.putBoolean(IS_SHOW_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
        Util.dismissDialogSafe(mDiscardDialog, this);
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
    }

    private void onReceiveFolderIgnoreList(FolderIgnoreList folderIgnoreList) {
        mEditIgnoreListContent.setMaxLines(Integer.MAX_VALUE);
        mEditIgnoreListContent.removeTextChangedListener(mIgnoreListContentTextWatcher);
        if (folderIgnoreList.ignore != null) {
            String ignoreList = TextUtils.join("\n", folderIgnoreList.ignore);
            mEditIgnoreListContent.setText(ignoreList);
        }
        mEditIgnoreListContent.addTextChangedListener(mIgnoreListContentTextWatcher);
    }

    private void updateViewsAndSetListeners() {
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mFolderFileWatcher.setOnCheckedChangeListener(null);
        mFolderPaused.setOnCheckedChangeListener(null);
        mCustomSyncConditionsSwitch.setOnCheckedChangeListener(null);
        mIgnoreDelete.setOnCheckedChangeListener(null);
        mRunScriptSwitch.setOnCheckedChangeListener(null);

        // Update views
        mLabelView.setText(mFolder.label);
        mIdView.setText(mFolder.id);
        updateFolderTypeDescription();
        updatePullOrderDescription();
        updateVersioningDescription();
        mFolderFileWatcher.setChecked(mFolder.fsWatcherEnabled);
        mFolderPaused.setChecked(mFolder.paused);
        mIgnoreDelete.setChecked(mFolder.ignoreDelete);
        mRunScriptSwitch.setChecked(mPreferences.getBoolean(
                Constants.DYN_PREF_OBJECT_FOLDER_RUN_SCRIPT(mFolder.id), false
            ));
        findViewById(R.id.editIgnoresContainer).setVisibility(mIsCreateMode ? View.GONE : View.VISIBLE);

        // Update views - custom sync conditions.
        mCustomSyncConditionsSwitch.setChecked(false);
        if (mIsCreateMode) {
            findViewById(R.id.customSyncConditionsContainer).setVisibility(View.GONE);
        } else {
            mCustomSyncConditionsSwitch.setChecked(mPreferences.getBoolean(
                Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + mFolder.id), false
            ));
        }
        mCustomSyncConditionsSwitch.setEnabled(!mIsCreateMode);
        mCustomSyncConditionsDescription.setEnabled(mCustomSyncConditionsSwitch.isChecked());
        mCustomSyncConditionsDialog.setFocusable(mCustomSyncConditionsSwitch.isChecked());
        mCustomSyncConditionsDialog.setEnabled(mCustomSyncConditionsSwitch.isChecked());

        // Populate devicesList.
        RestApi restApi = getApi();
        List<Device> devicesList = mConfig.getDevices(restApi, false);
        mDevicesContainer.removeAllViews();
        if (devicesList.isEmpty()) {
            addEmptyDeviceListView();
        } else {
            for (Device device : devicesList) {
                addDeviceViewAndSetListener(device, getLayoutInflater());
            }
        }

        // Keep state updated
        mLabelView.addTextChangedListener(mTextWatcher);
        mIdView.addTextChangedListener(mTextWatcher);
        mFolderFileWatcher.setOnCheckedChangeListener(mCheckedListener);
        mFolderPaused.setOnCheckedChangeListener(mCheckedListener);
        mCustomSyncConditionsSwitch.setOnCheckedChangeListener(mCheckedListener);
        mIgnoreDelete.setOnCheckedChangeListener(mCheckedListener);
        mRunScriptSwitch.setOnCheckedChangeListener(mCheckedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.folder_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.save).setTitle(mIsCreateMode ? R.string.create : R.string.save_title);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.save) {
            onSave();
            return true;
        } else if (itemId == R.id.remove) {
            showDeleteDialog();
            return true;
        } else if (itemId == android.R.id.home) {
            mBackPressedCallback.handleOnBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteDialog(){
        mDeleteDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.remove_folder_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    mConfig.removeFolder(getApi(), mFolder.id);
                    if (mFolder.id.equals(Constants.syncthingCameraFolderId)) {
                        // Remove consent to "Syncthing Camera" feature.
                        SharedPreferences.Editor editor = mPreferences.edit();
                        editor.putBoolean(Constants.PREF_ENABLE_SYNCTHING_CAMERA, false);
                        editor.apply();
                    }
                    mFolderNeedsToUpdate = false;
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
        mDeleteDialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == CHOOSE_FOLDER_REQUEST) {
            // This result case only occurs on API level >= Build.VERSION_CODES.LOLLIPOP (21)
            mFolderUri = data.getData();
            if (mFolderUri == null) {
                return;
            }
            // Get the folder path unix style, e.g. "/storage/0000-0000/DCIM"
            String targetPath = FileUtils.getAbsolutePathFromSAFUri(FolderActivity.this, mFolderUri);
            if (targetPath != null) {
                targetPath = Util.formatPath(targetPath);
            }
            if (targetPath == null || TextUtils.isEmpty(targetPath) || (targetPath.equals(File.separator))) {
                mFolder.path = "";
                mFolderUri = null;
                checkWriteAndUpdateUI();
                // Show message to the user suggesting to select a folder on internal or external storage.
                Toast.makeText(this, R.string.toast_invalid_folder_selected, Toast.LENGTH_LONG).show();
                return;
            }
            mFolder.path = FileUtils.cutTrailingSlash(targetPath);
            Log.v(TAG, "onActivityResult/CHOOSE_FOLDER_REQUEST: Got directory path '" + mFolder.path + "'");
            checkWriteAndUpdateUI();
            // Postpone sending the config changes using syncthing REST API.
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE) {
            mFolder.path = FileUtils.cutTrailingSlash(data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY));
            Log.v(TAG, "onActivityResult/DIRECTORY_REQUEST_CODE: Got directory path '" + mFolder.path + "'");
            checkWriteAndUpdateUI();
            // Postpone sending the config changes using syncthing REST API.
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == FILE_VERSIONING_DIALOG_REQUEST) {
            updateVersioning(data.getExtras());
        } else if (resultCode == Activity.RESULT_OK && requestCode == FOLDER_TYPE_DIALOG_REQUEST) {
            String newFolderType = data.getStringExtra(FolderTypeDialogActivity.EXTRA_RESULT_FOLDER_TYPE);
            if (!mIsCreateMode && newFolderType.equals(Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED)) {
                // Disallow switching existing folder's type to receiveEncrypted.
                // SyncthingNative also does this. Posting a wrong config will result in http code 500.
                Toast.makeText(this, R.string.folder_type_switch_to_receive_encrypted_not_allowed, Toast.LENGTH_LONG).show();
                return;
            }
            mFolder.type = newFolderType;
            updateFolderTypeDescription();
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == PULL_ORDER_DIALOG_REQUEST) {
            mFolder.order = data.getStringExtra(PullOrderDialogActivity.EXTRA_RESULT_PULL_ORDER);
            updatePullOrderDescription();
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == DeviceActivity.DEVICE_ADD_CODE) {
            updateViewsAndSetListeners();
        }
    }

    /**
     * Prerequisite: mFolder.path must be non-empty
     */
    private void checkWriteAndUpdateUI() {
        mPathView.setText(mFolder.path);
        if (TextUtils.isEmpty(mFolder.path)) {
            return;
        }

        /**
         * Check if the permissions we have on that folder is readonly or readwrite.
         * Access level readonly: folder can only be configured "sendonly".
         * Access level readwrite: folder can be configured "sendonly" or "sendreceive".
         */
        mCanWriteToPath = Util.nativeBinaryCanWriteToPath(FolderActivity.this, mFolder.path);
        if (mCanWriteToPath) {
            mAccessExplanationView.setText(R.string.folder_path_readwrite);
            mFolderTypeView.setEnabled(true);
            if (mIsCreateMode) {
                /**
                 * Suggest folder type FOLDER_TYPE_SEND_RECEIVE for folders to be created
                 * because the user most probably intentionally chose a special folder like
                 * "[storage]/Android/data/com.nutomic.syncthingandroid/files"
                 * or enabled root mode thus having write access.
                 * Default from {@link #initFolder} was already set in {@link #onCreate}.
                 *      mFolder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
                 * We won't set it again here as this would cause user selection to be reset on
                 * screen rotation - as we don't know if we restored the activity or created
                 * a fresh one.
                 */
                updateFolderTypeDescription();
            } else {
                mEditIgnoreListTitle.setEnabled(true);
                mEditIgnoreListContent.setEnabled(true);
            }
        } else {
            // Force "sendonly" folder.
            mAccessExplanationView.setText(R.string.folder_path_readonly);
            mFolderTypeView.setEnabled(false);
            mEditIgnoreListTitle.setEnabled(false);
            mEditIgnoreListContent.setEnabled(false);
            mFolder.type = Constants.FOLDER_TYPE_SEND_ONLY;
            updateFolderTypeDescription();
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

    /**
     * Init a new folder in mIsCreateMode, used in {@link #onCreate}.
     */
    private void initFolder() {
        mFolder = new Folder();
        mFolder.id = (getIntent().hasExtra(EXTRA_FOLDER_ID))
                ? getIntent().getStringExtra(EXTRA_FOLDER_ID)
                : generateRandomFolderId();
        mFolder.label = getIntent().getStringExtra(EXTRA_FOLDER_LABEL);
        if (!TextUtils.isEmpty(mFolder.label)) {
            mFolder.label = mFolder.label.trim();
        }
        mFolder.paused = false;
        if (getIntent().getBooleanExtra(EXTRA_RECEIVE_ENCRYPTED, false)) {
            mFolder.type = Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED;
        } else {
            mFolder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;      // Default for {@link #checkWriteAndUpdateUI}.
        }
        mFolder.minDiskFree = new Folder.MinDiskFree();
        mFolder.versioning = new Folder.Versioning();
        mFolder.versioning.type = "trashcan";
        mFolder.versioning.params.put("cleanoutDays", Integer.toString(14));
        mFolder.versioning.cleanupIntervalS = 0;
        mFolder.versioning.fsPath = "";
        mFolder.versioning.fsType = "basic";
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
        mDevicesContainer.setOnClickListener(view -> showAddDeviceDialog());
    }

    private void addDeviceViewAndSetListener(Device device, LayoutInflater inflater) {
        SharedWithDevice sharedWithDevice = new SharedWithDevice();
        sharedWithDevice.deviceID = device.deviceID;
        sharedWithDevice.introducedBy = device.introducedBy;

        inflater.inflate(R.layout.item_device_form, mDevicesContainer);
        LinearLayout deviceView = (LinearLayout) mDevicesContainer.getChildAt(mDevicesContainer.getChildCount()-1);

        SwitchCompat switchView = (SwitchCompat) deviceView.getChildAt(0);
        switchView.setOnCheckedChangeListener(null);
        switchView.setChecked(mFolder.getDevice(device.deviceID) != null);
        switchView.setText(device.getDisplayName());
        switchView.setTag(sharedWithDevice);
        switchView.setOnCheckedChangeListener(mCheckedListener);

        EditText encryptPassView = (EditText) deviceView.getChildAt(1);
        encryptPassView.removeTextChangedListener(mTextWatcher);
        if (mFolder.getDevice(device.deviceID) != null) {
            encryptPassView.setText(mFolder.getDevice(device.deviceID).encryptionPassword);
        } else {
            encryptPassView.setVisibility(View.GONE);
        }
        encryptPassView.addTextChangedListener(mTextWatcher);
    }

    private void onSave() {
        if (mFolder == null) {
            Log.e(TAG, "onSave: mFolder == null");
            return;
        }

        // Validate fields.
        if (TextUtils.isEmpty(mFolder.id)) {
            Toast.makeText(this, R.string.folder_id_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (TextUtils.isEmpty(mFolder.label)) {
            Toast.makeText(this, R.string.folder_label_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (TextUtils.isEmpty(mFolder.path)) {
            Toast.makeText(this, R.string.folder_path_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(
            Constants.DYN_PREF_OBJECT_FOLDER_RUN_SCRIPT(mFolder.id),
            mRunScriptSwitch.isChecked()
        );
        editor.apply();

        if (mIsCreateMode) {
            Log.v(TAG, "onSave: Adding folder with ID = \'" + mFolder.id + "\'");
            preCreateFolderStruct(mFolderUri, mFolder.path);
            mConfig.addFolder(getApi(), mFolder);

            // Start sync after adding a folder
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getApplication().getApplicationContext());
            Intent intent = new Intent(ACTION_SYNC_TRIGGER_FIRED);
            intent.putExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
            localBroadcastManager.sendBroadcast(intent);

            setResult(AppCompatActivity.RESULT_OK);
            finish();
            return;
        }

        // Edit mode.
        if (!mFolderNeedsToUpdate) {
            // We've got nothing to save.
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
            return;
        }

        // Save folder specific preferences.
        Log.v(TAG, "onSave: Updating folder with ID = \'" + mFolder.id + "\'");
        editor = mPreferences.edit();
        editor.putBoolean(
            Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + mFolder.id),
            mCustomSyncConditionsSwitch.isChecked()
        );
        editor.apply();

        // Update folder via restApi and send the config to REST endpoint.
        RestApi restApi = getApi();
        if (mIgnoreListNeedsToUpdate) {
            // Update ignore list.
            String[] ignore = mEditIgnoreListContent.getText().toString().split("\n");
            mConfig.postFolderIgnoreList(restApi, mFolder, ignore);
        }

        // Update folder using RestApi or ConfigXml.
        mConfig.updateFolder(restApi, mFolder);
        setResult(AppCompatActivity.RESULT_OK);
        finish();
        return;
    }

    private void preCreateFolderStruct(Uri uriFolderRoot, String absolutePath) {
        /**
         * Normally, syncthing takes care of creating the ".stfolder" marker.
         * This fails on Android 5+ if the syncthing binary only has
         * readonly access on the path and the user tries to configure a
         * sendOnly folder. To fix this, we'll precreate the marker using java code.
         */
        final String FOLDER_MARKER_DIR_NAME = new Folder().markerName;
        String strFolderMarkerPath = absolutePath + File.separator + FOLDER_MARKER_DIR_NAME;

        /**
         * Name of the dummy file created within the marker directory.
         * Creating the file is a workaround for issue where manufacturer
         * specific cleaning routines silently wipe out empty directories like
         * the marker directory.
         */
        final String DO_NOT_DELETE_FILE_NAME = "DO_NOT_DELETE";
        String strDoNotDeleteFile = strFolderMarkerPath + File.separator + DO_NOT_DELETE_FILE_NAME;

        /**
         * Precreate .stversions directory so we can put ".nomedia" in place to keep the gallery clean.
         */
        final String strStVersionsPath = absolutePath + File.separator + Constants.FOLDER_NAME_STVERSIONS;
        final String strStVersionsNoMediaFile = strStVersionsPath + File.separator + ".nomedia";

        // Fall back to classic API if uriFolderRoot is missing. E.g. in case FolderPickerActivity was used which only returns an absolute path.
        if (uriFolderRoot == null) {
            Log.w(TAG, "preCreateFolderStruct: uriFolderRoot == null. Using absolute path.");
            try {
                // ".stfolder"
                new File(strFolderMarkerPath).mkdirs();
                if (new File(strDoNotDeleteFile).createNewFile()) {
                    FileWriter writer = new FileWriter(strDoNotDeleteFile);
                    writer.write(DO_NOT_DELETE_FILE_NAME);
                    writer.close();
                }

                // ".stversions"
                new File(strStVersionsPath).mkdirs();
                new File(strStVersionsNoMediaFile).createNewFile();
            } catch (Exception e) {
                Log.e(TAG, "preCreateFolderStruct: Failed to create using absolute path.", e);
            }
            return;
        }

        // Derive DocumentFile handle from SAF tree Uri where we have write access.
        DocumentFile dfFolder = DocumentFile.fromTreeUri(this, uriFolderRoot);

        // Create ".stfolder" directory.
        DocumentFile dfFolderMarkerDir = FileUtils.safCreateDirectory(dfFolder, FOLDER_MARKER_DIR_NAME);
        if (dfFolderMarkerDir != null) {
            // Create ".stfolder/DO_NOT_DELETE.txt" file.
            FileUtils.safCreateFile(this, dfFolderMarkerDir, DO_NOT_DELETE_FILE_NAME + ".txt", DO_NOT_DELETE_FILE_NAME);
        }

        // Create ".stversions" directory.
        DocumentFile dfStVersionsDir = FileUtils.safCreateDirectory(dfFolder, Constants.FOLDER_NAME_STVERSIONS);
        if (dfStVersionsDir != null) {
            // Create ".stversions/.nomedia" file.
            FileUtils.safCreateFile(this, dfStVersionsDir, ".nomedia", "");
        }
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

    private void updateVersioning(Bundle arguments) {
        if (mFolder == null) {
            Log.e(TAG, "updateVersioning: mFolder == null");
            return;
        }
        if (mFolder.versioning == null) {
            mFolder.versioning = new Folder.Versioning();
        }

        String type = arguments.getString("type");
        arguments.remove("type");

        if (type.equals("none")) {
            mFolder.versioning = new Folder.Versioning();
            mFolder.versioning.type = "";
        } else {
            for (String key : arguments.keySet()) {
                mFolder.versioning.params.put(key, arguments.getString(key));
            }
            mFolder.versioning.type = type;
        }
        updateVersioningDescription();
        mFolderNeedsToUpdate = true;
    }

    private void updateFolderTypeDescription() {
        if (mFolder == null) {
            return;
        }

        switch (mFolder.type) {
            case Constants.FOLDER_TYPE_SEND_RECEIVE:
                setFolderTypeDescription(getString(R.string.folder_type_sendreceive),
                        getString(R.string.folder_type_sendreceive_description));
                break;
            case Constants.FOLDER_TYPE_SEND_ONLY:
                setFolderTypeDescription(getString(R.string.folder_type_sendonly),
                        getString(R.string.folder_type_sendonly_description));
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ONLY:
                setFolderTypeDescription(getString(R.string.folder_type_receiveonly),
                        getString(R.string.folder_type_receiveonly_description));
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED:
                setFolderTypeDescription(getString(R.string.folder_type_receive_encrypted),
                        getString(R.string.folder_type_receive_encrypted_description));
                break;
        }

        // Disable "file pull order" option for sendOnly folders.
        mPullOrderContainer.setVisibility(mPrefExpertMode &&
                !mFolder.type.equals(Constants.FOLDER_TYPE_SEND_ONLY) ? View.VISIBLE : View.GONE);
    }

    private void setFolderTypeDescription(String type, String description) {
        mFolderTypeView.setText(type);
        mFolderTypeDescriptionView.setText(description);
    }

    private void updatePullOrderDescription() {
        if (mFolder == null) {
            return;
        }

        if (TextUtils.isEmpty(mFolder.order)) {
            setPullOrderDescription(getString(R.string.pull_order_type_random),
                    getString(R.string.pull_order_type_random_description));
            return;
        }

        switch (mFolder.order) {
            case "random":
                setPullOrderDescription(getString(R.string.pull_order_type_random),
                        getString(R.string.pull_order_type_random_description));
                break;
            case "alphabetic":
                setPullOrderDescription(getString(R.string.pull_order_type_alphabetic),
                        getString(R.string.pull_order_type_alphabetic_description));
                break;
            case "smallestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_smallestFirst),
                        getString(R.string.pull_order_type_smallestFirst_description));
                break;
            case "largestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_largestFirst),
                        getString(R.string.pull_order_type_largestFirst_description));
                break;
            case "oldestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_oldestFirst),
                        getString(R.string.pull_order_type_oldestFirst_description));
                break;
            case "newestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_newestFirst),
                        getString(R.string.pull_order_type_newestFirst_description));
                break;
        }
    }

    private void setPullOrderDescription(String type, String description) {
        mPullOrderTypeView.setText(type);
        mPullOrderDescriptionView.setText(description);
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
