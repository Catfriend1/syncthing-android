Syncthing-Fork "Wrapper for Syncthing" has three release channels:

1. [GitHub](https://github.com/Catfriend1/syncthing-android/releases/latest) release page, e. g. com.github.catfriend1.syncthingandroid_1.0.0.1_7d59e75.apk

2. F-Droid client or [website](https://f-droid.org/packages/com.github.catfriend1.syncthingandroid/), e. g. com.github.catfriend1.syncthingandroid_fdroid_1.0.0.1_7d59e75.apk

3. [Google Play Store](https://play.google.com/store/apps/details?id=com.github.catfriend1.syncthingandroid), e. g. com.github.catfriend1.syncthingandroid_gplay_1.0.0.1_7d59e75.apk

The signing on these release channels differ, so if you wish to change to a different channel:

* Run existing app installation
  * Open the drawer on the left side > Import & Export > Export configuration
* Uninstall app
* Install app from your preferred release channel
* Run app
  * Complete the welcome wizard
  * Open the drawer on the left side > Import & Export > Import configuration

To verify your downloaded APK, compare the certificate hash of the APK to the one's listed below. It has to match one of them to indicate you have a genuine version of the app.

1. GitHub APK: dQAnHXvlh80yJgrQUCo6LAg4294=

2. F-Droid APK: nyupq9aU0x6yK8RHaPra5GbTqQY=

3. Google Play APK: dQAnHXvlh80yJgrQUCo6LAg4294=

Here is a quick way of getting the certificate hash out of an APK file on Linux:

* keytool -printcert -jarfile "/path/to/release.apk" | grep "SHA1: " | cut -d " " -f 3 | xxd -r -p | openssl base64
