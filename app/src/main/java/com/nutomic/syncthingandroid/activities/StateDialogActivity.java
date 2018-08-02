package com.nutomic.syncthingandroid.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.view.View;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.DialogLoadingBinding;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingService.State;
import com.nutomic.syncthingandroid.util.Util;

import java.util.concurrent.TimeUnit;

/**
 * Handles loading/disabled dialogs.
 */
public abstract class StateDialogActivity extends SyncthingActivity {

    private static final long SLOW_LOADING_TIME = TimeUnit.SECONDS.toMillis(30);

    private State mServiceState = State.INIT;
    private AlertDialog mLoadingDialog;
    private AlertDialog mDisabledDialog;
    private boolean mIsPaused = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerOnServiceConnectedListener(() ->
                getService().registerOnServiceStateChangeListener(this::onServiceStateChange));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsPaused = false;
        switch (mServiceState) {
            case DISABLED:
                showDisabledDialog();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
        dismissDisabledDialog();
        dismissLoadingDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getService() != null) {
            getService().unregisterOnServiceStateChangeListener(this::onServiceStateChange);
        }
        dismissDisabledDialog();
    }

    private void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        switch (mServiceState) {
            case INIT: // fallthrough
            case STARTING:
                dismissDisabledDialog();
                showLoadingDialog();
                break;
            case ACTIVE:
                dismissDisabledDialog();
                dismissLoadingDialog();
                break;
            case DISABLED:
                if (!mIsPaused) {
                    showDisabledDialog();
                }
                break;
            case ERROR: // fallthrough
            default:
                break;
        }
    }

    private void showDisabledDialog() {
        if (this.isFinishing() && (mDisabledDialog != null)) {
            return;
        }
        mDisabledDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        (dialogInterface, i) -> {
                            Intent intent = new Intent(this, SettingsActivity.class);
                            intent.putExtra(SettingsActivity.EXTRA_OPEN_SUB_PREF_SCREEN, "category_run_conditions");
                            startActivity(intent);
                        }
                )
                .setNegativeButton(R.string.exit,
                        (dialogInterface, i) -> ActivityCompat.finishAffinity(this)
                )
                .setCancelable(false)
                .show();
    }

    private void dismissDisabledDialog() {
        Util.dismissDialogSafe(mDisabledDialog, this);
        mDisabledDialog = null;
    }

    /**
     * Shows the loading dialog with the correct text ("creating keys" or "loading").
     */
    private void showLoadingDialog() {
        if (mIsPaused || mLoadingDialog != null)
            return;

        DialogLoadingBinding binding = DataBindingUtil.inflate(
                getLayoutInflater(), R.layout.dialog_loading, null, false);
        boolean isGeneratingKeys = getIntent().getBooleanExtra(EXTRA_KEY_GENERATION_IN_PROGRESS, false);
        binding.loadingText.setText((isGeneratingKeys)
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        mLoadingDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();

        if (!isGeneratingKeys) {
            new Handler().postDelayed(() -> {
                if (this.isFinishing() || mLoadingDialog == null)
                    return;

                binding.loadingSlowMessage.setVisibility(View.VISIBLE);
                binding.viewLogs.setOnClickListener(v ->
                        startActivity(new Intent(this, LogActivity.class)));
            }, SLOW_LOADING_TIME);
        }
    }

    private void dismissLoadingDialog() {
        Util.dismissDialogSafe(mLoadingDialog, this);
        mLoadingDialog = null;
    }
}
