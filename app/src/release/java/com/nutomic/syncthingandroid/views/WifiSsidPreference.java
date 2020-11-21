package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.AttributeSet;
import android.util.Log;
import java.util.List;

public class WifiSsidPreference extends WifiSsidPreferenceBase {

    public WifiSsidPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WifiSsidPreference(Context context) {
        super(context, null);
    }

    private List<WifiConfiguration> wifiManager_getConfiguredNetworks(WifiManager wifiManager) {
        try {
            return wifiManager.getConfiguredNetworks();
        } catch (SecurityException e) {
            // See changes in Android Q, https://developer.android.com/reference/android/net/wifi/WifiManager.html#getConfiguredNetworks()
            Log.e(TAG, "getConfiguredWifiSsidsAPI16to28:", e);
            return null;
        }
    }

}
