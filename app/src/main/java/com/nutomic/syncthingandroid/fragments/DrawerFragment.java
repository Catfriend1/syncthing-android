package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.http.ImageGetRequest;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "DrawerFragment";

    private TextView mCpuUsage;
    private TextView mRamUsage;
    private TextView mDownload;
    private TextView mUpload;
    private TextView mAnnounceServer;
    private TextView mVersion;
    private TextView mExitButton;

    private Timer mTimer;

    private MainActivity mActivity;

    public void onDrawerOpened() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateGui();
            }

        }, 0, Constants.GUI_UPDATE_INTERVAL);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateExitButtonVisibility();
    }

    public void onDrawerClosed() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onDrawerClosed();
    }

    /**
     * Populates views and menu.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mCpuUsage       = view.findViewById(R.id.cpu_usage);
        mRamUsage       = view.findViewById(R.id.ram_usage);
        mDownload       = view.findViewById(R.id.download);
        mUpload         = view.findViewById(R.id.upload);
        mAnnounceServer = view.findViewById(R.id.announce_server);
        mVersion        = view.findViewById(R.id.version);
        mExitButton     = view.findViewById(R.id.drawerActionExit);

        view.findViewById(R.id.drawerActionWebGui)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionRestart)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionSettings)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionShowQrCode)
                .setOnClickListener(this);
        mExitButton.setOnClickListener(this);

        updateExitButtonVisibility();
    }

    private void updateExitButtonVisibility() {
        boolean alwaysInBackground = alwaysRunInBackground();
        mExitButton.setVisibility(alwaysInBackground ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (MainActivity) getActivity();

        if (savedInstanceState != null && savedInstanceState.getBoolean("active")) {
            onDrawerOpened();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("active", mTimer != null);
    }

    /**
     * Invokes status callbacks.
     */
    private void updateGui() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }

        RestApi mApi = mainActivity.getApi();
        if (mApi != null) {
            mApi.getSystemInfo(this::onReceiveSystemInfo);
            mApi.getSystemVersion(this::onReceiveSystemVersion);
            mApi.getConnections(this::onReceiveConnections);
        }
    }

    /**
     * This will not do anything if gui updates are already scheduled.
     */
    public void requestGuiUpdate() {
        if (mTimer == null) {
            updateGui();
        }
    }

    /**
     * Populates views with status received via {@link RestApi#getSystemInfo}.
     */
    private void onReceiveSystemInfo(SystemInfo info) {
        if (getActivity() == null)
            return;
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        mCpuUsage.setText(percentFormat.format(info.cpuPercent / 100));
        mRamUsage.setText(Util.readableFileSize(mActivity, info.sys));
        int announceTotal = info.discoveryMethods;
        int announceConnected =
                announceTotal - Optional.fromNullable(info.discoveryErrors).transform(Map::size).or(0);
        mAnnounceServer.setText(String.format(Locale.getDefault(), "%1$d/%2$d",
                                              announceConnected, announceTotal));
        int color = (announceConnected > 0)
                ? R.color.text_green
                : R.color.text_red;
        mAnnounceServer.setTextColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * Populates views with status received via {@link RestApi#getSystemInfo}.
     */
    private void onReceiveSystemVersion(SystemVersion info) {
        if (getActivity() == null)
            return;

        mVersion.setText(info.version);
    }

    /**
     * Populates views with status received via {@link RestApi#getConnections}.
     */
    private void onReceiveConnections(Connections connections) {
        Connections.Connection c = connections.total;
        mDownload.setText(Util.readableTransferRate(mActivity, c.inBits));
        mUpload.setText(Util.readableTransferRate(mActivity, c.outBits));
    }

    /**
     * Gets QRCode and displays it in a Dialog.
     */

    private void showQrCode() {
        RestApi restApi = mActivity.getApi();
        if (restApi == null) {
            Toast.makeText(mActivity, R.string.syncthing_terminated, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String apiKey = restApi.getGui().apiKey;
            String deviceId = restApi.getLocalDevice().deviceID;
            URL url = restApi.getUrl();
            //The QRCode request takes one paramteer called "text", which is the text to be converted to a QRCode.
            new ImageGetRequest(mActivity, url, ImageGetRequest.QR_CODE_GENERATOR, apiKey,
                    ImmutableMap.of("text", deviceId),qrCodeBitmap -> {
                mActivity.showQrCodeDialog(deviceId, qrCodeBitmap);
                mActivity.closeDrawer();
            }, error -> Toast.makeText(mActivity, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "showQrCode", e);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.drawerActionWebGui:
                startActivity(new Intent(mActivity, WebGuiActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionSettings:
                startActivity(new Intent(mActivity, SettingsActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionRestart:
                mActivity.showRestartDialog();
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionExit:
                mActivity.stopService(new Intent(mActivity, SyncthingService.class));
                mActivity.finish();
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionShowQrCode:
                showQrCode();
                break;
        }
    }

    private boolean alwaysRunInBackground() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return sp.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);
    }
}
