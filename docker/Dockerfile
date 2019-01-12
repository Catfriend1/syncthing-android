FROM openjdk:8

ENV GO_VERSION 1.9.7
ENV ANDROID_SDK_VERSION 3859397

WORKDIR /opt

# Install Go
RUN wget -q https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -zxf go${GO_VERSION}.linux-amd64.tar.gz && \
    rm go${GO_VERSION}.linux-amd64.tar.gz
ENV GOROOT /opt/go
ENV PATH /opt/go/bin:${PATH}

# Install Android SDK manager
RUN mkdir -p /opt/android-sdk && cd /opt/android-sdk && \
    wget -q https://dl.google.com/android/repository/sdk-tools-linux-${ANDROID_SDK_VERSION}.zip && \
    unzip -q sdk-tools-linux-${ANDROID_SDK_VERSION}.zip && \
    rm sdk-tools-linux-${ANDROID_SDK_VERSION}.zip
ENV ANDROID_HOME /opt/android-sdk

# Accept the SDK license, as we can't install packages otherwise
RUN yes | ${ANDROID_HOME}/tools/bin/sdkmanager --licenses

# Install other android packages, including NDK
RUN ${ANDROID_HOME}/tools/bin/sdkmanager tools platform-tools "build-tools;28.0.3" "platforms;android-28" "extras;android;m2repository" ndk-bundle

# Accept licenses of newly installed packages
RUN yes | ${ANDROID_HOME}/tools/bin/sdkmanager --licenses

# Setup the NDK path
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/ndk-bundle

# Enable prebuild mode
ENV SYNCTHING_ANDROID_PREBUILT 1

# Run prebuild script (will prebuild stuff into the image if env var is set)
ADD prebuild.sh /opt/prebuild.sh
RUN /opt/prebuild.sh

WORKDIR /mnt
