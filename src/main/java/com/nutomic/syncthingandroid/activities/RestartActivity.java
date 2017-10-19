package com.nutomic.syncthingandroid.activities;

import android.app.AlertDialog;
import android.os.Bundle;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.NotificationHandler;

/**
 * Shows restart dialog.
 *
 * The user can choose to restart Syncthing immediately. Otherwise, a restart notification is
 * displayed.
 */
public class RestartActivity extends SyncthingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.restart_title)
                .setPositiveButton(R.string.restart_now, (dialogInterface, i) -> {
                    getService().getApi().restart();
                    finish();
                })
                .setNegativeButton(R.string.restart_later, (dialogInterface, i) -> {
                    createRestartNotification();
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    createRestartNotification();
                    finish();
                })
                .show();
    }

    /**
     * Creates a notification prompting the user to restart the app.
     */
    private void createRestartNotification() {
        new NotificationHandler(getService()).showRestartNotification();
        getApi().setRestartPostponed();
    }

}
