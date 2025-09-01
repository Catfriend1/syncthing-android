#!/bin/bash

if [ -z "$SYNCTHING_ANDROID_PREBUILT" ]; then
    echo "Prebuild disabled"
    rm -rf syncthing-android
    exit 0
fi

echo "Prepopulating gradle and go build/pkg cache"
cd syncthing-android
#
./gradlew --no-daemon buildNative || exit 1
./gradlew --no-daemon lintDebug || exit 1
#
cd ..
rm -rf syncthing-android
exit 0
