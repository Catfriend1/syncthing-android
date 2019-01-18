package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;
import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * SttracePreference which allows the user to select which debug facilities
 * are enabled.
 *
 * Setting can be "no debug facility" (none selected), or selecting individual  debug facilities.
 *
 * The preference is stored as Set&lt;String&gt; where an empty set represents
 * "no debug facility".
 *
 * Debug facilities are documented in https://docs.syncthing.net/dev/debugging.html
 *
 */
public class SttracePreference extends MultiSelectListPreference {

    private final String TAG = "SttracePreference";

    public SttracePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDefaultValue(new TreeSet<String>());
    }

    public SttracePreference(Context context) {
        this(context, null);
    }

    /**
     * Show the dialog.
     */
    @Override
    protected void showDialog(Bundle state) {
        Set<String> selected = getSharedPreferences().getStringSet(getKey(), new HashSet<>());
        // from JavaDoc: Note that you must not modify the set instance returned by this call.
        // therefore required to make a defensive copy of the elements
        selected = new HashSet<>(selected);
        CharSequence[] all = getDebugFacilities();
        filterRemovedDebugFacilities(selected, all);
        setEntries(all);        // display without surrounding quotes
        setEntryValues(all);    // the value of the entry is the debug facility "as is"
        setValues(selected);    // the currently selected values
        super.showDialog(state);
    }

    /**
     * Removes any debug facility that is no longer present in the current syncthing version.
     * Otherwise it will never be removed from the enabled facilities set by MultiSelectListPreference.
     */
    private void filterRemovedDebugFacilities(Set<String> selected, CharSequence[] all) {
        HashSet<CharSequence> availableDebugFacilities = new HashSet<>(Arrays.asList(all));
        selected.retainAll(availableDebugFacilities);
    }

    /**
     * Returns all debug facilities available in the currently syncthing version.
     */
    private CharSequence[] getDebugFacilities() {
        List<String> retDebugFacilities = new ArrayList<String>();
        Set<String> availableDebugFacilities = getSharedPreferences().getStringSet(Constants.PREF_DEBUG_FACILITIES_AVAILABLE, new HashSet<>());
        // from JavaDoc: Note that you must not modify the set instance returned by this call.
        // therefore required to make a defensive copy of the elements
        availableDebugFacilities = new HashSet<>(availableDebugFacilities);
        if (!availableDebugFacilities.isEmpty()) {
            for (String facilityName : availableDebugFacilities) {
                retDebugFacilities.add(facilityName);
            }
        } else {
            Log.w(TAG, "getDebugFacilities: Failed to get facilities from prefs, falling back to hardcoded list.");

            // Syncthing v0.14.47 debug facilities.
            retDebugFacilities.add("beacon");
            retDebugFacilities.add("config");
            retDebugFacilities.add("connections");
            retDebugFacilities.add("db");
            retDebugFacilities.add("dialer");
            retDebugFacilities.add("discover");
            retDebugFacilities.add("events");
            retDebugFacilities.add("fs");
            retDebugFacilities.add("http");
            retDebugFacilities.add("main");
            retDebugFacilities.add("model");
            retDebugFacilities.add("nat");
            retDebugFacilities.add("pmp");
            retDebugFacilities.add("protocol");
            retDebugFacilities.add("scanner");
            retDebugFacilities.add("sha256");
            retDebugFacilities.add("stats");
            retDebugFacilities.add("sync");
            retDebugFacilities.add("upgrade");
            retDebugFacilities.add("upnp");
            retDebugFacilities.add("versioner");
            retDebugFacilities.add("walkfs");
            retDebugFacilities.add("watchaggregator");
        }
        CharSequence[] retDebugFacilitiesArray = retDebugFacilities.toArray(new CharSequence[retDebugFacilities.size()]);
        Arrays.sort(retDebugFacilitiesArray);
        return retDebugFacilitiesArray;
    }

}
