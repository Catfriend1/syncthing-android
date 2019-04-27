HMD Global phones have been reported to kill apps with a manufacturer-specific implementation called "DuraSpeed". DuraSpeed is built-into the OS and normally cannot be disabled by the user. This causes Syncthing-Fork to silently cease syncing when DuraSpeed kills the app.

To work around this, connect your phone to a computer running ADB.
If you like to allow Syncthing-Fork to attempt to disable DuraSpeed system-wide when your phone boots, issue the following commands:

* adb shell pm grant com.github.catfriend1.syncthingandroid android.permission.WRITE_SECURE_SETTINGS
* adb shell pm grant com.github.catfriend1.syncthingandroid.debug android.permission.WRITE_SECURE_SETTINGS

If you're unhappy with Syncthing-Fork turning of DuraSpeed and you want to revert the change, issue the following commands:

* adb shell pm revoke com.github.catfriend1.syncthingandroid android.permission.WRITE_SECURE_SETTINGS
* adb shell pm revoke com.github.catfriend1.syncthingandroid.debug android.permission.WRITE_SECURE_SETTINGS

Reboot your phone after the "pm revoke" for the change to take effect.

Related:
- [GitHub issue](https://github.com/Catfriend1/syncthing-android/issues/404)
- [Details from the urbandroid-team tracker](https://github.com/urbandroid-team/dont-kill-my-app/issues/57)
