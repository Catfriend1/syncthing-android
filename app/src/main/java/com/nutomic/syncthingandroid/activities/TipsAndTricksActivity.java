package com.nutomic.syncthingandroid.activities;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.views.TipListAdapter;
import com.nutomic.syncthingandroid.views.TipListAdapter.ItemClickListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Holds a RecyclerView that shows tips and tricks.
 */
public class TipsAndTricksActivity extends SyncthingActivity {

    private static final String TAG = "TipsAndTricksActivity";

    private RecyclerView mRecyclerView;
    private TipListAdapter mTipListAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tips_and_tricks);
        mRecyclerView = findViewById(R.id.tip_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mTipListAdapter = new TipListAdapter(this);

        /**
         * Determine the app's private data folder on external storage if present.
         * e.g. "/storage/abcd-efgh/Android/[PACKAGE_NAME]/files"
         */
        ArrayList<File> externalFilesDir = new ArrayList<>();
        externalFilesDir.addAll(Arrays.asList(getExternalMediaDirs()));
        if (externalFilesDir.size() > 0) {
            externalFilesDir.remove(externalFilesDir.get(0));
        }
        externalFilesDir.remove(null);      // getExternalFilesDirs may return null for an ejected SDcard.
        if (externalFilesDir.size() > 0) {
            String absExternalStorageAppFilesPath = externalFilesDir.get(0).getAbsolutePath();
            mTipListAdapter.add(getString(R.string.tip_write_to_sdcard_title),
                    getString(R.string.tip_write_to_sdcard_text, absExternalStorageAppFilesPath));
        }

        // Tips referring to Huawei devices.
        if ("huawei".equalsIgnoreCase(Build.MANUFACTURER)) {
            String ipAddress = getString(R.string.tip_phone_ip_address_syntax);
            if (isWifiOrEthernetConnection()) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            }
            mTipListAdapter.add(getString(R.string.tip_huawei_device_disconnected_title), getString(R.string.tip_huawei_device_disconnected_text, ipAddress));
        }

        // Tips referring to Xiaomi devices.
        if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) {
            mTipListAdapter.add(getString(R.string.tip_xiaomi_autostart_title), getString(R.string.tip_xiaomi_autostart_text));
            mTipListAdapter.add(getString(R.string.tip_xiaomi_battery_saver_title), getString(R.string.tip_xiaomi_battery_saver_text));
        }

        // Fill tip title and text content.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            mTipListAdapter.add(getString(R.string.tip_sync_on_local_network_title), getString(R.string.tip_sync_on_local_network_text));
        }
        mTipListAdapter.add(getString(R.string.tip_custom_sync_conditions_title), getString(R.string.tip_custom_sync_conditions_text));
        mTipListAdapter.add(getString(R.string.tip_ignore_delete_title), getString(R.string.tip_ignore_delete_text));

        // Set onClick listener and add adapter to recycler view.
        mTipListAdapter.setOnClickListener(
            new ItemClickListener() {
                @Override
                public void onItemClick(View view, String itemTitle, String itemText) {
                    Log.v(TAG, "User clicked item with title \'" + itemTitle + "\'");
                    /**
                     * Future improvement:
                     * Collapse texts to the first three lines and open a DialogFragment
                     * if the user clicks an item from the tip list.
                     */
                }
            }
        );
        mRecyclerView.setAdapter(mTipListAdapter);
    }

    private boolean isWifiOrEthernetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            return false;
        }
        if (!ni.isConnected()) {
            // No network connection.
            return false;
        }
        switch (ni.getType()) {
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_WIMAX:
            case ConnectivityManager.TYPE_ETHERNET:
                return true;
            default:
                return false;
        }
    }

}
