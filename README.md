# Syncthing-Fork - A Syncthing Wrapper for Android

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
<a href="https://github.com/Catfriend1/syncthing-android/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/Catfriend1/syncthing-android/all.svg" /></a>
<a href="https://f-droid.org/packages/com.github.catfriend1.syncthingandroid" alt="F-Droid release"><img src="https://img.shields.io/f-droid/v/com.github.catfriend1.syncthingandroid.svg" /></a>
<a href="https://tooomm.github.io/github-release-stats/?username=Catfriend1&repository=syncthing-android" alt="GitHub Stats"><img src="https://img.shields.io/github/downloads/Catfriend1/syncthing-android/total.svg" /></a>
<a href="https://hosted.weblate.org/projects/syncthing/android/catfriend1/"><img src="https://hosted.weblate.org/widget/syncthing/android/catfriend1/svg-badge.svg" alt="Translation status" /></a>

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android. Head to the "releases" section or F-Droid for builds. Please open an issue under this fork if you need help. Important: Please don't file bugs at the upstream repository "[syncthing/syncthing-android](https://github.com/syncthing/syncthing-android)" if you are using this fork.

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png" alt="screenshot 1" width="200" />

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/2.png" alt="screenshot 2" width="200" />

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/4.png" alt="screenshot 3" width="200" />

## I am no longer publishing this on Google Play

"nel0x" has announced to continue publishing on the play store. I welcome his help and the work I think he'll put into his mission. It's up to you, reading this, to decide if to trust and/or support him or go with the F-Droid release channel of this app instead. You don't know me and I don't know him... we are all volunteers in the spirit of open source.

## Major enhancements in this fork are

- Folder, device and overall sync progress can easily be read off the UI.
- "Syncthing Camera" - an optional feature (with optional permission to use the camera) where you can take pictures with your friend, partner, ... on two phones into one shared and private Syncthing folder. No cloud involved. (deprecated)
- "Sync every hour" to save even more battery
- Individual sync conditions can be applied per device and per folder (for expert users).
- Recent changes UI, click to open files.
- Changes to folder and device config can be made regardless if syncthing is running or not.
- UI explains why syncthing is running or not.
- "Battery eater" problem is fixed.
- Discover other Syncthing devices on the same network and easily add them.
- Supports two-way synchronization on external sd cards since Android 11.
- Supports encrypted folders on untrusted devices.
- Partial Android 15+ support regarding the run condition monitor due to Android restrictions. Feel free to PR and help here :-).

## Switching from the (now deprecated) official version

Switching is easier than you may think!

- On Syncthing on the official app, go into the settings and create a backup.
- Confirm you can see that backup in your files.
- Now stop the official app entirely using the system app settings for Syncthing (force stop the app basically - we need to ensure it's not running).
- Now start Syncthing-Fork.
- In the Syncthing-Fork settings, restore the backup you created earlier.
- Like magic, everything should be as it was in Syncthing official.
- Confirm everything looks good.
- Uninstall the official Syncthing app.
- Delete the syncthing configuration backup from `backups/syncthing`.

## Privacy Policy

See our document on privacy: [privacy-policy.md](https://github.com/Catfriend1/syncthing-android/blob/main/privacy-policy.md).

## Goal of the forked version

- Develop and try out enhancements together
- Release the wrapper more frequently to identify and fix bugs together caused by changes in the syncthing submodule
- Make enhancements configurable in the settings UI, e.g. users should be able to turn them on and off
- Let's get ready for newer Android versions that put limits on background syncing tools. We need your bug reports as detailed as possible

## Building

### Build on Debian Linux / WSL - recommended way

A Linux VM, for example running Debian, is recommended to build this.

Build SyncthingNative and the Syncthing-Android wrapper using the following commands:

```bash
#
# Install prerequisites.
apt-get -y install gcc git openjdk-17-jdk python3 unzip
#
# Clone repository.
mkdir -p ~/git && cd ~/git
git clone https://github.com/Catfriend1/syncthing-android.git --recursive
## git stash && git pull origin Catfriend1-patch-1 && git checkout Catfriend1-patch-1
#
# Build
cd ~/git/syncthing-android
python3 install_minimum_android_sdk_prerequisites.py
./gradlew buildNative
export ANDROID_HOME=~/git/syncthing-android-prereq
echo -e "\norg.gradle.jvmargs=-Xmx4096m" >> gradle.properties
./gradlew lintDebug
./gradlew assembleDebug
```

To clean up all files generated during build, use the following commands:

```bash
./gradlew cleanNative
./gradlew clean
```

### Build on Windows

```bash
::
:: Install prerequisites.
winget install --accept-source-agreements --source winget --exact --id "Git.MinGit" --scope machine
winget install --accept-source-agreements --source winget --exact --id "AdoptOpenJDK.OpenJDK.17" --scope machine
winget install --accept-source-agreements --source winget --exact --id "Python.Python.3.9" --scope machine -h --override "/quiet InstallAllUsers=1 PrependPath=1 Include_test=0" 
::
:: Clone repository.
git clone https://github.com/Catfriend1/syncthing-android.git --recursive
::
:: Build
cd /d "YOUR_CLONED_GIT_ROOT"
SyncthingNative_update_and_build
App_build_and_release
```

## Development Notes

The Syncthing native used for this android application provides a web interface by default. It can be accessed via the Settings menu -> 'Web GUI'. It is quite helpful to access this web interface from your development machine. Read android documentation on how to access the network of your emulator. Or use the following command to connect to the single currently running emulator/AVD.

```bash
adb forward tcp:18384 tcp:8384
```

Start Syncthing app on your emulator and access the web interface from you favorite browser of your development machine via [`127.0.0.1:18384`](https://127.0.0.1:18384)

## License

The project is licensed under [MPLv2](LICENSE).
