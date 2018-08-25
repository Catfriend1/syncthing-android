package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.views.DevicesAdapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Displays a list of all existing devices.
 */
public class DeviceListFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener,
        ListView.OnItemClickListener {

    private final static String TAG = "DeviceListFragment";

    private final static Comparator<Device> DEVICES_COMPARATOR = (lhs, rhs) -> lhs.name.compareTo(rhs.name);

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
        Log.v(TAG, "startUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
        mUpdateListHandler.post(mUpdateListRunnable);
    }

    private void stopUpdateListHandler() {
        Log.v(TAG, "stopUpdateListHandler");
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
        if (mServiceState != SyncthingService.State.ACTIVE) {
            return;
        }
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        RestApi restApi = mainActivity.getApi();
        if (restApi == null) {
            return;
        }
        Log.v(TAG, "Invoking updateList on UI thread");
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
        RestApi restApi = activity.getApi();
        if (restApi == null || !restApi.isConfigLoaded()) {
            return;
        }
        List<Device> devices = restApi.getDevices(false);
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
        mAdapter.updateConnections(restApi);
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

}
