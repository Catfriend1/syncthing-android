package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.common.collect.Sets;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

/**
 * Activity that allows selecting a directory in the local file system.
 */
public class FolderPickerActivity extends SyncthingActivity
        implements AdapterView.OnItemClickListener {

    private static final String TAG = "FolderPickerActivity";

    private static final String EXTRA_INITIAL_DIRECTORY =
            "com.github.catfriend1.syncthingandroid.activities.FolderPickerActivity.INITIAL_DIRECTORY";

    /**
     * If requested by {@link #createIntent}, we'll only use one root dir and enforce
     * the user stays within that. {@link #populateRoots} will respect this extra.
     * See issue #366.
     */
    private static final String EXTRA_ROOT_DIRECTORY =
            "com.github.catfriend1.syncthingandroid.activities.FolderPickerActivity.ROOT_DIRECTORY";

    public static final String EXTRA_RESULT_DIRECTORY =
            "com.github.catfriend1.syncthingandroid.activities.FolderPickerActivity.RESULT_DIRECTORY";

    public static final int DIRECTORY_REQUEST_CODE = 234;

    private ListView mListView;
    private FileAdapter mFilesAdapter;
    private RootsAdapter mRootsAdapter;

    /**
     * Location of null means that the list of roots is displayed.
     */
    private File mLocation;

    public static Intent createIntent(Context context, String initialDirectory, @Nullable String rootDirectory) {
        Intent intent = new Intent(context, FolderPickerActivity.class);

        if (!TextUtils.isEmpty(initialDirectory)) {
            intent.putExtra(EXTRA_INITIAL_DIRECTORY, initialDirectory);
        }

        if (!TextUtils.isEmpty(rootDirectory)) {
            intent.putExtra(EXTRA_ROOT_DIRECTORY, rootDirectory);
        }

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        setContentView(R.layout.activity_folder_picker);
        mListView = findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setEmptyView(findViewById(android.R.id.empty));
        mFilesAdapter = new FileAdapter(this);
        mRootsAdapter = new RootsAdapter(this);
        mListView.setAdapter(mFilesAdapter);

        populateRoots();
        if (getIntent().hasExtra(EXTRA_INITIAL_DIRECTORY)) {
            String initialDirectory = getIntent().getStringExtra(EXTRA_INITIAL_DIRECTORY);
            displayFolder(new File(initialDirectory));
            return;
        }
        displayRoot();
    }

    /**
     * If a root directory is specified it is added to {@link #mRootsAdapter} otherwise
     * all available storage devices/folders from various APIs are inserted into
     * {@link #mRootsAdapter}.
     */
    @SuppressLint("NewApi")
    private void populateRoots() {
        ArrayList<File> roots = new ArrayList<>();
        String rootDir = getIntent().getStringExtra(EXTRA_ROOT_DIRECTORY);
        if (getIntent().hasExtra(EXTRA_ROOT_DIRECTORY) && !TextUtils.isEmpty(rootDir)) {
            roots.add(new File(rootDir));
        } else {
            roots.addAll(Arrays.asList(getExternalFilesDirs(null)));
            roots.remove(getExternalFilesDir(null));
            roots.remove(null);      // getExternalFilesDirs may return null for an ejected SDcard.
            roots.add(Environment.getExternalStorageDirectory());
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));

            // Add paths where we might have read-only access.
            Collections.addAll(roots, new File("/storage/").listFiles());
            roots.add(new File("/"));
        }
        // Remove any invalid directories.
        Iterator<File> it = roots.iterator();
        while (it.hasNext()) {
            File f = it.next();
            if (f == null || !f.exists() || !f.isDirectory()) {
                it.remove();
            }
        }

        mRootsAdapter.addAll(Sets.newTreeSet(roots));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mListView.getAdapter() == mRootsAdapter)
            return true;

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.folder_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create_folder:
                final EditText et = new EditText(this);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.create_folder)
                        .setView(et)
                        .setPositiveButton(android.R.string.ok,
                                (dialogInterface, i) -> createFolder(et.getText().toString())
                        )
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
                dialog.setOnShowListener(dialogInterface -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(et, InputMethodManager.SHOW_IMPLICIT));
                dialog.show();
                return true;
            case R.id.folder_go_up:
                if (canGoUpToSubDir() || canGoUpToRootDir()) {
                    goUpToParentDir();
                }
                return true;
            case R.id.select:
                Intent intent = new Intent()
                        .putExtra(EXTRA_RESULT_DIRECTORY, Util.formatPath(mLocation.getAbsolutePath()));
                setResult(Activity.RESULT_OK, intent);
                finish();
                return true;
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public Boolean canGoUpToSubDir() {
        return mLocation != null && !mRootsAdapter.contains(mLocation);
    }

    public Boolean canGoUpToRootDir() {
        return mRootsAdapter.contains(mLocation) && mRootsAdapter.getCount() > 1;
    }

    public void goUpToParentDir() {
        if (canGoUpToSubDir()) {
            displayFolder(mLocation.getParentFile());
            return;
        }
        if (canGoUpToRootDir()) {
            displayRoot();
            return;
        }
        Log.e(TAG, "goUpToParentDir: Cannot go up.");
    }

    /**
     * Creates a new folder with the given name and enters it.
     */
    private void createFolder(String name) {
        File newFolder = new File(mLocation, name);
        if (newFolder.mkdir()) {
            displayFolder(newFolder);
        } else {
            Toast.makeText(this, R.string.create_folder_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refreshes the ListView to show the contents of the folder in {@code }mLocation.peek()}.
     */
    private void displayFolder(File folder) {
        mLocation = folder;
        mFilesAdapter.clear();
        File[] contents = mLocation.listFiles();
        // In case we don't have read access to the folder, just display nothing.
        if (contents == null)
            contents = new File[]{};

        Arrays.sort(contents, (f1, f2) -> {
            if (f1.isDirectory() && f2.isFile())
                return -1;
            if (f1.isFile() && f2.isDirectory())
                return 1;
            return f1.getName().compareTo(f2.getName());
        });

        for (File f : contents) {
            mFilesAdapter.add(f);
        }
        mListView.setAdapter(mFilesAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        @SuppressWarnings("unchecked")
        ArrayAdapter<File> adapter = (ArrayAdapter<File>) mListView.getAdapter();
        File f = adapter.getItem(i);
        if (f.isDirectory()) {
            displayFolder(f);
            invalidateOptions();
        }
    }

    private void invalidateOptions() {
        invalidateOptionsMenu();
    }

    private class FileAdapter extends ArrayAdapter<File> {

        public FileAdapter(Context context) {
            super(context, R.layout.item_folder_picker);
        }

        @Override
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            TextView title = convertView.findViewById(android.R.id.text1);
            File f = getItem(position);
            title.setText(f.getName());
            int textColor = (f.isDirectory())
                    ? android.R.color.primary_text_light
                    : android.R.color.tertiary_text_light;
            title.setTextColor(ContextCompat.getColor(getContext(), textColor));

            return convertView;
        }
    }

    private class RootsAdapter extends ArrayAdapter<File> {

        public RootsAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);
        }

        @Override
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            TextView title = convertView.findViewById(android.R.id.text1);
            title.setText(getItem(position).getAbsolutePath());
            return convertView;
        }

        public boolean contains(File file) {
            for (int i = 0; i < getCount(); i++) {
                if (getItem(i).equals(file))
                    return true;
            }
            return false;
        }
    }

    /**
     * Goes up a directory, up to the list of roots if there are multiple roots.
     * <p>
     * If we already are in the list of roots, or if we are directly in the only
     * root folder, we cancel.
     */
    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    /**
     * Displays a list of all available roots, or if there is only one root, the
     * contents of that folder.
     */
    private void displayRoot() {
        mFilesAdapter.clear();
        if (mRootsAdapter.getCount() == 1) {
            displayFolder(mRootsAdapter.getItem(0));
        } else {
            mListView.setAdapter(mRootsAdapter);
            mLocation = null;
        }
        invalidateOptions();
    }

}
