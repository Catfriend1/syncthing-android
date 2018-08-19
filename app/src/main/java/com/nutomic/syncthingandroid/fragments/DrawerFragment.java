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

import com.google.common.collect.ImmutableMap;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.http.ImageGetRequest;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.net.URL;


/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements SyncthingService.OnServiceStateChangeListener,
        View.OnClickListener {

    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    private static final String TAG = "DrawerFragment";

    private TextView mVersion;
    private TextView mDrawerActionShowQrCode;
    private TextView mDrawerActionWebGui;
    private TextView mDrawerActionRestart;
    private TextView mDrawerActionSettings;
    private TextView mExitButton;

    private MainActivity mActivity;
    private SharedPreferences sharedPreferences;

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtons();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        mActivity = (MainActivity) getActivity();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);

        mVersion                = view.findViewById(R.id.version);
        mDrawerActionShowQrCode = view.findViewById(R.id.drawerActionShowQrCode);
        mDrawerActionWebGui     = view.findViewById(R.id.drawerActionWebGui);
        mDrawerActionRestart    = view.findViewById(R.id.drawerActionRestart);
        mDrawerActionSettings   = view.findViewById(R.id.drawerActionSettings);
        mExitButton             = view.findViewById(R.id.drawerActionExit);

        // Show static content.
        mVersion.setText(sharedPreferences.getString(Constants.PREF_LAST_BINARY_VERSION, ""));

        // Add listeners to buttons.
        mDrawerActionShowQrCode.setOnClickListener(this);
        mDrawerActionWebGui.setOnClickListener(this);
        mDrawerActionRestart.setOnClickListener(this);
        mDrawerActionSettings.setOnClickListener(this);
        mExitButton.setOnClickListener(this);

        updateButtons();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Update action button availability.
     */
    private void updateButtons() {
        Boolean synthingRunning = mServiceState == SyncthingService.State.ACTIVE;

        // Show buttons if syncthing is running.
        mVersion.setVisibility(synthingRunning ? View.VISIBLE : View.GONE);
        mDrawerActionShowQrCode.setVisibility(synthingRunning ? View.VISIBLE : View.GONE);
        mDrawerActionWebGui.setVisibility(synthingRunning ? View.VISIBLE : View.GONE);
        mDrawerActionRestart.setVisibility(synthingRunning ? View.VISIBLE : View.GONE);

        // Do not show the exit button if our app runs as a background service.
        mExitButton.setVisibility(
            sharedPreferences.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false) ?
                View.GONE :
                View.VISIBLE
        );
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
}
