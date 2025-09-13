package com.nutomic.syncthingandroid;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.NotificationHandler;

/**
 * Simple service locator for dependency management.
 * Replaces Dagger dependency injection with manual dependency management.
 */
public class ServiceLocator {
    
    private final SyncthingApp mApp;
    private SharedPreferences mSharedPreferences;
    private NotificationHandler mNotificationHandler;
    
    public ServiceLocator(SyncthingApp app) {
        mApp = app;
    }
    
    /**
     * Get SharedPreferences instance.
     */
    public SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null) {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApp);
        }
        return mSharedPreferences;
    }
    
    /**
     * Get NotificationHandler instance.
     */
    public NotificationHandler getNotificationHandler() {
        if (mNotificationHandler == null) {
            mNotificationHandler = new NotificationHandler(mApp, getSharedPreferences());
        }
        return mNotificationHandler;
    }
}