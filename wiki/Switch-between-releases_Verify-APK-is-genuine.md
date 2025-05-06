Syncthing-Fork "Wrapper for Syncthing" has these release channels:

1. F-Droid release build

* "COMMON USER" - please choose this!
* Published on [F-Droid](https://f-droid.org/packages/com.github.catfriend1.syncthingandroid/) and [GitHub release page](https://github.com/Catfriend1/syncthing-android/releases/latest)
* File name is like: com.github.catfriend1.syncthingandroid_fdroid_1.29.6.0_7d59e75.apk
* Certificate hash: nyupq9aU0x6yK8RHaPra5GbTqQY=

2. GitHub release build

* If you don't like to use F-Droid for some reason, please choose this!
* Only published on [GitHub release page](https://github.com/Catfriend1/syncthing-android/releases/latest)
* File name is like: com.github.catfriend1.syncthingandroid_release_1.29.6.0_7d59e75.apk
* Certificate hash: dQAnHXvlh80yJgrQUCo6LAg4294=

3. Google Play release build

* Published by [nel0x](https://github.com/nel0x) at [Google Play Store](https://play.google.com/store/apps/details?id=com.github.catfriend1.syncthingandroid)
* May contain limited functionality due to play policies.
* It's an appetizer for your family and friends to start their Syncthing experience.
* File name is like: com.github.catfriend1.syncthingandroid_gplay_1.29.6.0_7d59e75.aab
* Certificate hash: dQAnHXvlh80yJgrQUCo6LAg4294=

4. GitHub debug build

* Only published on GitHub
* Can be obtained by looking at the [action workflow builds](https://github.com/Catfriend1/syncthing-android/actions) and artifacts.
* It's for maintainers, contributors who require a second installation of the app on their phone for testing purposes.
* File name is like: com.github.catfriend1.syncthingandroid_debug_1.29.6.0_7d59e75.apk
* Certificate hash: 2ScaPj41giu4vFh+Y7Q0GJTqwbA=


The signing on these release channels differ, so if you wish to change to a different channel:

* Run existing app installation
  * Open the drawer on the left side > Import & Export > Export configuration
* Uninstall app
* Install app from your preferred release channel
* Run app
  * Complete the welcome wizard
  * Open the drawer on the left side > Import & Export > Import configuration

To verify your downloaded APK, compare the certificate hash of the APK to the one's listed above. It has to match one of them to indicate you have a genuine version of the app. Here is a quick way of getting the certificate hash out of an APK file on Linux:

* keytool -list -printcert -jarfile "/path/to/release.apk" | grep "SHA1: " | cut -d " " -f 3 | xxd -r -p | openssl base64
