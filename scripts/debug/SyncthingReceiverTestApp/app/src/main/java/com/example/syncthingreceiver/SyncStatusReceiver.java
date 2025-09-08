package com.example.syncthingreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SyncStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.github.catfriend1.syncthingfork.ACTION_NOTIFY_FOLDER_SYNC_COMPLETE".equals(action)) {
            Log.d("SyncStatusReceiver", "Received broadcast");
            Log.d("SyncStatusReceiver", "deviceId: " + intent.getStringExtra("deviceId"));
            Log.d("SyncStatusReceiver", "folderId: " + intent.getStringExtra("folderId"));
            Log.d("SyncStatusReceiver", "folderLabel: " + intent.getStringExtra("folderLabel"));
            Log.d("SyncStatusReceiver", "folderPath: " + intent.getStringExtra("folderPath"));
            Log.d("SyncStatusReceiver", "folderState: " + intent.getStringExtra("folderState"));
        }
    }
}
