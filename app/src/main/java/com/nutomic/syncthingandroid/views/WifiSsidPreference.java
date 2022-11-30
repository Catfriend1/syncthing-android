package com.nutomic.syncthingandroid.views;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
        SharedPreferences sharedPreferences = getSharedPreferences();
        Set<String> knownSsids = new HashSet<String>(sharedPreferences.getStringSet(Constants.PREF_KNOWN_WIFI_SSIDS, new HashSet<>()));
        String currentWifiSsid = getCurrentWifiSsid();
        if (!TextUtils.isEmpty(currentWifiSsid)) {
            knownSsids.add(currentWifiSsid);
        }

        if (!knownSsids.isEmpty()) {
            sharedPreferences.edit()
                .putStringSet(Constants.PREF_KNOWN_WIFI_SSIDS, knownSsids)
                .apply();

            Set<String> selectedSsids = sharedPreferences.getStringSet(getKey(), new HashSet<>());

            // from JavaDoc: Note that you must not modify the set instance returned by this call.
            // therefore required to make a defensive copy of the elements.
            selectedSsids = new HashSet<>(selectedSsids);

            // Convert knownSsids including quotes.
            CharSequence[] all = knownSsids.toArray(new CharSequence[knownSsids.size()]);
            filterRemovedNetworks(selectedSsids, all);
            setEntries(stripQuotesFromSsid(knownSsids)); // display without surrounding quotes
            setEntryValues(all); // the value of the entry is the SSID "as is"
            setValues(selectedSsids); // the currently selected values (without meanwhile deleted networks)
            super.showDialog(state);
        } else {
            if (isLocationEnabled()) {
                Toast.makeText(getContext(), R.string.sync_only_wifi_ssids_wifi_turn_on_wifi, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), R.string.sync_only_wifi_ssids_wifi_turn_on_location, Toast.LENGTH_LONG).show();
            }
        }

        if (!haveLocationPermission()) {
            if (getContext() instanceof Activity) {
                Activity activity = (Activity) getContext();
                requestLocationPermission(activity);
            } else {
                Toast.makeText(getContext(), R.string.sync_only_wifi_ssids_need_to_grant_location_permission, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Required permissions:
     * - API 16 to 28
     *      permission.ACCESS_COARSE_LOCATIO
     * - API 29+ (Android 10+)
     *      permission.ACCESS_FINE_LOCATION
     *      permission.ACCESS_BACKGROUND_LOCATION
     */
    private final String getCurrentWifiSsid() {
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            // May be null, if wifi has been turned off.
            Log.d(TAG, "getCurrentWifiSsid: SSID unknown due to wifiInfo == null");
            return "";
        }
        String wifiSsid = wifiInfo.getSSID();
        if (wifiSsid == null || wifiSsid.equals("<unknown ssid>")) {
            Log.d(TAG, "getCurrentWifiSsid: Got null SSID. Try to enable android location service.");
            return "";
        }
        return wifiSsid;
    }

    /**
     * Removes any network that is no longer saved on the device. Otherwise it will never be
     * removed from the allowed set by MultiSelectListPreference.
     */
    private void filterRemovedNetworks(Set<String> selectedSsids, CharSequence[] all) {
        HashSet<CharSequence> availableNetworks = new HashSet<>(Arrays.asList(all));
        selectedSsids.retainAll(availableNetworks);
    }

    /**
     * Removes surrounding quotes which indicate that the SSID is an UTF-8
     * string and not a Hex-String, if the strings are intended to be displayed to the
     * user, who will not expect the quotes.
     *
     * @param ssids the objects to convert
     * @return the formatted SSID to display
     */
    private CharSequence[] stripQuotesFromSsid(Set<String> ssids) {
        CharSequence[] result = ssids.toArray(new CharSequence[ssids.size()]);
        for (int i = 0; i < result.length; i++) {
            result[i] = ((String) result[i]).replaceFirst("^\"", "").replaceFirst("\"$", "");
        }
        return result;
    }

    private boolean haveLocationPermission() {
        Boolean coarseLocationGranted = ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Boolean backgroundLocationGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationGranted = ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return coarseLocationGranted && backgroundLocationGranted;
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getContext().getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception ex) {
        }

        return (gpsEnabled || networkEnabled);
    }

    private void requestLocationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    },
                    Constants.PERM_REQ_ACCESS_LOCATION
            );
            return;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                Constants.PERM_REQ_ACCESS_LOCATION);
    }

}
