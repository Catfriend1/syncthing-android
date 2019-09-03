
package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;

/**
 * Provides preference getters and setters.
 */
public class AppPrefs {
    private static final String TAG = "AppPrefs";

    private static final Boolean PREF_VERBOSE_LOG_DEFAULT = false;

    public static final boolean getPrefVerboseLog(Context context) {
        if (context == null) {
            Log.e(TAG, "getPrefVerboseLog: context == null");
            return PREF_VERBOSE_LOG_DEFAULT;
        }
        return getPrefVerboseLog(PreferenceManager.getDefaultSharedPreferences(context));
    }

    public static final boolean getPrefVerboseLog(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            Log.e(TAG, "getPrefVerboseLog: sharedPreferences == null");
            return PREF_VERBOSE_LOG_DEFAULT;
        }
        return sharedPreferences.getBoolean(Constants.PREF_VERBOSE_LOG, PREF_VERBOSE_LOG_DEFAULT);
    }
}
