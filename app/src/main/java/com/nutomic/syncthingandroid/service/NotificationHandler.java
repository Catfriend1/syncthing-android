package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.FirstStartActivity;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.activities.LogActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
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
    private final SharedPreferences mPreferences;
    private final NotificationManager mNotificationManager;
    private final NotificationChannel mPersistentChannel;
    private final NotificationChannel mPersistentChannelWaiting;
    private final NotificationChannel mInfoChannel;

    private String mLastNotificationText = null;
    private Boolean lastStartForegroundService = false;
    private Boolean appShutdownInProgress = false;

    private int FLAG_IMMUTABLE = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;

    // Dagger2 constructor injection - receives SharedPreferences directly to avoid circular dependency
    public NotificationHandler(Context context, SharedPreferences preferences) {
        mContext = context;
        mPreferences = preferences;
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPersistentChannel = new NotificationChannel(
                    CHANNEL_PERSISTENT, mContext.getString(R.string.notifications_persistent_channel),
                    NotificationManager.IMPORTANCE_MIN);
            mPersistentChannel.enableLights(false);
            mPersistentChannel.enableVibration(false);
            mPersistentChannel.setSound(null, null);
            mPersistentChannel.setShowBadge(false);
            mPersistentChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET);
            mNotificationManager.createNotificationChannel(mPersistentChannel);

            mPersistentChannelWaiting = new NotificationChannel(
                    CHANNEL_PERSISTENT_WAITING, mContext.getString(R.string.notification_persistent_waiting_channel),
                    NotificationManager.IMPORTANCE_MIN);
            mPersistentChannelWaiting.enableLights(false);
            mPersistentChannelWaiting.enableVibration(false);
            mPersistentChannelWaiting.setSound(null, null);
            mPersistentChannelWaiting.setShowBadge(false);
            mPersistentChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET);
            mNotificationManager.createNotificationChannel(mPersistentChannelWaiting);

            mInfoChannel = new NotificationChannel(
                    CHANNEL_INFO, mContext.getString(R.string.notifications_other_channel),
                    NotificationManager.IMPORTANCE_LOW);
            mInfoChannel.enableVibration(false);
            mInfoChannel.setSound(null, null);
            mInfoChannel.setShowBadge(true);
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
     * Shows, updates or hides the notification.
     */
    public void updatePersistentNotification(SyncthingService service) {
        // Persist previous notification details.
        updatePersistentNotification(service, true, 0, 0);
    }

    public void updatePersistentNotification(SyncthingService service,
                                                    Boolean persistNotificationDetails,
                                                    int onlineDeviceCount,
                                                    int totalSyncCompletion) {
        boolean startServiceOnBoot = mPreferences.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
        State currentServiceState = service.getCurrentState();
        boolean syncthingRunning = currentServiceState == SyncthingService.State.ACTIVE ||
                    currentServiceState == SyncthingService.State.STARTING;
        boolean startForegroundService = false;
        if (!appShutdownInProgress) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                /**
                 * Android 7 and lower:
                 * The app may run in background and monitor run conditions even if it is not
                 * running as a foreground service. For that reason, we can use a normal
                 * notification if syncthing is DISABLED.
                 */
                startForegroundService = startServiceOnBoot || syncthingRunning;
            } else {
                /**
                 * Android 8+:
                 * Always use startForeground.
                 * This makes sure the app is not killed, and we don't miss run condition events.
                 * On Android 8+, this behaviour is mandatory to receive broadcasts.
                 * https://stackoverflow.com/a/44505719/1837158
                 * Foreground priority requires a notification so this ensures that we either have a
                 * "default" or "low_priority" notification, but not "none".
                 */
                 startForegroundService = true;
            }
        }

        // Check if we have to stopForeground.
        if (startForegroundService != lastStartForegroundService) {
            if (!startForegroundService) {
                Log.v(TAG, "Stopping foreground service");
                service.stopForeground(false);
            }
        }

        // Prepare notification builder.
        String text;
        switch (currentServiceState) {
            case ERROR:
            case INIT:
                text = mContext.getString(R.string.syncthing_terminated);
                break;
            case DISABLED:
                text = mContext.getString(R.string.syncthing_disabled);
                break;
            case STARTING:
                text = mContext.getString(R.string.syncthing_starting);
                break;
            case ACTIVE:
                if (mLastNotificationText == null || !persistNotificationDetails) {
                    if (totalSyncCompletion == -1) {
                        mLastNotificationText = mContext.getString(
                                R.string.syncthing_active_details,
                                mContext.getString(R.string.no_remote_devices_connected)
                        );
                    } else if (totalSyncCompletion == 100) {
                        mLastNotificationText = mContext.getString(
                                R.string.syncthing_active_details,
                                mContext.getResources().getQuantityString(
                                        R.plurals.device_online_up_to_date,
                                        onlineDeviceCount,
                                        onlineDeviceCount
                                )
                        );
                    } else {
                        mLastNotificationText = mContext.getResources().getQuantityString(
                                R.plurals.syncthing_active_syncing_device_online,
                                onlineDeviceCount,
                                totalSyncCompletion,
                                onlineDeviceCount
                        );
                    }
                }
                text = mLastNotificationText;
                break;
            default:
                text = mContext.getString(R.string.syncthing_terminated);
                break;
        }

        /**
         * Reason for two separate IDs: if one of the notification channels is hidden then
         * the startForeground() below won't update the notification but use the old one.
         */
        int idToShow = syncthingRunning ? ID_PERSISTENT : ID_PERSISTENT_WAITING;
        int idToCancel = syncthingRunning ? ID_PERSISTENT_WAITING : ID_PERSISTENT;
        
        Intent openAppIntent = new Intent(mContext, MainActivity.class);
        
        Intent exitIntent = new Intent(mContext, MainActivity.class);
        exitIntent.setAction(MainActivity.ACTION_EXIT);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(
                    mContext,
                    0,
                    exitIntent,
                    FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        NotificationChannel channel = syncthingRunning ? mPersistentChannel : mPersistentChannelWaiting;
        NotificationCompat.Builder builder = getNotificationBuilder(channel)
                .setContentTitle(text)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(PendingIntent.getActivity(mContext, 0, openAppIntent, FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.baseline_close_24, mContext.getString(R.string.exit), exitPendingIntent);
        if (!appShutdownInProgress) {
            if (startForegroundService) {
                Log.v(TAG, "Starting foreground service or updating notification");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    service.startForeground(idToShow, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    service.startForeground(idToShow, builder.build());
                }
            } else {
                Log.v(TAG, "Updating notification");
                mNotificationManager.notify(idToShow, builder.build());
            }
        } else {
            mNotificationManager.cancel(idToShow);
        }
        mNotificationManager.cancel(idToCancel);

        // Remember last notification visibility.
        lastStartForegroundService = startForegroundService;
    }

    /**
     * Called by {@link SyncthingService#onStart} {@link SyncthingService#onDestroy}
     * to indicate app startup and shutdown.
     */
    public void setAppShutdownInProgress(Boolean newValue) {
        appShutdownInProgress = newValue;
    }

    public void showCrashedNotification(@StringRes int title, String extraInfo) {
        Intent intent = new Intent(mContext, LogActivity.class);
        Notification n = getNotificationBuilder(mInfoChannel)
                .setContentTitle(mContext.getString(title, extraInfo))
                .setContentText(mContext.getString(R.string.notification_crash_text, extraInfo))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, FLAG_IMMUTABLE))
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
                .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        mNotificationManager.notify(ID_MISSING_PERM, n);
    }

    public void cancelRestartNotification() {
        mNotificationManager.cancel(ID_RESTART);
    }

    public void showDeviceConnectNotification(String deviceId,
                                                    String deviceName,
                                                    String deviceAddress) {
        if (deviceId == null) {
            Log.e(TAG, "showDeviceConnectNotification: deviceId == null");
            return;
        }
        String title = mContext.getString(R.string.device_rejected,
                deviceName.isEmpty() ? deviceId.substring(0, 7) : deviceName);
        int notificationId = getNotificationIdFromText(title);

        // Prepare "accept" action.
        Intent intentAccept = new Intent(mContext, DeviceActivity.class)
                .putExtra(DeviceActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(DeviceActivity.EXTRA_IS_CREATE, true)
                .putExtra(DeviceActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(DeviceActivity.EXTRA_DEVICE_NAME, deviceName);
        PendingIntent piAccept = PendingIntent.getActivity(mContext, notificationId,
            intentAccept, FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId)
                .putExtra(SyncthingService.EXTRA_DEVICE_NAME, deviceName)
                .putExtra(SyncthingService.EXTRA_DEVICE_ADDRESS, deviceAddress);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_DEVICE);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        showConsentNotification(notificationId, title, piAccept, piIgnore);
    }

    public void showFolderShareNotification(String deviceId,
                                                String deviceName,
                                                String folderId,
                                                String folderLabel,
                                                Boolean receiveEncrypted,
                                                Boolean remoteEncrypted,
                                                Boolean isNewFolder) {
        if (deviceId == null) {
            Log.e(TAG, "showFolderShareNotification: deviceId == null");
            return;
        }
        if (folderId == null) {
            Log.e(TAG, "showFolderShareNotification: folderId == null");
            return;
        }
        String title = mContext.getString(R.string.folder_rejected, deviceName,
                folderLabel.isEmpty() ? folderId : folderLabel + " (" + folderId + ")");
        int notificationId = getNotificationIdFromText(title);

        // Prepare "accept" action.
        Intent intentAccept = new Intent(mContext, FolderActivity.class)
                .putExtra(FolderActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(FolderActivity.EXTRA_IS_CREATE, isNewFolder)
                .putExtra(FolderActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(FolderActivity.EXTRA_FOLDER_ID, folderId)
                .putExtra(FolderActivity.EXTRA_FOLDER_LABEL, folderLabel)
                .putExtra(FolderActivity.EXTRA_RECEIVE_ENCRYPTED, receiveEncrypted)
                .putExtra(FolderActivity.EXTRA_REMOTE_ENCRYPTED, remoteEncrypted);
        PendingIntent piAccept = PendingIntent.getActivity(mContext, notificationId,
            intentAccept, FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId)
                .putExtra(SyncthingService.EXTRA_FOLDER_ID, folderId)
                .putExtra(SyncthingService.EXTRA_FOLDER_LABEL, folderLabel);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_FOLDER);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        showConsentNotification(notificationId, title, piAccept, piIgnore);
    }
}
