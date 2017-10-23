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
import com.nutomic.syncthingandroid.util.Util;

import java.util.concurrent.TimeUnit;

/**
 * Handles loading/disabled dialogs.
 */
public abstract class StateDialogActivity extends SyncthingActivity {

    private static final long SLOW_LOADING_TIME = TimeUnit.SECONDS.toMillis(30);

    private AlertDialog mLoadingDialog;
    private AlertDialog mDisabledDialog;
    private boolean mIsPaused = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerOnServiceConnectedListener(() ->
                getService().registerOnApiChangeListener(this::onApiChange));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsPaused = false;
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
            getService().unregisterOnApiChangeListener(this::onApiChange);
        }
        dismissDisabledDialog();
    }

    private void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
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
                dismissLoadingDialog();
                if (!isFinishing()) {
                    showDisabledDialog();
                }
                break;
        }
    }

    private void showDisabledDialog() {
        if (mIsPaused)
            return;

        mDisabledDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        (dialogInterface, i) -> {
                            finish();
                            startActivity(new Intent(this, SettingsActivity.class));
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
        boolean isFirstStart = getIntent().getBooleanExtra(EXTRA_FIRST_START, false);
        binding.loadingText.setText((isFirstStart)
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        mLoadingDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();

        if (!isFirstStart) {
            new Handler().postDelayed(() -> {
                if (isFinishing() || mLoadingDialog == null)
                    return;

                binding.loadingSlowMessage.setVisibility(View.VISIBLE);
                binding.viewLogs.setOnClickListener(v -> {
                    startActivity(new Intent(this, LogActivity.class));
                });
            }, SLOW_LOADING_TIME);
        }
    }

    private void dismissLoadingDialog() {
        Util.dismissDialogSafe(mLoadingDialog, this);
        mLoadingDialog = null;
    }
}
