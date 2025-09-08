# Integrate your app with Syncthing

### Receive notification after a folder completed its sync progress

You can receive the folder sync complete event from Syncthing-Fork by using a BroadcastReceiver to subscribe.

Prerequisites:
- Declare our permission in your "AndroidManifest.xml"
```
<uses-permission android:name="com.github.catfriend1.syncthingfork.permission.RECEIVE_SYNC_STATUS" />
```

- Add a receiver to your "AndroidManifest.xml"
```
<receiver android:name=".SyncStatusReceiver" android:exported="true">
    <intent-filter>
        <action android:name="com.github.catfriend1.syncthingfork.ACTION_NOTIFY_FOLDER_SYNC_COMPLETE" />
    </intent-filter>
</receiver>
```

- File a pull request against "service/RestApi.java" to let us add your app's package id as a receiver for the status broadcasts. Refer to function ["sendBroadcastToApps"](https://github.com/Catfriend1/syncthing-android/blob/main/app/src/main/java/com/nutomic/syncthingandroid/service/RestApi.java), "packageIdList".
```
String[] packageIdList = {
    "com.example.syncthingreceiver"
};
```

Implementation:
- Wait for the desired notification to arrive in your receiver code.
```
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
```

If you have trouble, compare your implementation to the "SyncthingReceiverTestApp":
- Test app: https://github.com/Catfriend1/syncthing-android/releases/download/v1.29.6.4/SyncthingReceiverTestApp.apk

- Test app source: https://github.com/Catfriend1/syncthing-android/tree/main/scripts/debug/SyncthingReceiverTestApp
```
# git clone ...
cd "scripts/debug/SyncthingReceiverTestApp/"
#
# Build test app
./gradlew assembleDebug
```

- Start test app and follow the logcat while Syncthing-Fork is running and about to complete a folder sync progress.
```
# adb logcat v:* * | grep 'SyncStatusReceiver'
05-12 23:59:37.143 D setRemoteCompletionInfo: Completed folder=[android_sdk_built_for_x86_64_u3dz-photos]
05-12 23:59:37.293 D SyncStatusReceiver: Received broadcast
05-12 23:59:37.294 D SyncStatusReceiver: deviceId: RF3FVSV-***
05-12 23:59:37.294 D SyncStatusReceiver: folderId: android_sdk_built_for_x86_64_u4dz-photos
05-12 23:59:37.294 D SyncStatusReceiver: folderLabel: Android Camera
05-12 23:59:37.299 D SyncStatusReceiver: folderPath: /storage/emulated/0/DCIM
05-12 23:59:37.301 D SyncStatusReceiver: folderState: idle
```
