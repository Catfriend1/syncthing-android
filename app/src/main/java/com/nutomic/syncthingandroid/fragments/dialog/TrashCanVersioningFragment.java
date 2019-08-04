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
 * Contains the configuration options for trashcan file versioning.
 */

public class TrashCanVersioningFragment extends Fragment {

    private Bundle mArguments;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trashcan_versioning, container, false);
        mArguments = getArguments();
        fillArguments();
        updateNumberPicker();
        return view;
    }

    private void fillArguments() {
        if (missingParameters()) {
            mArguments.putString("cleanoutDays", "0");
        }
    }

    private boolean missingParameters() {
        return !mArguments.containsKey("cleanoutDays");
    }

    //a NumberPickerFragment is nested in the fragment_trashcan_versioning layout, the values for it are update below.
    private void updateNumberPicker() {
        NumberPickerFragment numberPicker = (NumberPickerFragment) getChildFragmentManager().findFragmentByTag("numberpicker_trashcan_versioning");
        numberPicker.updateNumberPicker(100, 0, getCleanoutDays());
        numberPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateCleanoutDays((String.valueOf(newVal))));
    }

    private int getCleanoutDays() {
        return Integer.valueOf(mArguments.getString("cleanoutDays"));
    }

    private void updateCleanoutDays(String newValue) {
        mArguments.putString("cleanoutDays", newValue);
    }
}
