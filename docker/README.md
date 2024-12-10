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
