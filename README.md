# Fork of Syncthing-Android:

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
<a href="https://github.com/Catfriend1/syncthing-android/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/Catfriend1/syncthing-android/all.svg" /></a>
<a href="https://f-droid.org/de/packages/com.github.catfriend1.syncthingandroid" alt="F-Droid release"><img src="https://img.shields.io/f-droid/v/com.github.catfriend1.syncthingandroid.svg" /></a>

# Major enhancements in this fork are:
- UI explains why syncthing is running or not running according to the run conditions set in preferences.
- A welcome wizard guiding you through initial setup on first launch or if mandatory prerequisites are missing like for example the storage permission.
- Run condition bugs are fixed.
- "Battery eater" problem is fixed.
- Android 8+ support is on it's way.
- Many bug fixes, enhancements and more frequent releases.

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android. Head to the "releases" section for builds. Please open an issue under this fork if you need help. Important: Please don't file bugs at the upstream repository "syncthing-android" if you are using this fork.

<img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_1.png" alt="screenshot 1" width="200" /> <img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_2.png" alt="screenshot 2" width="200" /> <img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_3.png" alt="screenshot 3" width="200" />

# Translations

The project is translated on [Transifex](https://www.transifex.com/projects/p/syncthing-android-1).

# Building

### Dependencies
- Android SDK (you can skip this if you are using Android Studio)
- Android NDK (`$ANDROID_NDK_HOME` should point at the root directory of your NDK)
- Go (see [here](https://docs.syncthing.net/dev/building.html#prerequisites) for the required version)

### Build instructions

Make sure you clone the project with
`git clone https://github.com/Catfriend1/syncthing-android.git --recursive`. Alternatively, run
`git submodule init && git submodule update` in the project folder.

A Linux VM, for example running Debian, is recommended to build this.

Build Syncthing using `./gradlew cleanNative buildNative`. Then use `./gradlew assembleDebug` or
Android Studio to build the apk.

# License

The project is licensed under the [MPLv2](LICENSE).
