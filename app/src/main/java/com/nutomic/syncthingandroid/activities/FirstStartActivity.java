package com.nutomic.syncthingandroid.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.SyncthingService;

import javax.inject.Inject;

public class FirstStartActivity extends Activity implements Button.OnClickListener {

    private static final int REQUEST_WRITE_STORAGE = 142;

    @Inject SharedPreferences mPreferences;

    /**
     * Handles activity behaviour depending on {@link #isFirstStart()} and {@link #haveStoragePermission()}.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        startService(new Intent(this, SyncthingService.class));

        if (!isFirstStart()) {
            if (haveStoragePermission()) {
                startApp();
                return;
            }
            else {
                requestStoragePermission();
            }
        }

        setContentView(R.layout.activity_first_start);
        Button cont = findViewById(R.id.cont);
        cont.setOnClickListener(this);
    }

    private boolean isFirstStart() {
        return mPreferences.getBoolean("first_start", true);
    }

    private void startApp() {
        boolean isFirstStart = isFirstStart();
        if (isFirstStart) {
            mPreferences.edit().putBoolean("first_start", false).apply();
        }

        // In case start_into_web_gui option is enabled, start both activities so that back
        // navigation works as expected.
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_FIRST_START, isFirstStart);
        Intent webIntent = new Intent(this, WebGuiActivity.class);
        if (mPreferences.getBoolean("start_into_web_gui", false)) {
            startActivities(new Intent[] {mainIntent, webIntent});
        } else {
            startActivity(mainIntent);
        }
        finish();
    }

    private boolean haveStoragePermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE);
    }

    @Override
    public void onClick(View v) {
        if (!haveStoragePermission()) {
            requestStoragePermission();
        }
        else {
            startApp();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required,
                            Toast.LENGTH_LONG).show();
                } else {
                    startApp();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
