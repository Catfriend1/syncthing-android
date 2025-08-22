package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.nutomic.syncthingandroid.R;

import java.util.Arrays;
import java.util.List;

public class PullOrderDialogActivity extends ThemedAppCompatActivity {

    public static final String EXTRA_PULL_ORDER = ".activities.PullOrderDialogActivity.PULL_ORDER";
    public static final String EXTRA_RESULT_PULL_ORDER = ".activities.PullOrderDialogActivity.EXTRA_RESULT_PULL_ORDER";

    private String selectedType;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            saveConfiguration();
            finish();
        }
    };

    private static final List<String> mTypes = Arrays.asList(
        "random",
        "alphabetic",
        "smallestFirst",
        "largestFirst",
        "oldestFirst",
        "newestFirst"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_pullorder_dialog);
        if (savedInstanceState == null) {
            selectedType = getIntent().getStringExtra(EXTRA_PULL_ORDER);
        }
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
        intent.putExtra(EXTRA_RESULT_PULL_ORDER, selectedType);
        setResult(AppCompatActivity.RESULT_OK, intent);
    }

    private void initiateSpinner() {
        Spinner pullOrderTypeSpinner = findViewById(R.id.pullOrderTypeSpinner);
        pullOrderTypeSpinner.setSelection(mTypes.indexOf(selectedType));
        pullOrderTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mTypes.indexOf(selectedType)) {
                    selectedType = mTypes.get(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // This is not allowed.
            }
        });
    }
}
