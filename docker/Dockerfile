FROM eclipse-temurin:11-jdk-jammy

ENV GO_VERSION 1.22.7

# Can be found scrolling down on this page:
# https://developer.android.com/studio/index.html#command-tools
ARG ANDROID_SDK_FILENAME=commandlinetools-linux-9123335_latest.zip
WORKDIR /opt

# The '--reinstall' combined with 'build-essentials' is necessary for cgo
# compilation of go std libs to work. A terrible, arcane hack to solve arkane
# cgo issues - almost fitting.
RUN apt-get update && apt-get install --reinstall --no-install-recommends -y build-essential gcc unzip git gnupg python3

# Install Go
RUN wget -nv https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -zxf go${GO_VERSION}.linux-amd64.tar.gz && \
    rm go${GO_VERSION}.linux-amd64.tar.gz
ENV GOROOT /opt/go
ENV PATH /opt/go/bin:${PATH}

# Install Android SDK manager
RUN mkdir -p /opt/android-sdk && cd /opt/android-sdk && \
    wget -q https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME} && \
    unzip -q ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME}
ENV ANDROID_HOME /opt/android-sdk

ARG SDKMANAGER="${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME}"

# Accept the SDK license, as we can't install packages otherwise
RUN yes | $SDKMANAGER --licenses > /dev/null

# NDK version
ENV NDK_VERSION 27.0.12077973
ENV TARGET_SDK_VERSION 35

# Install other android packages, including NDK
RUN $SDKMANAGER tools platform-tools "build-tools;${TARGET_SDK_VERSION}.0.0" "platforms;android-${TARGET_SDK_VERSION}" "extras;android;m2repository" "ndk;${NDK_VERSION}"

# Accept licenses of newly installed packages
RUN yes | $SDKMANAGER --licenses

# Enable prebuild mode
ENV SYNCTHING_ANDROID_PREBUILT 1

# Set location of go cache
ENV GOCACHE /opt/gocache

# Set location of GOPATH to persist packages for module builds in GOPATH/pkg/mod
ENV GOPATH /opt/gopath

# Run prebuild script (will prebuild stuff into the image if env var is set)
ADD . /opt/syncthing-android
RUN /opt/syncthing-android/docker/prebuild.sh

WORKDIR /mnt
