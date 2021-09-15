package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DiskEvent;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.views.ChangeListAdapter;
import com.nutomic.syncthingandroid.views.ChangeListAdapter.ItemClickListener;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.Constants.ENABLE_TEST_DATA;

/**
 * Holds a RecyclerView that shows recent changes to files and folders.
 */
public class RecentChangesActivity extends SyncthingActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "RecentChangesActivity";

    private static int DISK_EVENT_LIMIT = 100;

    private Boolean ENABLE_VERBOSE_LOG = false;

    private List<Device> mDevices;
    private String mLocalDeviceId;
    private ChangeListAdapter mRecentChangeAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    @Inject SharedPreferences mPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
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
                    switch (diskEvent.data.action) {
                        case "deleted":
                            return;
                    }
                    LogV("User clicked item with title \'" + diskEvent.data.path + "\'");
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
        if (item.getItemId() == R.id.refresh) {
            onTimerEvent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        syncthingServiceBinder.getService().registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State newState) {
        LogV("onServiceStateChange(" + newState + ")");
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
        mLocalDeviceId = restApi.getLocalDevice().deviceID;
        LogV("Querying disk events");
        restApi.getDiskEvents(DISK_EVENT_LIMIT, this::onReceiveDiskEvents);
        if (ENABLE_TEST_DATA) {
            onReceiveDiskEvents(new ArrayList());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onReceiveDiskEvents(List<DiskEvent> diskEvents) {
        LogV("onReceiveDiskEvents");
        if (isFinishing()) {
            return;
        }

        if (ENABLE_TEST_DATA) {
            getTestData(diskEvents);
        }

        // Hide disk events that are useless to display.
        removeUselessDiskEvents(diskEvents);

        // Show text if the list is empty.
        findViewById(R.id.no_recent_changes).setVisibility(diskEvents.size() > 0 ? View.GONE : View.VISIBLE);

        mRecentChangeAdapter.clear();
        for (DiskEvent diskEvent : diskEvents) {
            if (diskEvent.data != null) {
                // Replace "modifiedBy" partial device ID by readable device name.
                if (!TextUtils.isEmpty(diskEvent.data.modifiedBy)) {
                    for (Device device : mDevices) {
                        if (diskEvent.data.modifiedBy.equals(device.deviceID.substring(0, diskEvent.data.modifiedBy.length()))) {
                            if (device.deviceID.equals(mLocalDeviceId)) {
                                diskEvent.data.modifiedBy = getString(R.string.this_device);
                            } else {
                                diskEvent.data.modifiedBy = device.getDisplayName();
                            }
                            break;
                        }
                    }
                }
                mRecentChangeAdapter.add(diskEvent);
            }
        }
        mRecentChangeAdapter.notifyDataSetChanged();
    }

    private void getTestData(List<DiskEvent> diskEvents) {
        Random random = new Random();
        /*
        if (random.nextInt(2) == 0) {
            diskEvents.clear();
            return;
        }
        */

        /**
         * Items on UI without "removeUselessDiskEvents"
         *  10
         * Items on UI after "removeUselessDiskEvents"
         *  6
         */
        int id = 11;
        DiskEvent fakeDiskEvent = new DiskEvent();
        fakeDiskEvent.globalID = 84;
        fakeDiskEvent.type = "RemoteChangeDetected";
        fakeDiskEvent.data.folder = "abcd-efgh";
        fakeDiskEvent.data.folderID = "abcd-efgh";
        fakeDiskEvent.data.label = "label_abcd-efgh";
        fakeDiskEvent.data.modifiedBy = "SRV01";

        // - "Camera - Copy" folder
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "deleted";
        fakeDiskEvent.data.path = "Camera - Copy";
        fakeDiskEvent.data.type = "dir";
        fakeDiskEvent.time = "2018-10-29T15:18:52.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // - "document2.txt"
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "deleted";
        fakeDiskEvent.data.path = "document2.txt";
        fakeDiskEvent.data.type = "file";
        fakeDiskEvent.time = "2020-04-13T15:01:00.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // + "document2.txt" - to be removed by Pass 2
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "added";
        fakeDiskEvent.data.path = "document2.txt";
        fakeDiskEvent.time = "2020-04-13T15:00:00.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // - "Camera - Copy/IMG_20200413_130936.jpg" - to be removed by Pass 3
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "deleted";
        fakeDiskEvent.data.path = "Camera - Copy/IMG_20200413_130936.jpg";
        fakeDiskEvent.time = "2018-10-29T15:18:50.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // + "document1.txt"
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "added";
        fakeDiskEvent.data.path = "document1.txt";
        fakeDiskEvent.time = "2018-10-29T17:08:00.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // - "Camera - Copy/IMG_20200413_132532.jpg" - to be removed by Pass 3
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "deleted";
        fakeDiskEvent.data.path = "Camera - Copy/IMG_20200413_132532.jpg";
        fakeDiskEvent.time = "2018-10-29T15:18:50.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // - "Camera - Copy/IMG_20200413_132049.jpg" - to be removed by Pass 3
        fakeDiskEvent.id = --id;
        fakeDiskEvent.data.action = "deleted";
        fakeDiskEvent.data.path = "Camera - Copy/IMG_20200413_132049.jpg";
        fakeDiskEvent.time = "2018-10-29T15:18:50.6183215+01:00";
        addFakeDiskEvent(diskEvents, fakeDiskEvent);

        // - "document1.txt"
        for (int i = --id; i > 0; i--) {
            fakeDiskEvent.id = i;
            fakeDiskEvent.data.action = "deleted";
            fakeDiskEvent.data.path = "document1.txt";
            fakeDiskEvent.time = "2018-10-28T14:08:" +
                    String.format(Locale.getDefault(), "%02d", random.nextInt(60)) +
                    ".6183215+01:00";
            addFakeDiskEvent(diskEvents, fakeDiskEvent);
        }
    }

    private void addFakeDiskEvent(List<DiskEvent> diskEvents,
                                        final DiskEvent fakeDiskEvent) {
        diskEvents.add(deepCopy(fakeDiskEvent, new TypeToken<DiskEvent>(){}.getType()));
    }

    private void removeUselessDiskEvents(List<DiskEvent> diskEvents) {
        /**
         * Pass 1
         * Remove invalid disk events
         */
        for (Iterator<DiskEvent> it = diskEvents.iterator(); it.hasNext();) {
            DiskEvent diskEvent = it.next();
            if (diskEvent.data == null) {
                Log.d(TAG, "removeUselessDiskEvents: Pass 1: Clearing event with data == null");
                it.remove();
            }
        }

        /**
         * Pass 2
         * Check if a file has been created and deleted afterwards.
         * We can remove the creation event for the non-existent file.
         */
        for (Iterator<DiskEvent> it = diskEvents.iterator(); it.hasNext();) {
            DiskEvent diskEvent = it.next();
            for (Iterator<DiskEvent> it2 = diskEvents.iterator(); it2.hasNext();) {
                DiskEvent diskEvent2 = it2.next();
                if (diskEvent2.id > diskEvent.id) {
                    // diskEvent2 occured after diskEvent.
                    // LogV("removeUselessDiskEvents: Pass 2: curId=" + diskEvent.id + ", foundId=" + diskEvent2.id);
                    if (diskEvent2.data.path.equals(diskEvent.data.path) &&
                            diskEvent.data.action.equals("added") &&
                            diskEvent2.data.action.equals("deleted")) {
                        LogV("removeUselessDiskEvents: Pass 2: Removing \"added\" event because file was deleted afterwards, path=[" + diskEvent.data.path + "]");
                        it.remove();
                        break;
                    }
                }
            }
        }

        /**
         * Pass 3
         * Check if a folder has been removed.
         * We can remove prior events corresponding to the folder.
         */
        for (Iterator<DiskEvent> it = diskEvents.iterator(); it.hasNext();) {
            DiskEvent diskEvent = it.next();
            for (Iterator<DiskEvent> it2 = diskEvents.iterator(); it2.hasNext();) {
                DiskEvent diskEvent2 = it2.next();
                if (diskEvent2.id > diskEvent.id) {
                    // diskEvent2 occured after diskEvent.
                    // LogV("removeUselessDiskEvents: Pass 3: curId=" + diskEvent.id + ", foundId=" + diskEvent2.id);
                    if (diskEvent2.data.type.equals("dir") &&
                            diskEvent2.data.action.equals("deleted") &&
                            diskEvent.data.path.startsWith(diskEvent2.data.path + "/")) {
                        LogV("removeUselessDiskEvents: Pass 3: Removing event because folder was deleted afterwards, path=[" + diskEvent.data.path + "]");
                        it.remove();
                        break;
                    }
                }
            }
        }
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

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
