package com.nutomic.syncthingandroid.fragments.dialog;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.NumberPickerFragment;

/**
 * Contains the configuration options for simple file versioning.
 */

public class SimpleVersioningFragment extends Fragment {

    private Bundle mArguments;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_simple_versioning, container, false);
        mArguments = getArguments();
        fillArguments();
        updateNumberPicker();
        return view;
    }

    private void fillArguments() {
        if (!mArguments.containsKey("keep")){
            mArguments.putString("keep", "5");
        }
        if (!mArguments.containsKey("cleanoutDays")){
            mArguments.putString("cleanoutDays", "0");
        }
    }

    //a NumberPickerFragment is nested in the fragment_simple_versioning layout, the values for it are update below.
    private void updateNumberPicker() {
        NumberPickerFragment numberPicker;

        numberPicker = (NumberPickerFragment) getChildFragmentManager().findFragmentByTag("numberpicker_simple_versioning_keep");
        numberPicker.updateNumberPicker(100000, 1, Integer.valueOf(mArguments.getString("keep")));
        numberPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateKeepVersions((String.valueOf(newVal))));

        numberPicker = (NumberPickerFragment) getChildFragmentManager().findFragmentByTag("numberpicker_simple_versioning_cleanoutdays");
        numberPicker.updateNumberPicker(100, 0, Integer.valueOf(mArguments.getString("cleanoutDays")));
        numberPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateCleanoutDays((String.valueOf(newVal))));
    }

    private void updateCleanoutDays(String newValue) {
        mArguments.putString("cleanoutDays", newValue);
    }

    private void updateKeepVersions(String newValue) {
        mArguments.putString("keep", newValue);
    }

}
