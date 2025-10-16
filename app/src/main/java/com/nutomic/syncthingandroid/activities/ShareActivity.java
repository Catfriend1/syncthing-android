package com.nutomic.syncthingandroid.activities;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.ConfigXml.OpenConfigException;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED;
import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

/**
 * Shares incoming files to syncthing folders.
 * <p>
 * {@link #getDisplayNameForUri} and {@link #getDisplayNameFromContentResolver} are taken from
 * ownCloud Android {@see https://github.com/owncloud/android/blob/master/owncloudApp/src/main/java/com/owncloud/android/utils/UriUtils.java}
 */
public class ShareActivity extends SyncthingActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "ShareActivity";

    private Boolean ENABLE_VERBOSE_LOG = false;

    private static final String PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER = "previously_selected_syncthing_folder";

    public static final String PREF_FOLDER_SAVED_SUBDIRECTORY = "saved_sub_directory_";

    private ConfigRouter mConfig;

    private Spinner mFoldersSpinner;

    private SyncthingService mSyncthingService = null;

    private TextView mSubDirectoryTextView;

    @Inject SharedPreferences mPreferences;

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = (SyncthingService) syncthingServiceBinder.getService();
        syncthingService.registerOnServiceStateChangeListener(ShareActivity.this);
        mSyncthingService = syncthingService;
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        List<Folder> folders = null;
        try {
            folders = mConfig.getFolders(getApi());
        } catch (OpenConfigException e) {
            Toast.makeText(this, getString(R.string.complete_welcome_wizard_first), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Get the index of the previously selected folder.
        int folderIndex = 0;
        String savedFolderId = mPreferences
                .getString(PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER, "");
        for (Folder folder : folders) {
            if (folder.id.equals(savedFolderId)) {
                folderIndex = folders.indexOf(folder);
                break;
            }
        }

        ArrayAdapter<Folder> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, folders);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = findViewById(R.id.folders);
        sItems.setAdapter(adapter);
        sItems.setSelection(folderIndex);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        mConfig = new ConfigRouter(ShareActivity.this);
        setContentView(R.layout.activity_share);

        getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Button mShareButton = findViewById(R.id.share_button);
        Button mCancelButton = findViewById(R.id.cancel_button);
        Button browseButton = findViewById(R.id.browse_button);
        EditText mShareName = findViewById(R.id.name);
        TextView mShareTitle = findViewById(R.id.namesTitle);

        mSubDirectoryTextView = findViewById(R.id.sub_directory_Textview);
        mFoldersSpinner = findViewById(R.id.folders);

        ArrayList<Uri> extrasToCopy = new ArrayList<>();
        String action = getIntent().getAction();
        if (action != null) {
            if (action.equals(Intent.ACTION_SEND)) {
                try {
                    Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
                    if (uri != null) {
                        extrasToCopy.add(uri);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Intent.ACTION_SEND: Ignored malformed intent.");
                }
            } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                try {
                    ArrayList<Uri> extras = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (extras != null) {
                        extrasToCopy = extras;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Intent.ACTION_SEND_MULTIPLE: Ignored malformed intent.");
                }
            }
        }

        if (extrasToCopy.isEmpty()) {
            Toast.makeText(this, getString(R.string.nothing_share), Toast.LENGTH_SHORT).show();
            finish();
        }

        Map<Uri, String> files = new HashMap<>();
        for (Uri sourceUri : extrasToCopy) {
            String displayName = getDisplayNameForUri(sourceUri);
            if (displayName == null) {
                displayName = generateDisplayName();
            }
            files.put(sourceUri, displayName);
        }

        mShareName.setText(TextUtils.join("\n", files.values()));
        if (files.size() > 1) {
            mShareName.setFocusable(false);
            mShareName.setKeyListener(null);
        }
        mShareTitle.setText(getResources().getQuantityString(R.plurals.file_name_title,
                files.size() > 1 ? 2 : 1));
        mShareButton.setOnClickListener(view -> {
            if (files.size() == 1) {
                files.entrySet().iterator().next().setValue(mShareName.getText().toString());
            }
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            if (folder == null) {
                Log.e(TAG, "onCreate-OnClickListener: folder == null");
                return;
            }
            if (folder.path == null) {
                Log.e(TAG, "onCreate-OnClickListener: folder.path == null");
                return;
            }
            File directory = new File(folder.path, getSavedSubDirectory());
            boolean allowOverwrite = mPreferences.getBoolean(Constants.PREF_ALLOW_OVERWRITE_FILES, false);
            CopyFilesTask mCopyFilesTask = new CopyFilesTask(this, files, folder, directory, allowOverwrite);
            mCopyFilesTask.execute();
        });

        mFoldersSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSubDirectoryTextView.setText(getSavedSubDirectory());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        browseButton.setOnClickListener(view -> {
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            File initialDirectory = new File(folder.path, getSavedSubDirectory());
            Intent intent = FolderPickerActivity.createIntent(
                    getApplicationContext(),
                    initialDirectory.getAbsolutePath(), folder.path
            );
            startActivityForResult(intent, FolderPickerActivity.DIRECTORY_REQUEST_CODE);
        });

        mCancelButton.setOnClickListener(view -> finish());
        mSubDirectoryTextView.setText(getSavedSubDirectory());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFoldersSpinner.getSelectedItem() != null) {
            Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
            mPreferences.edit()
                    .putString(PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER, selectedFolder.id)
                    .apply();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE && resultCode == RESULT_OK) {
            Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
            String folderDirectory = Util.formatPath(selectedFolder.path);
            String subDirectory = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            //Remove the parent directory from the string, so it is only the Sub directory that is displayed to the user.
            subDirectory = subDirectory.replace(folderDirectory, "");
            mSubDirectoryTextView.setText(subDirectory);

            mPreferences
                    .edit().putString(PREF_FOLDER_SAVED_SUBDIRECTORY + selectedFolder.id, subDirectory)
                    .apply();
        }
    }

    @Override
    protected void onDestroy() {
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(ShareActivity.this);
        }
        super.onDestroy();
    }

    /**
     * Generate file name for new file.
     */
    private String generateDisplayName() {
        Date date = new Date(System.currentTimeMillis());
        DateFormat df = DateFormat.getDateTimeInstance();
        return String.format(getResources().getString(R.string.file_name_template),
                df.format(date));
    }

    /**
     * Get file name from uri.
     */
    private String getDisplayNameForUri(Uri uri) {
        String displayName;

        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            displayName = uri.getLastPathSegment();
        } else {
            displayName = getDisplayNameFromContentResolver(uri);
            if (displayName == null) {
                // last chance to have a name
                displayName = uri.getLastPathSegment().replaceAll("\\s", "");
            }

            // Add best possible extension
            int index = displayName.lastIndexOf(".");
            if (index == -1 || MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(displayName.substring(index + 1)) == null) {
                String mimeType = this.getContentResolver().getType(uri);
                String extension = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(mimeType);
                if (extension != null) {
                    displayName += "." + extension;
                }
            }
        }

        // Replace path separator characters to avoid inconsistent paths
        return displayName != null ? displayName.replaceAll("/", "-") : null;
    }

    /**
     * Get file name from content uri (content://).
     */
    private String getDisplayNameFromContentResolver(Uri uri) {
        String displayName = null;
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            String displayNameColumn;
            if (mimeType.startsWith("image/")) {
                displayNameColumn = MediaStore.Images.ImageColumns.DISPLAY_NAME;
            } else if (mimeType.startsWith("video/")) {
                displayNameColumn = MediaStore.Video.VideoColumns.DISPLAY_NAME;

            } else if (mimeType.startsWith("audio/")) {
                displayNameColumn = MediaStore.Audio.AudioColumns.DISPLAY_NAME;

            } else {
                displayNameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME;
            }

            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(
                        uri,
                        new String[]{displayNameColumn},
                        null,
                        null,
                        null
                );
                if (cursor != null) {
                    cursor.moveToFirst();
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow(displayNameColumn));
                }

            } catch (Exception e) {
                Log.e(TAG, "Could not retrieve display name for " + uri.toString());
                // nothing else, displayName keeps null

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return displayName;
    }

    /**
     * Get the previously selected sub directory for the currently selected Syncthing folder.
     */
    private String getSavedSubDirectory() {
        Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
        String savedSubDirectory = "";

        if (selectedFolder != null) {
            savedSubDirectory = mPreferences
                    .getString(PREF_FOLDER_SAVED_SUBDIRECTORY + selectedFolder.id, "");
        }

        return savedSubDirectory;
    }

    private static class CopyFilesTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<ShareActivity> refShareActivity;
        private ProgressDialog mProgress;
        private final Map<Uri, String> mFiles;
        private final Folder mFolder;
        private final File mDirectory;
        private final boolean mAllowOverwrite;
        private int mCopied = 0, mIgnored = 0;

        CopyFilesTask(ShareActivity context, Map<Uri, String> files, Folder folder, File directory, boolean allowOverwrite) {
            refShareActivity = new WeakReference<>(context);
            this.mFiles = files;
            this.mFolder = folder;
            this.mDirectory = directory;
            this.mAllowOverwrite = allowOverwrite;
        }

        protected void onPreExecute() {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            // shareActivity cannot be null before the task executes.
            mProgress = ProgressDialog.show(shareActivity, null,
                    shareActivity.getString(R.string.copy_progress), true);
        }

        protected Boolean doInBackground(Void... params) {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            if (shareActivity == null || shareActivity.isFinishing()) {
                cancel(true);
                return true;
            }

            boolean isError = false;
            for (Map.Entry<Uri, String> entry : mFiles.entrySet()) {
                InputStream inputStream = null;
                try {
                    File outFile = new File(mDirectory, entry.getValue());
                    if (outFile.isFile() && !mAllowOverwrite) {
                        mIgnored++;
                        continue;
                    }
                    inputStream = shareActivity.getContentResolver().openInputStream(entry.getKey());
                    Files.asByteSink(outFile).writeFrom(inputStream);
                    mCopied++;
                } catch (FileNotFoundException e) {
                    Log.e(TAG, String.format("Can't find input file \"%s\" to copy",
                            entry.getKey()), e);
                    isError = true;
                } catch (IOException e) {
                    Log.e(TAG, String.format("IO exception during file \"%s\" sharing",
                            entry.getKey()), e);
                    isError = true;
                } finally {
                    try {
                        if (inputStream != null)
                            inputStream.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Exception on input/output stream close", e);
                    }
                }
            }
            return isError;
        }

        protected void onPostExecute(Boolean isError) {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            if (shareActivity == null || shareActivity.isFinishing()) {
                return;
            }
            Util.dismissDialogSafe(mProgress, shareActivity);

            if (isError) {
                Toast.makeText(shareActivity, shareActivity.getString(R.string.copy_exception),
                        Toast.LENGTH_SHORT).show();
                shareActivity.finish();
                return;
            }

            Toast.makeText(shareActivity, mIgnored > 0 ?
                            shareActivity.getResources().getQuantityString(R.plurals.copy_success_partially, mCopied,
                                    mCopied, mFolder.label, mIgnored) :
                            shareActivity.getResources().getQuantityString(R.plurals.copy_success, mCopied, mCopied,
                                    mFolder.label),
                    Toast.LENGTH_LONG).show();

            shareActivity.afterCopyFilesTask();
        }
    }

    private void afterCopyFilesTask() {
        // Check if feature "Sync according to time schedule" is enabled.
        boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);
        if (prefRunOnTimeSchedule) {
            // Notify {@link RunConditionMonitor} to start SyncthingNative immediately for a certain amount of time.
            LogV("prefRunOnTimeSchedule=true, notifying RunConditionMonitor via intent");
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
            Intent intent = new Intent(ACTION_SYNC_TRIGGER_FIRED);
            intent.putExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
            localBroadcastManager.sendBroadcast(intent);
        }

        this.finish();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
