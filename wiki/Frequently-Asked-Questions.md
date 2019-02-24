### How can I access the config and key files?

Use the import/export items in the app settings. This will use the folder `/sdcard/backups/syncthing/` for files. The actual files are stored in `/data/data/com.github.catfriend1.syncthingandroid/files/` which can only be accessed with root.

### Persistent notification - required or optional?
The persistent notification is necessary to run a so called foreground service to avoid the app being put asleep by Android and missing run condition changes or synchronization activity. See [#333](https://github.com/Catfriend1/syncthing-android/issues/333) and [#327](https://github.com/Catfriend1/syncthing-android/issues/327) for details. While some users reported a foreground service necessary since Android 8+ and others reported it's working without we are in the same mess here in-between differently behaving, manufacturer specific Android versions that we cannot ensure it working for all users when we would remove the persistent notification. That's why it's required and cannot be configured in Syncthing-Fork as we want the app to work out of the box for all users out there. Other popular apps, like Linphone and Telegram FOSS use the same technique to ensure not being interrupted by the Android OS.

### What about SD card support?

Syncthing can not write to external SD cards, but there are a few known workarounds:
* If you format the external SD Card as internal storage, the SD card will be the new `/storage/emulated/0/` and will be encrypted and only usable in that phone.
* If you want a "Send Only" folder, you can create an empty file named `.stfolder` in the folder.
* Create your destination folders under Syncthing's application-specific folder, e.g. `/storage/emulated/0/Android/data/com.github.catfriend1.syncthingandroid/files` or `/storage/emulated/0/Android/media/com.github.catfriend1.syncthingandroid`
* If on a rooted phone running Nougat or Marshmallow, try using [this Xposed module](https://play.google.com/store/apps/details?id=com.balamurugan.marshmallowsdfix) to give Syncthing permission to write to the SD card.

Implementing this would involve a lot of work, and so far, no one was willing to program this. If you want to help with this and are familiar with Go, Java and Android, read through the discussion in issues [#29](https://github.com/syncthing/syncthing-android/issues/29) and [#1008](https://github.com/syncthing/syncthing-android/issues/1008).

### Where are the logs?

You can find the Syncthing logs in the app settings ("Debug" section). Additionally, logs are saved to the primary storage, under `Android/data/com.github.catfriend1.syncthingandroid/files/syncthing.log`.

### How to access the web interface from a browser?

Open the URL at `http://localhost:8384`. You will be prompted to accept the SSL certificate. The username is `syncthing`, and the password is Syncthing's API key (you can find the API key in the settings in the web interface).

### Sync as Root?

Syncing files with root permissions is supported. Using root privileges can be enabled in Settings > Experimental. Using root, you can sync folders on your external storage.
Misconfigured sync folders pointing to Android's system paths may brick your phone or prevent it from booting.

You should not attempt to sync the following files or folders:
- /data *1)
- /storage/abcd-efgh/Android *1)
- /storage/abcd-efgh/Android/com.github.catfriend1.syncthingandroid/files/syncthing.log *2)
- /storage/abcd-efgh/WhatsApp *2)

*1) If you'd like to backup app data, you can use third-party apps like TitaniumBackup or oandrbackup. Syncthing is not designed to replace an os specific backup utility.

*2) Syncing constantly changing files like logs or databases is not supported.


### How can I automate Syncthing?

Syncthing-Android can be started and stopped by other apps with intents. See [Remote Control by Broadcast Intents](Remote-Control-by-Broadcast-Intents) for instructions.
