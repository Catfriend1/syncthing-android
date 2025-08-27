package com.nutomic.syncthingandroid.preferences;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import com.nutomic.syncthingandroid.activities.AnnounceServerActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;

public class AnnounceServerPreference extends Preference {

    public AnnounceServerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnnounceServerPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();
        Intent intent = new Intent(getContext(), AnnounceServerActivity.class);
        // Get the current value from SharedPreferences directly
        String currentValue = getSharedPreferences().getString("globalAnnounceServers", "");
        intent.putExtra(AnnounceServerActivity.EXTRA_ANNOUNCE_SERVERS, currentValue);
        // Use startActivityForResult so SettingsActivity can receive the result
        if (getContext() instanceof SettingsActivity) {
            ((SettingsActivity) getContext()).startActivityForResult(intent, SettingsActivity.REQUEST_CODE_ANNOUNCE_SERVERS);
        } else {
            getContext().startActivity(intent);
        }
    }
}