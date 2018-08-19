package com.nutomic.syncthingandroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.base.Optional;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;

/**
 * Displays why syncthing is running or disabled.
 */
public class StatusFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "StatusFragment";

    private Runnable mRestApiQueryRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mRestApiQueryHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL);
        }
    };

    private MainActivity mActivity;
    private ArrayAdapter mAdapter;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;
    private final Handler mRestApiQueryHandler = new Handler();
    private Boolean mLastVisibleToUser = false;

    /**
     * Object that must be locked upon accessing the status holders.
     */
    private final Object mStatusHolderLock = new Object();

    /**
     * Status holders, filled on callbacks.
     */
    private String mCpuUsage = "";
    private String mRamUsage = "";
    private String mDownload = "";
    private String mUpload = "";
    private String mAnnounceServer = "";

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && !mLastVisibleToUser) {
            // User switched to the current tab, start handler.
            mRestApiQueryHandler.post(mRestApiQueryRunnable);

        } else if (!isVisibleToUser && mLastVisibleToUser) {
            // User switched away to another tab, stop handler.
            mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
        super.onPause();
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateStatus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_list_item_1);
        setListAdapter(mAdapter);
        setHasOptionsMenu(true);
        updateStatus();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (MainActivity) getActivity();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.status_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.open_preferences:
                startActivity(new Intent(getContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateStatus() {
        SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
        if (syncthingActivity == null || getView() == null || syncthingActivity.isFinishing()) {
            return;
        }
        SyncthingService syncthingService = syncthingActivity.getService();
        if (syncthingService == null) {
            return;
        }

        // Add status line showing the syncthing service state.
        ArrayList<String> statusItems = new ArrayList<String>();
        switch (mServiceState) {
            case INIT:
            case STARTING:
                statusItems.add(getString(R.string.syncthing_starting));
                break;
            case ACTIVE:
                statusItems.add(getString(R.string.syncthing_running));
                break;
            case DISABLED:
                statusItems.add(getString(R.string.syncthing_not_running));
                break;
            case ERROR:
                statusItems.add(getString(R.string.syncthing_has_crashed));
                break;
        }

        // Add explanation why syncthing is (not) running.
        switch (mServiceState) {
            case ACTIVE:
            case DISABLED:
                statusItems.add(getString(R.string.reason) + "\n" +
                    "- " + syncthingService.getRunDecisionExplanation().trim().replace("\n", "\n- "));
            default:
                break;
        }

        // Add status holders refreshed by callbacks to the list.
        if (mServiceState == SyncthingService.State.ACTIVE) {
            synchronized (mStatusHolderLock) {
                if (!TextUtils.isEmpty(mCpuUsage)) {
                    statusItems.add(getString(R.string.cpu_usage) + ": " + mCpuUsage);
                }
                if (!TextUtils.isEmpty(mRamUsage)) {
                    statusItems.add(getString(R.string.ram_usage) + ": " + mRamUsage);
                }
                if (!TextUtils.isEmpty(mDownload)) {
                    statusItems.add(getString(R.string.download_title) + ": " + mDownload);
                }
                if (!TextUtils.isEmpty(mUpload)) {
                    statusItems.add(getString(R.string.upload_title) + ": " + mUpload);
                }
                if (!TextUtils.isEmpty(mAnnounceServer)) {
                    statusItems.add(getString(R.string.announce_server) + ": " + mAnnounceServer);
                }
            }
        }

        // Update list contents.
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        mAdapter.addAll(statusItems);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Invokes status callbacks via syncthing's REST API
     * while the user is looking at the current tab.
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
        Log.v(TAG, "Invoking REST status queries");
        restApi.getSystemInfo(this::onReceiveSystemInfo);
        restApi.getConnections(this::onReceiveConnections);
    }

    /**
     * Populates status holders with status received via {@link RestApi#getSystemInfo}.
     */
    private void onReceiveSystemInfo(SystemInfo info) {
        if (getActivity() == null) {
            return;
        }
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        int announceTotal = info.discoveryMethods;
        int announceConnected =
                announceTotal - Optional.fromNullable(info.discoveryErrors).transform(Map::size).or(0);
        synchronized (mStatusHolderLock) {
            mCpuUsage = percentFormat.format(info.cpuPercent / 100);
            mRamUsage = Util.readableFileSize(mActivity, info.sys);
            mAnnounceServer = String.format(Locale.getDefault(), "%1$d/%2$d", announceConnected, announceTotal);
        }
        updateStatus();
    }

    /**
     * Populates status holders with status received via {@link RestApi#getConnections}.
     */
    private void onReceiveConnections(Connections connections) {
        if (getActivity() == null) {
            return;
        }
        Connections.Connection c = connections.total;
        synchronized (mStatusHolderLock) {
            mDownload = Util.readableTransferRate(mActivity, c.inBits);
            mUpload = Util.readableTransferRate(mActivity, c.outBits);
        }
        updateStatus();
    }

}
