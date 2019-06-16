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
- Git (for Linux) or Git for Windows
`Make sure, git (or git.exe) is installed and available on the PATH environment variable. If Git is not found on the PATH environment variable, build-syncthing.py will automatically try to download and setup MinGit 2.19.0-x64 on the PATH.`

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


### gradle.properties
- Windows: Edit "%userprofile%\.gradle\gradle.properties" to make the build faster.

> org.gradle.jvmargs=-Xmx4096M

> android.enableSeparateAnnotationProcessing=true

> #android.debug.obsoleteApi=true
