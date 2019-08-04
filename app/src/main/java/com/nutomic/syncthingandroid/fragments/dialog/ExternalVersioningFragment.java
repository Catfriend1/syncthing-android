package com.nutomic.syncthingandroid.fragments.dialog;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;

/**
 * Contains the configuration options for external file versioning.
 */

public class ExternalVersioningFragment extends Fragment {

    private View mView;

    private Bundle mArguments;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_external_versioning, container, false);
        mArguments = getArguments();
        fillArguments();
        initTextView();
        return mView;
    }

    private void fillArguments() {
        if (missingParameters()){
            mArguments.putString("command", "");
        }
    }

    private boolean missingParameters() {
        return !mArguments.containsKey("command");
    }

    private void initTextView() {
        TextView commandTextView = mView.findViewById(R.id.commandTextView);

        commandTextView.setText(getCommand());
        commandTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence command, int start, int before, int count) {
                updateCommand(command.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private void updateCommand(String command) {
        mArguments.putString("command", command);
    }

    private String getCommand() {
        return mArguments.containsKey("command") ? mArguments.getString("command") : "" ;
    }
}
