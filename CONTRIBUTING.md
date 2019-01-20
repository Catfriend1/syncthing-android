## Reporting Bugs

Please file bugs in the [Github Issue
Tracker](https://github.com/Catfriend1/syncthing-android/issues). Bugs that
are not specific to the Syncthing-Fork wrapper should be reported to the
[upstream project](https://github.com/syncthing/syncthing/issues) instead.
Include at least the following in your issue report:

 - What happened

 - What did you expect to happen instead of what *did* happen, if it's
   not crazy obvious

 - What version of Android, Syncthing and Syncthing-Android you are
   running

 - Screenshot if the issue concerns something visible in the GUI

 - Console log entries, where possible and relevant

You can get logs in various ways:

 - Log window in the app: Settings -> Open Log -> Android Log

 - Install [adb](http://www.howtogeek.com/125769/how-to-install-and-use-abd-the-android-debug-bridge-utility/), 
   and run `adb logcat`. To see only info about crashes, run `adb logcat -s *:E`.

 - Using one of the various "logcat apps" on Google Play and F-Droid

## Contributing Code

Every contribution is welcome. If you want to contribute but are unsure
where to start, any open issues are fair game!

Code should follow the
[Android Code Style Guidelines](https://source.android.com/source/code-style.html#java-language-rules),
which are used by default in Android Studio.

Unit tests are available, and can be executed from Android Studio, or from
the terminal with `gradle connectedAndroidTest`.
