#!/bin/bash

if [ -z "$SYNCTHING_ANDROID_PREBUILT" ]; then
    echo "Prebuild disabled"
    rm -rf syncthing-android
    exit 0
fi

echo "Prepopulating gradle and go build/pkg cache"
cd syncthing-android
./gradlew --no-daemon lintDebug buildNative
cd ..
rm -rf syncthing-android
