package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DiskEvent;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.views.ChangeListAdapter;
import com.nutomic.syncthingandroid.views.ChangeListAdapter.ItemClickListener;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.nutomic.syncthingandroid.service.Constants.ENABLE_TEST_DATA;

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
                    switch (diskEvent.data.action) {
                        case "deleted":
                            return;
                    }
                    if (mServiceState != SyncthingService.State.ACTIVE) {
                        return;
                    }
                    SyncthingService syncthingService = getService();
                    if (syncthingService == null) {
                        Log.e(TAG, "onItemClick: syncthingService == null");
                        return;
                    }
                    RestApi restApi = syncthingService.getApi();
                    if (restApi == null) {
                        Log.e(TAG, "onItemClick: restApi == null");
                        return;
                    }
                    Folder folder = restApi.getFolderByID(diskEvent.data.folderID);
                    if (folder == null) {
                        Log.e(TAG, "onItemClick: folder == null");
                        return;
                    }
                    switch (diskEvent.data.type) {
                        case "dir":
                            FileUtils.openFolder(RecentChangesActivity.this, folder.path + File.separator + diskEvent.data.path);
                            break;
                        case "file":
                            FileUtils.openFile(RecentChangesActivity.this, folder.path + File.separator + diskEvent.data.path);
                            break;
                        default:
                            Log.e(TAG, "onItemClick: Unknown diskEvent.data.type=[" + diskEvent.data.type + "]");
                    }
                }
            }
        );
        mRecyclerView.setAdapter(mRecentChangeAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.recent_changes_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                onTimerEvent();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
            Log.e(TAG, "onTimerEvent: syncthingService == null");
            return;
        }
        RestApi restApi = syncthingService.getApi();
        if (restApi == null) {
            Log.e(TAG, "onTimerEvent: restApi == null");
            return;
        }
        mDevices = restApi.getDevices(true);
        Log.v(TAG, "Querying disk events");
        restApi.getDiskEvents(DISK_EVENT_LIMIT, this::onReceiveDiskEvents);
        if (ENABLE_TEST_DATA) {
            onReceiveDiskEvents(new ArrayList());
        }
    }

    private void onReceiveDiskEvents(List<DiskEvent> diskEvents) {
        Log.v(TAG, "onReceiveDiskEvents");
        if (isFinishing()) {
            return;
        }

        if (ENABLE_TEST_DATA) {
            DiskEvent fakeDiskEvent = new DiskEvent();
            fakeDiskEvent.id = 10;
            fakeDiskEvent.globalID = 84;
            fakeDiskEvent.time = "2018-10-28T14:08:" +
                    String.format(Locale.getDefault(), "%02d", new Random().nextInt(60)) +
                    ".6183215+01:00";
            fakeDiskEvent.type = "RemoteChangeDetected";
            fakeDiskEvent.data.action = "added";
            fakeDiskEvent.data.folder = "abcd-efgh";
            fakeDiskEvent.data.folderID = "abcd-efgh";
            fakeDiskEvent.data.label = "label_abcd-efgh";
            fakeDiskEvent.data.modifiedBy = "SRV01";
            fakeDiskEvent.data.path = "document1.txt";
            fakeDiskEvent.data.type = "file";
            diskEvents.add(fakeDiskEvent);

            for (int i = 9; i > 0; i--) {
                fakeDiskEvent = deepCopy(fakeDiskEvent, new TypeToken<DiskEvent>(){}.getType());
                fakeDiskEvent.id = i;
                fakeDiskEvent.data.action = "deleted";
                diskEvents.add(fakeDiskEvent);
            }

            if (new Random().nextInt(2) == 0) {
                diskEvents.clear();
            }
        }

        // Show text if the list is empty.
        findViewById(R.id.no_recent_changes).setVisibility(diskEvents.size() > 0 ? View.GONE : View.VISIBLE);

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

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }
}
