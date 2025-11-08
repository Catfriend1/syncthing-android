package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

/**
 * Receives notifications from ntfy.sh app and triggers Syncthing sync cycle.
 * 
 * When ntfy.sh app receives a notification for this device's ID, it broadcasts
 * an intent with action "io.heckel.ntfy.MESSAGE_RECEIVED". We listen for this
 * and trigger a sync cycle if the topic matches our device ID.
 */
public class NtfyReceiver extends BroadcastReceiver {
    
    private static final String TAG = "NtfyReceiver";
    private static final String NTFY_ACTION = "io.heckel.ntfy.MESSAGE_RECEIVED";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast with action: " + action);
        
        if (action == null || !NTFY_ACTION.equals(action)) {
            Log.d(TAG, "Not a ntfy notification, ignoring");
            return;
        }
        
        // Log all extras for debugging
        if (intent.getExtras() != null) {
            Log.d(TAG, "Intent extras: " + intent.getExtras().toString());
        }
        
        // Extract notification data from ntfy.sh intent
        String topic = intent.getStringExtra("topic");
        String message = intent.getStringExtra("message");
        String title = intent.getStringExtra("title");
        
        Log.i(TAG, "Received ntfy notification: topic=" + topic + ", title=" + title + ", message=" + message);
        
        if (topic == null || topic.isEmpty()) {
            Log.w(TAG, "Received ntfy notification without topic, ignoring");
            return;
        }
        
        // Verify that the topic matches our local device ID
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String localDeviceId = prefs.getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        
        if (localDeviceId.isEmpty()) {
            Log.w(TAG, "Local device ID not available yet, ignoring ntfy notification");
            return;
        }
        
        if (!topic.equals(localDeviceId)) {
            Log.d(TAG, "Ntfy topic '" + topic + "' does not match local device ID '" + 
                    localDeviceId.substring(0, Math.min(7, localDeviceId.length())) + "...', ignoring");
            return;
        }
        
        Log.i(TAG, "Ntfy notification matches local device ID, triggering sync cycle");
        
        // Trigger a sync cycle by sending a local broadcast to RunConditionMonitor
        Intent syncTriggerIntent = new Intent(RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED);
        syncTriggerIntent.putExtra(RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(syncTriggerIntent);
        Log.i(TAG, "Sync trigger broadcast sent to RunConditionMonitor");
    }
}
