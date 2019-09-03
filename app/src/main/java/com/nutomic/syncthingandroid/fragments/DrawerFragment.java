package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.common.collect.ImmutableMap;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.RecentChangesActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.TipsAndTricksActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.http.ImageGetRequest;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.net.URL;


/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements SyncthingService.OnServiceStateChangeListener,
        View.OnClickListener {

    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    private static final String TAG = "DrawerFragment";

    private static final int SETTINGS_SCREEN_REQUEST = 3460;

    /**
     * These buttons might be accessible if the screen is big enough
     * or the user can scroll the drawer to access them.
     */
    private TextView mVersion = null;
    private TextView mDrawerActionShowQrCode;
    private TextView mDrawerRecentChanges;
    private TextView mDrawerActionWebGui;
    private TextView mDrawerActionImportExport;
    private TextView mDrawerActionRestart;
    private TextView mDrawerTipsAndTricks;

    /**
     * These buttons are always visible.
     */
    private TextView mDrawerActionSettings;
    private TextView mDrawerActionExit;

    private MainActivity mActivity;
    private SharedPreferences sharedPreferences = null;

    private Boolean mRunningOnTV = false;

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
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
        mRunningOnTV = Util.isRunningOnTV(mActivity);

        mVersion                    = view.findViewById(R.id.version);
        mDrawerActionShowQrCode     = view.findViewById(R.id.drawerActionShowQrCode);
        mDrawerRecentChanges        = view.findViewById(R.id.drawerActionRecentChanges);
        mDrawerActionWebGui         = view.findViewById(R.id.drawerActionWebGui);
        mDrawerActionImportExport   = view.findViewById(R.id.drawerActionImportExport);
        mDrawerActionRestart        = view.findViewById(R.id.drawerActionRestart);
        mDrawerTipsAndTricks        = view.findViewById(R.id.drawerActionTipsAndTricks);
        mDrawerActionSettings       = view.findViewById(R.id.drawerActionSettings);
        mDrawerActionExit           = view.findViewById(R.id.drawerActionExit);

        // Add listeners to buttons.
        mDrawerActionShowQrCode.setOnClickListener(this);
        mDrawerRecentChanges.setOnClickListener(this);
        mDrawerActionWebGui.setOnClickListener(this);
        mDrawerActionImportExport.setOnClickListener(this);
        mDrawerActionRestart.setOnClickListener(this);
        mDrawerTipsAndTricks.setOnClickListener(this);
        mDrawerActionSettings.setOnClickListener(this);
        mDrawerActionExit.setOnClickListener(this);

        // Initially fill UI elements.
        updateUI();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void updateUI() {
        Boolean syncthingRunning = mServiceState == SyncthingService.State.ACTIVE;

        // Update static info labels.
        if (sharedPreferences != null && mVersion != null) {
            mVersion.setText(sharedPreferences.getString(Constants.PREF_LAST_BINARY_VERSION, ""));
        }

        // Update action button availability. Show buttons if syncthing is running.
        mVersion.setVisibility(View.VISIBLE);
        mDrawerActionShowQrCode.setVisibility(syncthingRunning ? View.VISIBLE : View.GONE);
        mDrawerRecentChanges.setVisibility(syncthingRunning ? View.VISIBLE : View.GONE);
        mDrawerActionWebGui.setVisibility((syncthingRunning && !mRunningOnTV) ? View.VISIBLE : View.GONE);
        mDrawerActionRestart.setVisibility(syncthingRunning ? View.VISIBLE : View.GONE);
        mDrawerTipsAndTricks.setVisibility(View.VISIBLE);
        mDrawerActionExit.setVisibility(View.VISIBLE);
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
            // The QRCode request takes one parameter called "text", which is the text to be converted to a QRCode.
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
        Intent intent;
        switch (v.getId()) {
            case R.id.drawerActionShowQrCode:
                showQrCode();
                break;
            case R.id.drawerActionRecentChanges:
                startActivity(new Intent(mActivity, RecentChangesActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionWebGui:
                startActivity(new Intent(mActivity, WebGuiActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionImportExport:
                intent = new Intent(mActivity, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_OPEN_SUB_PREF_SCREEN, "category_import_export");
                startActivity(intent);
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionRestart:
                mActivity.showRestartDialog();
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionTipsAndTricks:
                startActivity(new Intent(mActivity, TipsAndTricksActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionSettings:
                startActivityForResult(new Intent(mActivity, SettingsActivity.class), SETTINGS_SCREEN_REQUEST);
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionExit:
                if (sharedPreferences != null && sharedPreferences.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)) {
                    /**
                     * App is running as a service. Show an explanation why exiting syncthing is an
                     * extraordinary request, then ask the user to confirm.
                     */
                    AlertDialog mExitConfirmationDialog = new AlertDialog.Builder(mActivity)
                            .setTitle(R.string.dialog_exit_while_running_as_service_title)
                            .setMessage(R.string.dialog_exit_while_running_as_service_message)
                            .setPositiveButton(R.string.yes, (d, i) -> {
                                doExit();
                            })
                            .setNegativeButton(R.string.no, (d, i) -> {})
                            .show();
                } else {
                    // App is not running as a service.
                    doExit();
                }
                mActivity.closeDrawer();
                break;
        }
    }

    private Boolean doExit() {
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        Log.i(TAG, "Exiting app on user request");
        mActivity.stopService(new Intent(mActivity, SyncthingService.class));
        mActivity.finish();
        return true;
    }

    /**
     * Receives result of SettingsActivity.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SETTINGS_SCREEN_REQUEST && resultCode == SettingsActivity.RESULT_RESTART_APP) {
            Log.d(TAG, "Got request to restart MainActivity");
            if (doExit()) {
                startActivity(new Intent(getActivity(), MainActivity.class));
            }
        }
    }
}
