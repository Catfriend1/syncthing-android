package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.dialog.ExternalVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.NoVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.SimpleVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.StaggeredVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.TrashCanVersioningFragment;

import java.util.Arrays;
import java.util.List;

public class VersioningDialogActivity extends ThemedAppCompatActivity {

    private Fragment mCurrentFragment;

    private static final List<String> mTypes = Arrays.asList("none", "trashcan", "simple", "staggered", "external");

    private Bundle mArguments;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            saveConfiguration();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_versioning_dialog);

        if (savedInstanceState != null) {
            mArguments = savedInstanceState.getBundle("arguments");
        } else {
            mArguments = getIntent().getExtras();
        }

        updateFragmentView(mTypes.indexOf(getIntent().getExtras().getString("type")));
        initiateFinishBtn();
        initiateSpinner();

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    private void initiateFinishBtn() {
        Button finishBtn = findViewById(R.id.finish_btn);
        finishBtn.setOnClickListener(v -> {
            saveConfiguration();
            finish();
        });
    }

    private void saveConfiguration() {
        Intent intent = new Intent();
        intent.putExtras(mCurrentFragment.getArguments());
        setResult(AppCompatActivity.RESULT_OK, intent);
    }

    private void initiateSpinner() {
        Spinner versioningTypeSpinner = findViewById(R.id.versioningTypeSpinner);
        versioningTypeSpinner.setSelection(mTypes.indexOf(getIntent().getExtras().getString("type")));
        versioningTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mTypes.indexOf(getIntent().getExtras().getString("type"))) {
                    updateVersioningType(position);
                    updateFragmentView(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void updateVersioningType(int position) {
        mArguments.putString("type", mTypes.get(position));
    }

    private void updateFragmentView(int selection) {
        if (mCurrentFragment != null){
            mArguments = mCurrentFragment.getArguments();
        }

        mCurrentFragment = getFragment(selection);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // This Activity (VersioningDialogActivity) contains all the file versioning parameters that have been passed from the FolderActivity in the intent extras, so we simply
        // pass that to the currentfragment.
        mCurrentFragment.setArguments(mArguments);
        transaction.replace(R.id.versioningFragmentContainer, mCurrentFragment);
        transaction.commit();
    }

    private Fragment getFragment(int selection) {
        Fragment fragment = null;

        switch (selection) {
            case 0:
                fragment = new NoVersioningFragment();
                break;
            case 1:
                fragment = new TrashCanVersioningFragment();
                break;
            case 2:
                fragment = new SimpleVersioningFragment();
                break;
            case 3:
                fragment = new StaggeredVersioningFragment();
                break;
            case 4:
                fragment = new ExternalVersioningFragment();
                break;
        }

        return fragment;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("arguments", mCurrentFragment.getArguments());
    }
}
