## Building

### Build on Debian Linux / WSL - recommended way

A Linux VM, for example running Debian, is recommended to build this.

Build SyncthingNative and the Syncthing-Android wrapper using the following commands:

```bash
#
# Install prerequisites.
apt-get -y install gcc git golang-go openjdk-21-jdk-headless python3 unzip
#
# Clone repository.
mkdir -p ~/git && cd ~/git
git clone https://github.com/Catfriend1/syncthing-android.git --recursive
#
# Install prerequisites.
cd ~/git/syncthing-android
python3 scripts/install_minimum_android_sdk_prerequisites.py
#
# Build.
export ANDROID_HOME=~/git/syncthing-android-prereq
./gradlew buildNative
#
# Flavor: debug
./gradlew lintDebug
./gradlew assembleDebug
#
# Flavor: release
## Remember to setup signing first
## or remove the section "signingConfigs" from "app/build.gradle.kts" for an unsigned build.
### sed -i -e '/signingConfig/,+2d' "app/build.gradle.kts"
./gradlew lintRelease
./gradlew assembleRelease
#
# Artifacts: Grab output APK.
## Flavor: debug
cp "app/build/outputs/apk/debug/app-debug.apk" "/mnt/x/app-debug.apk"
##
## Flavor: release
cp "app/build/outputs/apk/release/app-universal-release.apk" "/mnt/x/app-universal-release.apk"
##
## Flavor: release-unsigned
cp "app/build/outputs/apk/release/app-universal-release-unsigned.apk" "/mnt/x/app-universal-release-unsigned.apk"
#
# Cleanup.
## To clean up all files generated during build, use the following commands.
./gradlew cleanNative
./gradlew clean
```

### Build on Windows

You may want to add the following folder exceptions to your antivirus solution if the build process takes too long and you intend to develop regularly.

Exception list
```bash
%LocalAppData%\go-build
%userprofile%\.gradle\caches
```

build-windows.cmd
```bash
@echo off
::
:: Install prerequisites.
winget install --accept-source-agreements --source winget --exact --id "Git.MinGit" --scope machine
winget install --accept-source-agreements --source winget --exact --id "GoLang.Go" --scope machine
winget install --accept-source-agreements --source winget --exact --id "EclipseAdoptium.Temurin.21.JDK" --scope machine
winget install --accept-source-agreements --source winget --exact --id "Python.Python.3.13" --scope machine -h --override "/quiet InstallAllUsers=1 PrependPath=1 Include_doc=0 Include_launcher=0 Include_pip=0 Include_tcltk=0 Include_test=0"
::
:: Clone repository.
git clone https://github.com/Catfriend1/syncthing-android.git --recursive
::
:: Build
cd /d "YOUR_CLONED_GIT_ROOT"
call build
```

## Development Notes

The Syncthing native used for this android application provides a web interface by default. It can be accessed via the Settings menu -> 'Web GUI'. It is quite helpful to access this web interface from your development machine. Read android documentation on how to access the network of your emulator. Or use the following command to connect to the single currently running emulator/AVD.

```bash
adb forward tcp:18384 tcp:8384
```

Start Syncthing app on your emulator and access the web interface from you favorite browser of your development machine via [`127.0.0.1:18384`](https://127.0.0.1:18384)
