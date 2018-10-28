package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DiskEvent;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.views.ChangeListAdapter;
import com.nutomic.syncthingandroid.views.ChangeListAdapter.ItemClickListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds a RecyclerView that shows recent changes to files and folders.
 */
public class RecentChangesActivity extends SyncthingActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "RecentChangesActivity";

    private static int DISK_EVENT_LIMIT = 100;

    private List<Device> mDevices;
    private ChangeListAdapter mRecentChangeAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_changes);
        mRecyclerView = findViewById(R.id.changes_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecentChangeAdapter = new ChangeListAdapter(this);

        // Set onClick listener and add adapter to recycler view.
        mRecentChangeAdapter.setOnClickListener(
            new ItemClickListener() {
                @Override
                public void onItemClick(DiskEvent diskEvent) {
                    Log.v(TAG, "User clicked item with title \'" + diskEvent.data.path + "\'");
                    /**
                     * Future improvement:
                     * Collapse texts to the first three lines and open a DialogFragment
                     * if the user clicks an item from the list.
                     */
                }
            }
        );
        mRecyclerView.setAdapter(mRecentChangeAdapter);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        syncthingServiceBinder.getService().registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State newState) {
        Log.v(TAG, "onServiceStateChange(" + newState + ")");
        mServiceState = newState;
        if (newState == SyncthingService.State.ACTIVE) {
            onTimerEvent();
        }
    }

    @Override
    protected void onDestroy() {
        SyncthingService syncthingService = getService();
        if (syncthingService != null) {
            syncthingService.unregisterOnServiceStateChangeListener(this);
        }
        super.onDestroy();
    }

    private void onTimerEvent() {
        if (isFinishing()) {
            return;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            return;
        }
        SyncthingService syncthingService = getService();
        if (syncthingService == null) {
            Log.e(TAG, "syncthingService == null");
            return;
        }
        RestApi restApi = syncthingService.getApi();
        if (restApi == null) {
            Log.e(TAG, "restApi == null");
            return;
        }
        mDevices = restApi.getDevices(true);
        Log.v(TAG, "Querying disk events");
        restApi.getDiskEvents(DISK_EVENT_LIMIT, this::onReceiveDiskEvents);
    }

    private void onReceiveDiskEvents(List<DiskEvent> diskEvents) {
        Log.v(TAG, "onReceiveDiskEvents");
        if (isFinishing()) {
            return;
        }

        mRecentChangeAdapter.clear();
        for (DiskEvent diskEvent : diskEvents) {
            if (diskEvent.data != null) {
                // Replace "modifiedBy" partial device ID by readable device name.
                if (!TextUtils.isEmpty(diskEvent.data.modifiedBy)) {
                    for (Device device : mDevices) {
                        if (diskEvent.data.modifiedBy.equals(device.deviceID.substring(0, diskEvent.data.modifiedBy.length()))) {
                            diskEvent.data.modifiedBy = device.getDisplayName();
                            break;
                        }
                    }
                }
                mRecentChangeAdapter.add(diskEvent);
            }
        }
        mRecentChangeAdapter.notifyDataSetChanged();
    }
}
