package com.nutomic.syncthingandroid.views;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

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
        // Syncthing v0.14.47 debug facilities.
        List<String> debugFacilities = new ArrayList<String>();
        debugFacilities.add("beacon");
        debugFacilities.add("config");
        debugFacilities.add("connections");
        debugFacilities.add("db");
        debugFacilities.add("dialer");
        debugFacilities.add("discover");
        debugFacilities.add("events");
        debugFacilities.add("fs");
        debugFacilities.add("http");
        debugFacilities.add("main");
        debugFacilities.add("model");
        debugFacilities.add("nat");
        debugFacilities.add("pmp");
        debugFacilities.add("protocol");
        debugFacilities.add("scanner");
        debugFacilities.add("sha256");
        debugFacilities.add("stats");
        debugFacilities.add("sync");
        debugFacilities.add("upgrade");
        debugFacilities.add("upnp");
        debugFacilities.add("versioner");
        debugFacilities.add("walkfs");
        debugFacilities.add("watchaggregator");
        return debugFacilities.toArray(new CharSequence[debugFacilities.size()]);
    }

}
