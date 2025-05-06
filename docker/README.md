# Fresh howto to build with Docker Desktop under WSL 2

## Add WSL feature
```
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```

## Reboot
```
shutdown -r -t 0
```

## Set WSL version 2
```
wsl --set-default-version 2
```

## Install Debian Linux
```
wsl.exe --install debian
wsl.exe -l -v
```

## Install Docker Desktop
- Download https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe
- Install
- logoff
- Relogon

## Docker Desktop
- Settings
- Resources
- Resources / Advanced
- Resources / Advanced / Disk image location
```
%LocalAppData%\docker\wsl
```
- Resources / Advanced / WSL integration
Enable integration with my default WSL distro
```
Debian
```

## Docker build step
- Open "cmd"
```
cd /d "syncthing-android"

docker build -t syncthing-android-builder:latest -f docker/Dockerfile .
docker run --rm -v "%userprofile%/.android/debug.keystore:/root/.android/debug.keystore:ro" -v .:/mnt syncthing-android-builder:latest ./gradlew buildNative assembleDebug
```


## Container insight
```
docker run -it --rm syncthing-android-builder bash
du -h -d 1 /
du -h -d 1 /opt
du -h -d 1 /root
```

## Destroy docker container
```
docker images
docker rmi syncthing-android-builder:latest
```


# How to use this

## Create the builder image

From inside the checked out syncthing-android repository, run:

`docker build -t syncthing-android-builder:latest -f ./docker/Dockerfile .`

## Build the app

1. From inside the checked out syncthing-android repository, run:
   `git submodule init; git submodule update`
2. Actual build:
   `docker run --rm -v /tmp/syncthing-android:/mnt syncthing-android-builder ./gradlew buildNative assembleDebug`
3. Retrieve APKs from ./app/build/outputs
