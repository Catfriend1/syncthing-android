# How to use this

1. Build the docker image: `docker build -t syncthing-android-builder:latest .`
2. Checkout syncthing-android somewhere (for the sake of discussion let's say /tmp/syncthing-android)
3. Inside /tmp/syncthing-android, do `git submodule init; git submodule update`
4. Run `docker run --rm -v /tmp/syncthing-android:/mnt syncthing-android-builder ./gradlew buildNative assembleDebug`
5. Retrieve APKs from /tmp/syncthing-android/app/build/outputs
