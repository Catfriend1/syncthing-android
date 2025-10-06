nVidia Shield TV's Android OS seem to lack the "regular" battery optimization consent and settings dialogs as they are known from mobile phones and tablets. Because of the missing functionality, you can't setup Syncthing-Fork on those TV's without making some steps to prepare the installation of the Syncthing-Fork app first.

The following instructions to solve the problem by preparing the Android TV for running the app have been contributed by @o-l-a-v :

* Enable Developer options (rapidly tab build info inside Settings > About)
* Enable "USB debugging" and then "Network debugging" inside Developer options
* Wait for ip:port to show
* Locate / Download Google platform-tools, navigate to folder where adb.exe resides. Open Command Prompt here
* Type the following:

    ${applicationId} = com.github.catfriend1.syncthingfork

```
    adb start-server
    adb connect ip:port
    # example: adb connect 192.168.1.76:5555
```
* Enable "Allow Network debugging" on the prompt that pops up on the nVidia Shield Android TV
* Then type the following to check weather you are successfully connected. Should state one device, and "connected"
```
    adb devices
```
* To whitelist Syncthing-Fork from doze / Android Battery Optimization, type the following
```
    adb shell dumpsys deviceidle whitelist +${applicationId}
```
* If you ever want to revert this change, type the following in ADB
```
    adb shell dumpsys deviceidle whitelist -${applicationId}
```
Related:
- [nVidia GeForce forum topic](https://forums.geforce.com/default/topic/1092750)
