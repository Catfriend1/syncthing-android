# syncthing-android

[![Build Status](https://travis-ci.org/syncthing/syncthing-android.svg?branch=master)](https://travis-ci.org/syncthing/syncthing-android)
[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
[![Bountysource](https://api.bountysource.com/badge/tracker?tracker_id=1183310)](https://www.bountysource.com/teams/syncthing-android)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android.

<img src="src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_1.png" alt="screenshot 1" width="200" />
<img src="src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_2.png" alt="screenshot 2" width="200" />
<img src="src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_3.png" alt="screenshot 3" width="200" />

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid) [<img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="80">](https://f-droid.org/app/com.nutomic.syncthingandroid)

# Translations

The project is translated on [Transifex](https://www.transifex.com/projects/p/syncthing-android/).

Translations can be updated using the [Transifex client](http://docs.transifex.com/developer/client/), using commands `tx push -s` and `tx pull -a`.

# Building

### Requirements
- Android SDK Platform (for the `compileSdkVersion` specified in [build.gradle](build.gradle))
- Android NDK Platform
- Android Support Repository

### Build instructions

This repository is using external dependencies so you have to initialize all submodules with --recursive option first time: `git clone https://github.com/syncthing/syncthing-android.git --recursive`.

Set the `ANDROID_NDK` environment variable to the Android NDK folder (e.g. `export ANDROID_NDK=/opt/android_ndk`).
Build Go and Syncthing using `./make-all.bash`.
Use `./gradlew assembleDebug` in the project directory to compile the APK.

To prepare a new release, execute `./prepare-release.bash`, and follow the instructions.

To check for updated gradle dependencies, run `gradle dependencyUpdates`. Additionally, the git submodule in `ext/syncthing/src/github.com/syncthing/syncthing` may need to be updated.


### Building on Windows

To build the Syncthing app on Windows we need to have cygwin installed.

From a cygwin shell in the project directory, build Go using `./make-go.bash [arch]`
After Go is built, compile syncthing using `./make-syncthing.bash [arch]`

Lastly, use `./gradlew assembleDebug` in the project directory to compile the APK,
or use Android Studio to build/deploy the APK.

# License

The project is licensed under the [MPLv2](LICENSE).
