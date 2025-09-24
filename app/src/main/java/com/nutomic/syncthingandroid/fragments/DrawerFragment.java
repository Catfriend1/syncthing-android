package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.RecentChangesActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.TipsAndTricksActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.service.Constants;
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

        /**
         * Show Web UI menu item on Android TV for debug builds only.
         * Reason: SyncthingNative's Web UI is not approved by Google because
         *          it is lacking full DPAD navigation support.
         */
        mDrawerActionWebGui.setVisibility((!mRunningOnTV || Constants.isDebuggable(getContext())) ? View.VISIBLE : View.GONE);

        // Enable buttons if syncthing is running.
        mDrawerRecentChanges.setEnabled(syncthingRunning);
        mDrawerActionWebGui.setEnabled(syncthingRunning);
        mDrawerActionRestart.setEnabled(syncthingRunning);
    }

    /**
     * Gets QRCode and displays it in a Dialog.
     */
    private void showQrCode() {
        String localDeviceID = PreferenceManager.getDefaultSharedPreferences(mActivity).getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(localDeviceID)) {
            Toast.makeText(mActivity, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show();
            return;
        }
        final int qrCodeSize = 232;
        Bitmap qrCodeBitmap = null;
        try {
            qrCodeBitmap = generateQrCodeBitmap(localDeviceID, qrCodeSize, qrCodeSize);
        } catch (WriterException | NullPointerException ex) {
            Log.e(TAG, "showQrCode: generateQrCodeBitmap failed", ex);
        }
        mActivity.showQrCodeDialog(localDeviceID, qrCodeBitmap);
        mActivity.closeDrawer();
    }

    private Bitmap generateQrCodeBitmap(String text, int width, int height) throws WriterException, NullPointerException {
        BitMatrix bitMatrix;
        try {
            bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE,
            width, height, null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        int bitMatrixWidth = bitMatrix.getWidth();
        int bitMatrixHeight = bitMatrix.getHeight();
        int[] pixels = new int[bitMatrixWidth * bitMatrixHeight];
        int colorWhite = 0xFFFFFFFF;
        int colorBlack = 0xFF000000;
        for (int y = 0; y < bitMatrixHeight; y++) {
            int offset = y * bitMatrixWidth;
            for (int x = 0; x < bitMatrixWidth; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? colorBlack : colorWhite;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_4444);
        bitmap.setPixels(pixels, 0, width, 0, 0, bitMatrixWidth, bitMatrixHeight);
        return bitmap;
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        int id = v.getId();
        if (id == R.id.drawerActionShowQrCode) {
            showQrCode();
        } else if (id == R.id.drawerActionRecentChanges) {
            startActivity(new Intent(mActivity, RecentChangesActivity.class));
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionWebGui) {
            startActivity(new Intent(mActivity, WebGuiActivity.class));
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionImportExport) {
            intent = new Intent(mActivity, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_OPEN_SUB_PREF_SCREEN, "category_import_export");
            startActivity(intent);
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionRestart) {
            mActivity.showRestartDialog();
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionTipsAndTricks) {
            startActivity(new Intent(mActivity, TipsAndTricksActivity.class));
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionSettings) {
            startActivityForResult(new Intent(mActivity, SettingsActivity.class), SETTINGS_SCREEN_REQUEST);
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionExit) {
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
                        .setNegativeButton(R.string.no, (d, i) -> {
                        })
                        .show();
            } else {
                // App is not running as a service.
                doExit();
            }
            mActivity.closeDrawer();
        }
    }

    private Boolean doExit() {
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        Log.i(TAG, "Exiting app on user request");
        mActivity.stopService(new Intent(mActivity, SyncthingService.class));
        mActivity.finishAndRemoveTask();
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
