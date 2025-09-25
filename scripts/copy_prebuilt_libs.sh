#!/bin/bash
#
# copy_prebuilt_libs.sh
# Copy prebuilt .so files from Docker layer cache to jniLibs directory
# if they exist, to enable Docker Layer Caching for native builds.
#

set -e

# Define paths
PREBUILT_LIBS_DIR="${PREBUILT_LIBS_DIR:-/opt/syncthing-android/prebuilt-libs}"
TARGET_LIBS_DIR="app/src/main/jniLibs"
ARCHITECTURES=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

echo "Checking for prebuilt libraries in Docker cache..."

# Check if prebuilt libs directory exists
if [ ! -d "$PREBUILT_LIBS_DIR" ]; then
    echo "No prebuilt libraries found in Docker cache - will build from scratch"
    exit 0
fi

# Count available prebuilt libraries
found_libs=0
for arch in "${ARCHITECTURES[@]}"; do
    if [ -f "$PREBUILT_LIBS_DIR/$arch/libsyncthingnative.so" ]; then
        found_libs=$((found_libs + 1))
        echo "Found prebuilt library for $arch"
    fi
done

if [ $found_libs -eq 0 ]; then
    echo "No prebuilt libraries found for any architecture - will build from scratch"
    exit 0
fi

echo "Found $found_libs prebuilt libraries, copying to $TARGET_LIBS_DIR"

# Create target directory if it doesn't exist
mkdir -p "$TARGET_LIBS_DIR"

# Copy prebuilt libraries to target location
for arch in "${ARCHITECTURES[@]}"; do
    if [ -f "$PREBUILT_LIBS_DIR/$arch/libsyncthingnative.so" ]; then
        mkdir -p "$TARGET_LIBS_DIR/$arch"
        cp "$PREBUILT_LIBS_DIR/$arch/libsyncthingnative.so" "$TARGET_LIBS_DIR/$arch/"
        echo "Copied prebuilt library for $arch"
    fi
done

echo "Successfully copied $found_libs prebuilt libraries from Docker cache"