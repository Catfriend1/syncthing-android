package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.SystemStatus;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.SegmentedButton;
import com.nutomic.syncthingandroid.views.SegmentedButton.OnClickListenerSegmentedButton;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION;

/**
 * Displays why syncthing is running or disabled.
 */
public class StatusFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "StatusFragment";

    private Boolean ENABLE_VERBOSE_LOG = false;

    @Inject SharedPreferences mPreferences;

    private Runnable mRestApiQueryRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mRestApiQueryHandler.postDelayed(this, Constants.REST_UPDATE_INTERVAL);
        }
    };

    private MainActivity mActivity;
    private ArrayAdapter mAdapter;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;
    private final Handler mRestApiQueryHandler = new Handler();
    private Boolean mLastVisibleToUser = false;
    private SegmentedButton btnForceStartStop;
    /**
     * Object that must be locked upon accessing the status holders.
     */
    private final Object mStatusHolderLock = new Object();

    /**
     * Status holders, filled on callbacks.
     */
    private String mRamUsage = "";
    private String mDownload = "";
    private String mUpload = "";
    private String mAnnounceServer = "";
    private String mUptime = "";

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
            startRestApiQueryHandler();
        } else {
            // User switched away to another tab, stop handler.
            stopRestApiQueryHandler();
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        stopRestApiQueryHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLastVisibleToUser) {
            startRestApiQueryHandler();
        }
    }

    private void startRestApiQueryHandler() {
        LogV("startUpdateListHandler");
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
        mRestApiQueryHandler.post(mRestApiQueryRunnable);
    }

    private void stopRestApiQueryHandler() {
        LogV("stopUpdateListHandler");
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
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

        // Create button group.
        btnForceStartStop = (SegmentedButton) mActivity.findViewById(R.id.forceStartStop);
        btnForceStartStop.clearButtons();
        btnForceStartStop.addButtons(
                getString(R.string.button_follow_run_conditions),
                getString(R.string.button_force_start),
                getString(R.string.button_force_stop)
        );
        btnForceStartStop.setOnClickListener(new OnClickListenerSegmentedButton() {
                @Override
                public void onClick(int index) {
                    onBtnForceStartStopClick(index);
                }
        });

        // Restore last state of button group.
        int prefBtnStateForceStartStop = mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP);
        btnForceStartStop.setPushedButtonIndex(prefBtnStateForceStartStop);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.status_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.open_preferences) {
            startActivity(new Intent(getContext(), SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String pref) {
            if (pref != null && pref.equals(Constants.PREF_BTNSTATE_FORCE_START_STOP))
                btnForceStartStop.setPushedButtonIndex(mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP));
        }
    };

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
                if (!TextUtils.isEmpty(mUptime)) {
                    statusItems.add(getString(R.string.uptime) + ": " + mUptime);
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
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            updateStatus();
            return;
        }
        RestApi restApi = mainActivity.getApi();
        if (restApi == null) {
            return;
        }
        // LogV("Invoking REST status queries");

        // Force a cache-miss to query status of all devices asynchronously.
        restApi.getRemoteDeviceStatus("");

        // onReceiveSystemStatus will call {@link #updateStatus}.
        restApi.getSystemStatus(this::onReceiveSystemStatus);
    }

    /**
     * Populates status holders with status received via {@link RestApi#getSystemStatus}.
     */
    private void onReceiveSystemStatus(SystemStatus systemStatus) {
        if (getActivity() == null) {
            return;
        }
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        int announceTotal = systemStatus.discoveryMethods;
        int announceConnected =
                announceTotal - Optional.fromNullable(systemStatus.discoveryErrors).transform(Map::size).or(0);
        synchronized (mStatusHolderLock) {
            mRamUsage = Util.readableFileSize(mActivity, systemStatus.sys);
            mAnnounceServer = (announceTotal == 0) ?
                    "" :
                    String.format(Locale.getDefault(), "%1$d/%2$d", announceConnected, announceTotal);

            /**
             * Calculate readable uptime.
             */
            long uptimeDays = TimeUnit.SECONDS.toDays(systemStatus.uptime);
            long uptimeHours = TimeUnit.SECONDS.toHours(systemStatus.uptime) - TimeUnit.DAYS.toHours(uptimeDays);
            long uptimeMinutes = TimeUnit.SECONDS.toMinutes(systemStatus.uptime) - TimeUnit.HOURS.toMinutes(uptimeHours) - TimeUnit.DAYS.toMinutes(uptimeDays);
            if (uptimeDays > 0) {
                mUptime = String.format(Locale.getDefault(), "%dd %02dh %02dm", uptimeDays, uptimeHours, uptimeMinutes);
            } else if (uptimeHours > 0) {
                mUptime = String.format(Locale.getDefault(), "%dh %02dm", uptimeHours, uptimeMinutes);
            } else {
                mUptime = String.format(Locale.getDefault(), "%dm", uptimeMinutes);
            }

            Connection total = new Connection();
            RestApi restApi = getRestApiSafe();
            if  (restApi != null) {
                if (restApi.isConfigLoaded()) {
                    total = restApi.getTotalConnectionStatistic();
                }
            }

            /**
             * "Hide" rates on the UI if they are lower than 1 KByte/sec. We don't like to
             * bother the user looking at discovery or index exchange traffic.
             */
            mDownload = (total.inBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.inBits);
            mDownload += " (" + Util.readableFileSize(mActivity, total.inBytesTotal) + ")";
            mUpload = (total.outBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.outBits);
            mUpload += " (" + Util.readableFileSize(mActivity, total.outBytesTotal) + ")";
        }
        updateStatus();
    }

    private void onBtnForceStartStopClick(int index) {
        LogV("onBtnForceStartStopClick");

        // Note: "index" is equivalent to the defined integer "Constants.PREF_BTNSTATE_*" values.
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, index);
        editor.apply();

        // Notify {@link RunConditionMonitor} that the button's state changed.
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mActivity);
        Intent intent = new Intent(ACTION_UPDATE_SHOULDRUN_DECISION);
        localBroadcastManager.sendBroadcast(intent);
    }

    private RestApi getRestApiSafe() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return null;
        }
        if (mainActivity.isFinishing()) {
            return null;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            return null;
        }
        return mainActivity.getApi();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
