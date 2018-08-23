package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.FirstStartActivity;
import com.nutomic.syncthingandroid.activities.LogActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService.State;

import javax.inject.Inject;

public class NotificationHandler {

    private static final String TAG = "NotificationHandler";
    private static final int ID_PERSISTENT = 1;
    private static final int ID_PERSISTENT_WAITING = 4;
    private static final int ID_RESTART = 2;
    private static final int ID_STOP_BACKGROUND_WARNING = 3;
    private static final int ID_CRASH = 9;
    private static final int ID_MISSING_PERM = 10;
    private static final String CHANNEL_PERSISTENT = "01_syncthing_persistent";
    private static final String CHANNEL_INFO = "02_syncthing_notifications";
    private static final String CHANNEL_PERSISTENT_WAITING = "03_syncthing_persistent_waiting";

    private final Context mContext;
    @Inject SharedPreferences mPreferences;
    private final NotificationManager mNotificationManager;
    private final NotificationChannel mPersistentChannel;
    private final NotificationChannel mPersistentChannelWaiting;
    private final NotificationChannel mInfoChannel;

    public NotificationHandler(Context context) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPersistentChannel = new NotificationChannel(
                    CHANNEL_PERSISTENT, mContext.getString(R.string.notifications_persistent_channel),
                    NotificationManager.IMPORTANCE_MIN);
            mPersistentChannel.enableLights(false);
            mPersistentChannel.enableVibration(false);
            mPersistentChannel.setSound(null, null);
            mNotificationManager.createNotificationChannel(mPersistentChannel);

            mPersistentChannelWaiting = new NotificationChannel(
                    CHANNEL_PERSISTENT_WAITING, mContext.getString(R.string.notification_persistent_waiting_channel),
                    NotificationManager.IMPORTANCE_MIN);
            mPersistentChannelWaiting.enableLights(false);
            mPersistentChannelWaiting.enableVibration(false);
            mPersistentChannelWaiting.setSound(null, null);
            mNotificationManager.createNotificationChannel(mPersistentChannelWaiting);

            mInfoChannel = new NotificationChannel(
                    CHANNEL_INFO, mContext.getString(R.string.notifications_other_channel),
                    NotificationManager.IMPORTANCE_LOW);
            mPersistentChannel.enableVibration(false);
            mPersistentChannel.setSound(null, null);
            mNotificationManager.createNotificationChannel(mInfoChannel);
        } else {
            mPersistentChannel = null;
            mPersistentChannelWaiting = null;
            mInfoChannel = null;
        }
    }

    private NotificationCompat.Builder getNotificationBuilder(NotificationChannel channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new NotificationCompat.Builder(mContext, channel.getId());
        } else {
            //noinspection deprecation
            return new NotificationCompat.Builder(mContext);
        }
    }

    /**
     * Shows or hides the persistent notification based on running state and
     * {@link Constants#PREF_NOTIFICATION_TYPE}.
     */
    public void updatePersistentNotification(SyncthingService service) {
        String type = mPreferences.getString(Constants.PREF_NOTIFICATION_TYPE, "low_priority");

        // Always use startForeground() if app is set to always run. This makes sure the app
        // is not killed, and we don't miss wifi/charging events.
        // On Android 8, this behaviour is mandatory to receive broadcasts.
        // https://stackoverflow.com/a/44505719/1837158
        boolean foreground = mPreferences.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);

        // Foreground priority requires a notification so this ensures that we either have a
        // "default" or "low_priority" notification, but not "none".
        if ("none".equals(type) && foreground) {
            type = "low_priority";
        }

        State currentServiceState = service.getCurrentState();
        boolean syncthingRunning = currentServiceState == SyncthingService.State.ACTIVE ||
                    currentServiceState == SyncthingService.State.STARTING;
        if (foreground || (syncthingRunning && !type.equals("none"))) {
            int title = R.string.syncthing_terminated;
            switch (currentServiceState) {
                case ERROR:
                case INIT:
                    break;
                case DISABLED:
                    title = R.string.syncthing_disabled;
                    break;
                case STARTING:
                case ACTIVE:
                    title = R.string.syncthing_active;
                    break;
                default:
                    break;
            }

            /**
             * We no longer need to launch FirstStartActivity instead of MainActivity as
             * {@link SyncthingService#onStartCommand} will check for denied permissions.
             */
            Intent intent = new Intent(mContext, MainActivity.class);

            // Reason for two separate IDs: if one of the notification channels is hidden then
            // the startForeground() below won't update the notification but use the old one
            int idToShow = syncthingRunning ? ID_PERSISTENT : ID_PERSISTENT_WAITING;
            int idToCancel = syncthingRunning ? ID_PERSISTENT_WAITING : ID_PERSISTENT;
            NotificationChannel channel = syncthingRunning ? mPersistentChannel : mPersistentChannelWaiting;
            NotificationCompat.Builder builder = getNotificationBuilder(channel)
                    .setContentTitle(mContext.getString(title))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, 0));
            if (type.equals("low_priority"))
                builder.setPriority(NotificationCompat.PRIORITY_MIN);

            if (foreground) {
                service.startForeground(idToShow, builder.build());
            } else {
                service.stopForeground(false); // ensure no longer running with foreground priority
                mNotificationManager.notify(idToShow, builder.build());
            }
            mNotificationManager.cancel(idToCancel);
        } else {
            // ensure no longer running with foreground priority
            cancelPersistentNotification(service);
        }
    }

    public void cancelPersistentNotification(SyncthingService service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && alwaysRunInBackground())
            return;

        service.stopForeground(false);
        mNotificationManager.cancel(ID_PERSISTENT);
        mNotificationManager.cancel(ID_PERSISTENT_WAITING);
    }

    public void showCrashedNotification(@StringRes int title, String extraInfo) {
        Intent intent = new Intent(mContext, LogActivity.class);
        Notification n = getNotificationBuilder(mInfoChannel)
                .setContentTitle(mContext.getString(title, extraInfo))
                .setContentText(mContext.getString(R.string.notification_crash_text))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, 0))
                .setAutoCancel(true)
                .build();
        mNotificationManager.notify(ID_CRASH, n);
    }

    /**
     * Calculate a deterministic ID between 1000 and 2000 to avoid duplicate
     * notification ids for different device, folder consent popups triggered
     * by {@link EventProcessor}.
     */
    public int getNotificationIdFromText(String text) {
        return 1000 + text.hashCode() % 1000;
    }

    /**
     * Closes a notification. Required after the user hit an action button.
     */
    public void cancelConsentNotification(int notificationId) {
        if (notificationId == 0) {
            return;
        }
        Log.v(TAG, "Cancelling notification with id " + notificationId);
        mNotificationManager.cancel(notificationId);
    }

    /**
     * Used by {@link EventProcessor}
     */
    public void showConsentNotification(int notificationId,
                                        String text,
                                        PendingIntent piAccept,
                                        PendingIntent piIgnore) {
        /**
         * As we know the id for a specific notification text,
         * we'll dismiss this notification as it may be outdated.
         * This is also valid if the notification does not exist.
         */
        mNotificationManager.cancel(notificationId);
        Notification n = getNotificationBuilder(mInfoChannel)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(text))
                .setContentIntent(piAccept)
                .addAction(R.drawable.ic_stat_notify, mContext.getString(R.string.accept), piAccept)
                .addAction(R.drawable.ic_stat_notify, mContext.getString(R.string.ignore), piIgnore)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .build();
        mNotificationManager.notify(notificationId, n);
    }

    public void showStoragePermissionRevokedNotification() {
        Intent intent = new Intent(mContext, FirstStartActivity.class);
        Notification n = getNotificationBuilder(mInfoChannel)
                .setContentTitle(mContext.getString(R.string.syncthing_terminated))
                .setContentText(mContext.getString(R.string.toast_write_storage_permission_required))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, 0))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        mNotificationManager.notify(ID_MISSING_PERM, n);
    }

    public void showRestartNotification() {
        Intent intent = new Intent(mContext, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART);
        PendingIntent pi = PendingIntent.getService(mContext, 0, intent, 0);

        Notification n = getNotificationBuilder(mInfoChannel)
                .setContentTitle(mContext.getString(R.string.restart_title))
                .setContentText(mContext.getString(R.string.restart_notification_text))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pi)
                .build();
        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(ID_RESTART, n);
    }

    public void cancelRestartNotification() {
        mNotificationManager.cancel(ID_RESTART);
    }

    public void showStopSyncthingWarningNotification() {
        final String msg = mContext.getString(R.string.appconfig_receiver_background_enabled);
        NotificationCompat.Builder nb = getNotificationBuilder(mInfoChannel)
                .setContentText(msg)
                .setTicker(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setContentTitle(mContext.getText(mContext.getApplicationInfo().labelRes))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(mContext, 0,
                        new Intent(mContext, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            nb.setCategory(Notification.CATEGORY_ERROR);
        }
        mNotificationManager.notify(ID_STOP_BACKGROUND_WARNING, nb.build());
    }

    private boolean alwaysRunInBackground() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sp.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);
    }
}
