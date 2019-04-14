package com.nutomic.syncthingandroid.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
// import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 */
public abstract class ThemedAppCompatActivity extends AppCompatActivity {

    // private static final String TAG = "ThemedAppCompatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load theme.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Integer prefAppTheme = Integer.parseInt(sharedPreferences.getString(Constants.PREF_APP_THEME, Constants.APP_THEME_LIGHT));
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);
        super.onCreate(savedInstanceState);
    }
}
