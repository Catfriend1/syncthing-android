package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReceiverManager {

    private static final String TAG = "ReceiverManager";

    private static final Boolean ENABLE_VERBOSE_LOG = false;

    private static List<BroadcastReceiver> mReceivers = new ArrayList<BroadcastReceiver>();

    @SuppressWarnings("UnspecifiedRegisterReceiverFlag")
    public static synchronized void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter intentFilter) {
        mReceivers.add(receiver);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, intentFilter);
        }
        LogV("Registered receiver: " + receiver + " with filter: " + intentFilter);
    }

    public static synchronized boolean isReceiverRegistered(BroadcastReceiver receiver) {
        return mReceivers.contains(receiver);
    }

    public static synchronized void unregisterAllReceivers(Context context) {
        if (context == null) {
            Log.e(TAG, "unregisterReceiver: context is null");
            return;
        }
        Iterator<BroadcastReceiver> iter = mReceivers.iterator();
        while (iter.hasNext()) {
            BroadcastReceiver receiver = iter.next();
            if (isReceiverRegistered(receiver)) {
                try {
                    context.unregisterReceiver(receiver);
                    LogV("Unregistered receiver: " + receiver);
                } catch(IllegalArgumentException e) {
                    // We have to catch the race condition a registration is still pending in android
                    // according to https://stackoverflow.com/a/3568906
                    Log.w(TAG, "unregisterReceiver(" + receiver + ") threw IllegalArgumentException");
                }
                iter.remove();
            }
        }
    }

    private static void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
