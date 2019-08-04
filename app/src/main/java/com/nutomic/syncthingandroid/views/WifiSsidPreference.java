package com.nutomic.syncthingandroid.views;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * MultiSelectListPreference which allows the user to select on which WiFi networks (based on SSID)
 * syncing should be allowed.
 *
 * Setting can be "All networks" (none selected), or selecting individual networks.
 *
 * Due to restrictions in Android, it is possible/likely, that the list of saved WiFi networks
 * cannot be retrieved if the WiFi is turned off. In this case, an explanation is shown.
 *
 * The preference is stored as Set&lt;String&gt; where an empty set represents
 * "all networks allowed".
 *
 * SSIDs are formatted according to the naming convention of WifiManager, i.e. they have the
 * surrounding double-quotes (") for UTF-8 names, or they are hex strings (if not quoted).
 */
public class WifiSsidPreference extends MultiSelectListPreference {

    private static final String TAG = "WifiSsidPreference";

    public WifiSsidPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDefaultValue(new TreeSet<String>());
    }

    public WifiSsidPreference(Context context) {
        this(context, null);
    }

    /**
     * Show the dialog if WiFi is available and configured networks can be loaded.
     * Otherwise will display a Toast requesting to turn on WiFi.
     *
     * On opening of the dialog, will also remove any SSIDs from the set that have been removed
     * by the user in the WiFi manager. This change will be persisted only if the user changes
     * any other setting
     */
    @Override
    protected void showDialog(Bundle state) {
        WifiConfiguration[] networks = loadConfiguredNetworksSorted();
        if (networks != null) {
            Set<String> selected = getSharedPreferences().getStringSet(getKey(), new HashSet<>());
            // from JavaDoc: Note that you must not modify the set instance returned by this call.
            // therefore required to make a defensive copy of the elements
            selected = new HashSet<>(selected);
            CharSequence[] all = extractSsid(networks, false);
            filterRemovedNetworks(selected, all);
            setEntries(extractSsid(networks, true)); // display without surrounding quotes
            setEntryValues(all); // the value of the entry is the SSID "as is"
            setValues(selected); // the currently selected values (without meanwhile deleted networks)
            super.showDialog(state);
        } else {
            Toast.makeText(getContext(), R.string.sync_only_wifi_ssids_wifi_turn_on_wifi, Toast.LENGTH_LONG).show();
        }

        // On Android 8.1, ACCESS_COARSE_LOCATION is required, see issue #999
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (getContext() instanceof Activity) {
                    Activity activity = (Activity) getContext();
                    ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, Constants.PERM_REQ_ACCESS_COARSE_LOCATION);
                    this.getDialog().dismiss(); // wait for result
                } else {
                    Toast.makeText(getContext(), R.string.sync_only_wifi_ssids_need_to_grant_location_permission, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Removes any network that is no longer saved on the device. Otherwise it will never be
     * removed from the allowed set by MultiSelectListPreference.
     */
    private void filterRemovedNetworks(Set<String> selected, CharSequence[] all) {
        HashSet<CharSequence> availableNetworks = new HashSet<>(Arrays.asList(all));
        selected.retainAll(availableNetworks);
    }

    /**
     * Converts WiFi configuration to it's string representation, using the SSID.
     *
     * It can also remove surrounding quotes which indicate that the SSID is an UTF-8
     * string and not a Hex-String, if the strings are intended to be displayed to the
     * user, who will not expect the quotes.
     *
     * @param configs the objects to convert
     * @param stripQuotes if to remove surrounding quotes
     * @return the formatted SSID of the wifi configurations
     */
    private CharSequence[] extractSsid(WifiConfiguration[] configs, boolean stripQuotes) {
        CharSequence[] result = new CharSequence[configs.length];
        for (int i = 0; i < configs.length; i++) {
            // See #620: there may be null-SSIDs
            String ssid = configs[i].SSID != null ? configs[i].SSID : "";
            // WiFi SSIDs can either be UTF-8 (encapsulated in '"') or hex-strings
            if (stripQuotes) {
                result[i] = ssid.replaceFirst("^\"", "").replaceFirst("\"$", "");
            } else {
                result[i] = ssid;
            }
        }
        return result;
    }

    /**
     * Load the configured WiFi networks, sort them by SSID.
     *
     * @return a sorted array of WifiConfiguration, or null, if data cannot be retrieved
     */
    private WifiConfiguration[] loadConfiguredNetworksSorted() {
        WifiManager wifiManager = (WifiManager)
                getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            // WiFi is turned off or device doesn't have WiFi.
            return null;
        }

        List<WifiConfiguration> configuredNetworks = null;
        try {
            configuredNetworks = wifiManager.getConfiguredNetworks();
        } catch (SecurityException e) {
            // See changes in Android Q, https://developer.android.com/reference/android/net/wifi/WifiManager.html#getConfiguredNetworks()
            Log.e(TAG, "loadConfiguredNetworksSorted:", e);
        }
        if (configuredNetworks == null) {
            return null;
        }

        WifiConfiguration[] result = configuredNetworks.toArray(new WifiConfiguration[configuredNetworks.size()]);
        Arrays.sort(result, (lhs, rhs) -> {
            // See #620: There may be null-SSIDs
            String l = lhs.SSID != null ? lhs.SSID : "";
            String r = rhs.SSID != null ? rhs.SSID : "";
            return l.compareToIgnoreCase(r);
        });
        return result;
    }

}
