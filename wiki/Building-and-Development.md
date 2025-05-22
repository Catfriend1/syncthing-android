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
winget install --accept-source-agreements --source winget --exact --id "Python.Python.3.13" --scope machine -h --override "/quiet InstallAllUsers=1 PrependPath=1 Include_doc=0 Include_launcher=0 Include_pip=0 Include_tcltk=0 Include_test=0"
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
