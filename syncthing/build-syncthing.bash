#!/usr/bin/env bash

set -e

if [ -z "${ANDROID_NDK_HOME}" ]; then
    echo "Please set ANDROID_NDK_HOME environment variable"
    exit 1
fi

MODULE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="${MODULE_DIR}/.."
MIN_SDK=$(grep "minSdkVersion" "${PROJECT_DIR}/app/build.gradle" -m 1 | awk '{print $2}')
# Use seperate build dir so standalone ndk isn't deleted by `gradle clean`
BUILD_DIR="${MODULE_DIR}/gobuild"
GO_BUILD_DIR="${BUILD_DIR}/go-packages"
export GOPATH="${MODULE_DIR}/"

if [ "${OSTYPE}" = "cygwin" ]; then
    export GOPATH=`cygpath -w ${GOPATH}`
    GO_BUILD_DIR=`cygpath -w ${GO_BUILD_DIR}`
fi

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
            # arm64 requires at least API 21.
            MIN_SDK=21
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

    if [ -z "${SYNCTHING_ANDROID_PREBUILT}" ]; then
      # Build standalone NDK toolchain if it doesn't exist.
      # https://developer.android.com/ndk/guides/standalone_toolchain.html
      STANDALONE_NDK_DIR="${BUILD_DIR}/standalone-ndk/android-${MIN_SDK}-${GOARCH}"
      PKG_ARGUMENT="-pkgdir ${GO_BUILD_DIR}"
    else
      # The environment variable indicates the SDK and stdlib was prebuilt, set a custom paths.
      STANDALONE_NDK_DIR="${ANDROID_NDK_HOME}/standalone-ndk/android-${MIN_SDK}-${GOARCH}"
      PKG_ARGUMENT=""
    fi

    if [ ! -d "$STANDALONE_NDK_DIR" ]; then
        if ! which python &>/dev/null; then
            echo "Could not find python"
            exit 1
        fi

        # We can't build the NDK with Cygwin/MinGW provided python, check that python is reporting a sensible host system.
        # Also, strip \r as that is returned by Windows python.
        HOST_PLATFORM=`python -c 'import platform; print platform.system()' | tr -d '\r'`

        case ${HOST_PLATFORM} in
            Windows|Linux|Darwin)
                ;;
            *)
                echo "Cannot build NDK with Python provided by an unsupported system: ${HOST_PLATFORM}"
                echo "Please make sure that python that is available on the path is native to your host platform (Windows/Linux/Darwin)"
                exit 1
                ;;
        esac

        if [ "${OSTYPE}" = "cygwin" ]; then
            STANDALONE_NDK_DIR=`cygpath -w ${STANDALONE_NDK_DIR}`
        fi

        echo -e "Building standalone NDK\n"
        ${ANDROID_NDK_HOME}/build/tools/make-standalone-toolchain.sh \
          --platform=android-${MIN_SDK} --arch=${ANDROID_ARCH} \
          --install-dir=${STANDALONE_NDK_DIR}
    fi

    echo -e "Building Syncthing\n"
    CGO_ENABLED=1 CC="${STANDALONE_NDK_DIR}/bin/${GCC}" \
      go run build.go -goos android -goarch ${GOARCH} $PKG_ARGUMENT -no-upgrade build

    # Copy compiled binary to jniLibs folder
    TARGET_DIR="${PROJECT_DIR}/app/src/main/jniLibs/${JNI_DIR}"
    mkdir -p ${TARGET_DIR}
    mv syncthing ${TARGET_DIR}/libsyncthing.so

    echo -e "Finished build for ${ANDROID_ARCH}\n"

done

echo -e "All builds finished"
