package com.nutomic.syncthingandroid.fragments;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import com.nutomic.syncthingandroid.R;

/**
 * Simply displays a numberpicker and allows easy access to configure it with the public functions.
 */

public class NumberPickerFragment extends Fragment {

    private NumberPicker mNumberPicker;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mNumberPicker = (NumberPicker) inflater.inflate(R.layout.numberpicker_fragment, container, false);
        mNumberPicker.setWrapSelectorWheel(false);

        return mNumberPicker;
    }

    public void setOnValueChangedListener(NumberPicker.OnValueChangeListener onValueChangeListener){
        mNumberPicker.setOnValueChangedListener(onValueChangeListener);
    }

    public void updateNumberPicker(int maxValue, int minValue, int currentValue){
        mNumberPicker.setMaxValue(maxValue);
        mNumberPicker.setMinValue(minValue);
        mNumberPicker.setValue(currentValue);
    }
}
