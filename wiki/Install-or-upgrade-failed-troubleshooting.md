Errors covered by this article:
```
INSTALL_FAILED_DUPLICATE_PERMISSION
(appName) conflicts with another package already installed
```

This error may occur on some Android systems while trying to update the app to v1.29.6.5 or higher.

Workaround:
- Open the Syncthing-Fork menu -> Import & Export -> Export config
- Open the Syncthing-Fork menu -> Exit
- Android settings -> Apps -> Syncthing-Fork -> Force stop
- Android settings -> Apps -> Syncthing-Fork -> Uninstall, do NOT keep data when Android asks (this refers to the app's config, not your synced folders)
- Install latest release
- Open the Syncthing-Fork menu -> Import & Export -> Import config

If that didn't solve the problem, you could try this workaround:
- Open the Syncthing-Fork menu -> Import & Export -> Export config
- Open the Syncthing-Fork menu -> Exit
- Connect your phone to a computer
- Phone
  - Android settings -> Enable Developer Options
  - Android settings -> Developer Options -> Enable USB Debugging
- Computer
  - Install ADB
  - Open command line to completely uninstall the app
```
adb uninstall com.github.catfriend1.syncthingandroid
adb uninstall com.github.catfriend1.syncthingandroid.debug
adb uninstall com.github.catfriend1.syncthingfork
adb uninstall com.github.catfriend1.syncthingfork.debug
```
- Phone
  - Install latest release
  - Open the Syncthing-Fork menu -> Import & Export -> Import config
