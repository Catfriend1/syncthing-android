### Run SyncthingNative as root

Using root privileges to run the Syncthing native binary can be enabled in Settings > Behaviour. Using root, you can sync folders on your external storage if your Android version does not support the "all files access" permission.

⚠️ Misconfigured sync folders pointing to Android's system paths may brick your phone or prevent it from booting.

You should not attempt to sync the following files or folders:
- `/data` *1)
- `/storage/emulated/0/Android/data` *1)
- `/storage/emulated/0/Android/media` *2)
- `/storage/emulated/0/WhatsApp` *2)

*1) If you'd like to backup apps or app data, you can use third-party apps like e.g. "App Manager". Syncthing is not designed to replace an os specific backup utility.

*2) Syncing constantly changing files like logs or databases is not supported. It may cause massive battery drain.
