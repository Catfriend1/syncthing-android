package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.views.DevicesAdapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

/**
 * Displays a list of all existing devices.
 */
public class DeviceListFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener,
        ListView.OnItemClickListener {

    private final static String TAG = "DeviceListFragment";

    private Boolean ENABLE_VERBOSE_LOG = false;

    @Inject SharedPreferences mPreferences;

    /**
     * Compares devices by name, uses the device ID as fallback if the name is empty
     */
    private final static Comparator<Device> DEVICES_COMPARATOR = (lhs, rhs) -> {
        String lhsName = lhs.name != null && !lhs.name.isEmpty() ? lhs.name : lhs.deviceID;
        String rhsName = rhs.name != null && !rhs.name.isEmpty() ? rhs.name : rhs.deviceID;
        return lhsName.compareTo(rhsName);
    };

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mUpdateListHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL);
        }
    };

    private final Handler mUpdateListHandler = new Handler();
    private Boolean mLastVisibleToUser = false;
    private DevicesAdapter mAdapter;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getActivity().getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // User switched to the current tab, start handler.
            startUpdateListHandler();
        } else {
            // User switched away to another tab, stop handler.
            stopUpdateListHandler();
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        stopUpdateListHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLastVisibleToUser) {
            startUpdateListHandler();
        }
    }

    private void startUpdateListHandler() {
        LogV("startUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
        mUpdateListHandler.post(mUpdateListRunnable);
    }

    private void stopUpdateListHandler() {
        LogV("stopUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);
        setEmptyText(getString(R.string.devices_list_empty));
        getListView().setOnItemClickListener(this);
    }

    /**
     * Invokes updateList which polls the REST API for device status updates
     *  while the user is looking at the current tab.
     */
    private void onTimerEvent() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        LogV("Invoking updateList on UI thread");
        mainActivity.runOnUiThread(DeviceListFragment.this::updateList);
    }

    /**
     * Refreshes ListView by updating devices and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }
        List<Device> devices;
        RestApi restApi = activity.getApi();
        if (restApi == null ||
                !restApi.isConfigLoaded() ||
                mServiceState != SyncthingService.State.ACTIVE) {
            // Syncthing is not running or REST API is not available yet.
            ConfigXml configXml = new ConfigXml(activity);
            configXml.loadConfig();
            devices = configXml.getDevices(false);
        } else {
            // Syncthing is running and REST API is available.
            devices = restApi.getDevices(false);
        }
        if (devices == null) {
            return;
        }
        if (mAdapter == null) {
            mAdapter = new DevicesAdapter(activity);
            setListAdapter(mAdapter);
        }

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        Collections.sort(devices, DEVICES_COMPARATOR);
        mAdapter.addAll(devices);
        mAdapter.updateDeviceStatus(restApi);
        mAdapter.notifyDataSetChanged();
        setListShown(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), DeviceActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_IS_CREATE, false);
        intent.putExtra(DeviceActivity.EXTRA_DEVICE_ID, mAdapter.getItem(i).deviceID);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.device_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_device:
                Intent intent = new Intent(getActivity(), DeviceActivity.class)
                        .putExtra(DeviceActivity.EXTRA_IS_CREATE, true);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
