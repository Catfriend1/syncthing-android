FROM debian:stable-slim

ENV DEBIAN_FRONTEND=noninteractive

# Can be found scrolling down on this page:
# https://developer.android.com/studio/index.html#command-tools
ARG ANDROID_SDK_FILENAME=commandlinetools-linux-11076708_latest.zip
WORKDIR /opt

RUN apt-get update && \
    apt-get install -y -q --no-install-recommends \
      autogen \
      automake \
      autopoint \
      bash \
      bzip2 \
      ca-certificates \
      curl \
      g++ \
      git \
      gnupg \
      golang-go \
      libc-dev \
      make \
      gettext \
      libtool \
      openjdk-21-jdk-headless \
      pkg-config \
      python3 \
      rename \
      shtool \
      unzip \
      wget && \
    apt-get clean

# Add go to PATH.
ENV GOROOT=/usr/lib/go
ENV PATH=$GOROOT/bin:$PATH

# Install Android SDK manager
RUN mkdir -p /opt/android-sdk && cd /opt/android-sdk && \
    wget -q https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME} && \
    unzip -q ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME}
ENV ANDROID_HOME=/opt/android-sdk

ARG SDKMANAGER="${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME}"

# Accept the SDK license, as we can't install packages otherwise
RUN yes | $SDKMANAGER --licenses > /dev/null

# Set location of go cache
ENV GOCACHE=/opt/gocache

# Set location of GOPATH to persist packages for module builds in GOPATH/pkg/mod
ENV GOPATH=/opt/gopath

# Run prebuild script (will prebuild stuff into the image if env var is set)
ADD . /opt/syncthing-android

RUN find /opt/syncthing-android -name "*.sh" -exec sed -i 's/\r$//' {} \;
RUN find /opt/syncthing-android -name "gradlew" -exec sed -i 's/\r$//' {} \;

# Prebuild go and SyncthingNative
RUN cd /opt/syncthing-android && \
    ./gradlew --no-daemon buildNative && \
    ./gradlew --no-daemon lintDebug

# Clean up build sources
RUN rm -rf /opt/syncthing-android

# Remove bootstrap go
RUN apt-get purge -y golang-go && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Use built go
ENV GOROOT=/opt/syncthing-android-prereq/go
ENV PATH=$GOROOT/bin:$PATH

WORKDIR /mnt
