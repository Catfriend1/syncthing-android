package com.nutomic.syncthingandroid.fragments.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.fragments.NumberPickerFragment;

import java.util.concurrent.TimeUnit;


/**
 * Contains the configuration options for Staggered file versioning.
 */

public class StaggeredVersioningFragment extends Fragment {

    private View mView;

    private Bundle mArguments;

    private TextView mPathView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_staggered_versioning, container, false);
        mArguments = getArguments();
        fillArguments();
        updateNumberPicker();
        initiateVersionsPathTextView();
        return mView;
    }

    private void fillArguments() {
        if (missingParameters()) {
            mArguments.putString("maxAge", "0");
            mArguments.putString("versionsPath", "");
        }
    }

    private boolean missingParameters() {
        return !mArguments.containsKey("maxAge");
    }

    //The maxAge parameter is displayed in days but stored in seconds since Syncthing needs it in seconds.
    //A NumberPickerFragment is nested in the fragment_staggered_versioning layout, the values for it are update below.
    private void updateNumberPicker() {
        NumberPickerFragment numberPicker = (NumberPickerFragment) getChildFragmentManager().findFragmentByTag("numberpicker_staggered_versioning");
        numberPicker.updateNumberPicker(100, 0, getMaxAgeInDays());
        numberPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updatePreference("maxAge", (String.valueOf(TimeUnit.DAYS.toSeconds(newVal)))));
    }

    private void initiateVersionsPathTextView() {
        mPathView = mView.findViewById(R.id.directoryTextView);
        String currentPath = getVersionsPath();

        mPathView.setText(currentPath);
        mPathView.setOnClickListener(view ->
            startActivityForResult(FolderPickerActivity.createIntent(getContext(), currentPath, null), FolderPickerActivity.DIRECTORY_REQUEST_CODE));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE ) {
            updatePath(data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY));
        }
    }

    private void updatePath(String directory) {
        mPathView.setText(directory);
        updatePreference("versionsPath", directory);
    }

    private String getVersionsPath() {
        return mArguments.getString("versionsPath");
    }

    private void updatePreference(String key, String newValue) {
        getArguments().putString(key, newValue);
    }

    private int getMaxAgeInDays() {
        return (int) TimeUnit.SECONDS.toDays(Long.valueOf(mArguments.getString("maxAge")));
    }
}
