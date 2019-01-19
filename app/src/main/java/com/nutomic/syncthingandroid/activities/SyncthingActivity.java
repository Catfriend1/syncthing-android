package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
// import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "SyncthingActivity";

    private SyncthingService mSyncthingService;

    /**
     * Look for a Toolbar in the layout and bind it as the activity's actionbar with reasonable
     * defaults.
     *
     * The Toolbar must exist in the content view and have an id of R.id.toolbar. Trying to call
     * getSupportActionBar before this Activity's onPostCreate will cause a crash.
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }
        toolbar.setNavigationContentDescription(R.string.main_menu);
        toolbar.setNavigationIcon(R.drawable.btn_arrow_back);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setTouchscreenBlocksFocus(false);
        }
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.btn_arrow_back);
        }
    }

    @Override
    protected void onPause() {
        unbindService(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, SyncthingService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = syncthingServiceBinder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    /**
     * Returns service object (or null if not bound).
     */
    public SyncthingService getService() {
        return mSyncthingService;
    }

    /**
     * Returns RestApi instance, or null if SyncthingService is not yet connected.
     */
    public RestApi getApi() {
        return (getService() != null)
                ? getService().getApi()
                : null;
    }
}
