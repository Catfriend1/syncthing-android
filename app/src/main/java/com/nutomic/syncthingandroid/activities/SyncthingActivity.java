package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.annimon.stream.Stream;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

import java.util.LinkedList;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends AppCompatActivity implements ServiceConnection {

    public static final String EXTRA_FIRST_START = "com.nutomic.syncthing-android.SyncthingActivity.FIRST_START";

    private SyncthingService mSyncthingService;

    private final LinkedList<OnServiceConnectedListener> mServiceConnectedListeners = new LinkedList<>();

    /**
     * To be used for Fragments.
     */
    public interface OnServiceConnectedListener {
        void onServiceConnected();
    }

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
        if (toolbar == null)
            return;

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
        SyncthingServiceBinder binder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = binder.getService();
        Stream.of(mServiceConnectedListeners).forEach(OnServiceConnectedListener::onServiceConnected);
        mServiceConnectedListeners.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    /**
     * Used for Fragments to use the Activity's service connection.
     */
    void registerOnServiceConnectedListener(OnServiceConnectedListener listener) {
        if (mSyncthingService != null) {
            listener.onServiceConnected();
        } else {
            mServiceConnectedListeners.addLast(listener);
        }
    }

    /**
     * Returns service object (or null if not bound).
     */
    SyncthingService getService() {
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
