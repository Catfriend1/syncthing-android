package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.text.InputType;

import com.nutomic.syncthingandroid.R;

/**
 * Custom EditTextPreference that displays a password field with toggle visibility functionality
 */
public class PasswordPreference extends EditTextPreference {
    private boolean mPasswordVisible = false;
    private String mPasswordValue = "";
    private ImageButton mToggleButton;

    public PasswordPreference(Context context) {
        super(context);
        init();
    }

    public PasswordPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PasswordPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_password_with_toggle);
        // Set password input type for the dialog
        getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        setupToggleButton(view);
        setupClickListener(view);
        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        setupToggleButton(view);
        setupClickListener(view);
        // Initialize password value and update summary
        mPasswordValue = getText() != null ? getText() : "";
        updateSummary();
    }

    private void setupToggleButton(View view) {
        mToggleButton = view.findViewById(R.id.password_visibility_toggle);
        if (mToggleButton != null) {
            mToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePasswordVisibility();
                }
            });
            updateToggleButton();
        }
    }

    private void setupClickListener(View view) {
        // Find the main preference area (LinearLayout containing title and summary)
        View preferenceArea = view.findViewById(R.id.preference_main_area);
        if (preferenceArea != null) {
            preferenceArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Trigger the preference's onClick behavior to show the edit dialog
                    if (isEnabled()) {
                        showDialog(null);
                    }
                }
            });
        }
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        mPasswordValue = text != null ? text : "";
        updateSummary();
    }

    private void togglePasswordVisibility() {
        mPasswordVisible = !mPasswordVisible;
        updateToggleButton();
        updateSummary();
    }

    private void updateToggleButton() {
        if (mToggleButton != null) {
            if (mPasswordVisible) {
                mToggleButton.setImageResource(R.drawable.ic_visibility_24dp);
            } else {
                mToggleButton.setImageResource(R.drawable.ic_visibility_off_24dp);
            }
        }
    }

    private void updateSummary() {
        if (mPasswordValue.isEmpty()) {
            setSummary(getContext().getString(R.string.backup_password_not_set));
        } else {
            if (mPasswordVisible) {
                setSummary(getContext().getString(R.string.backup_password_set, mPasswordValue));
            } else {
                setSummary(getContext().getString(R.string.backup_password_set_masked));
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            mPasswordValue = getText() != null ? getText() : "";
            updateSummary();
        }
    }
}