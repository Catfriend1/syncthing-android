#!/bin/bash
#
# copy_prebuilt_libs.sh
# Copy prebuilt .so files from Docker layer cache to jniLibs directory
# if they exist, to enable Docker Layer Caching for native builds.
#

set -e

# Define paths
PREBUILT_LIBS_DIR="${PREBUILT_LIBS_DIR:-/opt/syncthing-android-prereq/prebuilt-jnilibs}"
TARGET_LIBS_DIR="app/src/main/jniLibs"
ARCHITECTURES=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

echo "Checking for prebuilt libraries in Docker cache..."

# Check if prebuilt libs directory exists (F-Droid compatibility)
if [ ! -d "$PREBUILT_LIBS_DIR" ]; then
    echo "No prebuilt libraries directory found - will build from scratch"
    exit 0
fi

# Copy prebuilt libraries if they exist
found_libs=0
for arch in "${ARCHITECTURES[@]}"; do
    if [ -f "$PREBUILT_LIBS_DIR/$arch/libsyncthingnative.so" ]; then
        mkdir -p "$TARGET_LIBS_DIR/$arch"
        cp "$PREBUILT_LIBS_DIR/$arch/libsyncthingnative.so" "$TARGET_LIBS_DIR/$arch/"
        echo "Copied prebuilt library for $arch"
        found_libs=$((found_libs + 1))
    fi
done

if [ $found_libs -eq 0 ]; then
    echo "No prebuilt libraries found - will build from scratch"
else
    echo "Copied $found_libs prebuilt libraries from Docker cache"
fi