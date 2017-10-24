#!/usr/bin/env bash

set -e

MODULE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="${MODULE_DIR}/.."
TARGET_SDK=$(grep "targetSdkVersion" "${PROJECT_DIR}/app/build.gradle" -m 1 | awk '{print $2}')
# Use seperate build dir so standalone ndk isn't deleted by `gradle clean`
BUILD_DIR="${MODULE_DIR}/gobuild"
export GOPATH="${MODULE_DIR}/"

cd "${MODULE_DIR}/src/github.com/syncthing/syncthing"

# Make sure all tags are available for git describe
# https://github.com/syncthing/syncthing-android/issues/872
git fetch --tags

for ANDROID_ARCH in arm x86 arm64; do
    echo -e "Starting build for ${ANDROID_ARCH}\n"
    case ${ANDROID_ARCH} in
        arm)
            GOARCH=arm
            JNI_DIR="armeabi"
            GCC="arm-linux-androideabi-clang"
            ;;
        arm64)
            GOARCH=arm64
            JNI_DIR="arm64-v8a"
            GCC="aarch64-linux-android-clang"
            ;;
        x86)
            GOARCH=386
            JNI_DIR="x86"
            GCC="i686-linux-android-clang"
            ;;
        *)
            echo "Invalid architecture"
            exit 1
    esac

    # Build standalone NDK toolchain if it doesn't exist.
    # https://developer.android.com/ndk/guides/standalone_toolchain.html
    STANDALONE_NDK_DIR="${BUILD_DIR}/standalone-ndk/android-${TARGET_SDK}-${GOARCH}"

    if [ ! -d "$STANDALONE_NDK_DIR" ]; then
        echo -e "Building standalone NDK\n"
        ${ANDROID_NDK_HOME}/build/tools/make-standalone-toolchain.sh \
          --platform=android-${TARGET_SDK} --arch=${ANDROID_ARCH} \
          --install-dir=${STANDALONE_NDK_DIR}
    fi

    echo -e "Building Syncthing\n"
    CGO_ENABLED=1 CC="${STANDALONE_NDK_DIR}/bin/${GCC}" \
      go run build.go -goos android -goarch ${GOARCH} -pkgdir "${BUILD_DIR}/go-packages" -no-upgrade build

    # Copy compiled binary to jniLibs folder
    TARGET_DIR="${PROJECT_DIR}/app/src/main/jniLibs/${JNI_DIR}"
    mkdir -p ${TARGET_DIR}
    mv syncthing ${TARGET_DIR}/libsyncthing.so

    echo -e "Finished build for ${ANDROID_ARCH}\n"

done

echo -e "All builds finished"
