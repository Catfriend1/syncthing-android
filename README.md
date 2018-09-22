# Fork of Syncthing-Android:

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
<a href="https://github.com/Catfriend1/syncthing-android/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/Catfriend1/syncthing-android/all.svg" /></a>
<a href="https://f-droid.org/de/packages/com.github.catfriend1.syncthingandroid" alt="F-Droid release"><img src="https://img.shields.io/badge/f--droid-4160-brightgreen.svg" /></a>

# Major enhancements in this fork are:
- UI explains why syncthing is running or not running according to the run conditions set in preferences.
- A welcome wizard guiding you through initial setup on first launch or if mandatory prerequisites are missing like for example the storage permission.
- Run condition bugs are fixed.
- "Battery eater" problem is fixed.
- Android 8+ support is on it's way.
- Many bug fixes, enhancements and more frequent releases.

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android. Head to the "releases" section for builds. Please open an issue under this fork if you need help. Important: Please don't file bugs at the upstream repository "syncthing-android" if you are using this fork.

<img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_12.png" alt="screenshot 1" width="200" /> <img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_11.png" alt="screenshot 2" width="200" /> <img src="app/src/main/play/en-GB/listing/phoneScreenshots/screenshot_phone_09.png" alt="screenshot 3" width="200" />

# Translations

The project is translated on [Transifex](https://www.transifex.com/projects/p/syncthing-android-1).

# Building

### Prerequisites
- Android SDK
`You can skip this if you are using Android Studio.`
- Android NDK r16b
`$ANDROID_NDK_HOME environment variable should point at the root directory of your NDK. If the variable is not set, build-syncthing.py will automatically try to download and setup the NDK.`
- Go 1.9.7
`Make sure, Go is installed and available on the PATH environment variable. If Go is not found on the PATH environment variable, build-syncthing.py will automatically try to download and setup GO on the PATH.`
- Python 2.7
`Make sure, Python is installed and available on the PATH environment variable.`

### Build instructions

Make sure you clone the project with
`git clone https://github.com/Catfriend1/syncthing-android.git --recursive`.
Alternatively, run `git submodule init && git submodule update` in the project folder.

A Linux VM, for example running Debian, is recommended to build this.

Build Syncthing and the Syncthing-Android wrapper using the following commands:
`./gradlew buildNative`
`./gradlew lint assembleDebug`

You can also use Android Studio to build the apk after you manually ran the `./gradlew buildNative` command in the repository root.

To clean up all files generated during build, use the following commands:
`./gradlew cleanNative`
`./gradlew clean`

# License

The project is licensed under the [MPLv2](LICENSE).
