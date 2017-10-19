#!/usr/bin/env bash

set -e

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JNIDIR="/src/main/jniLibs"

case "$1" in
    arm)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=arm
        export GOARM=5
        export TARGETDIR=$MYDIR$JNIDIR/armeabi
        ;;
    arm64)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=arm64
        unset GOARM
        export TARGETDIR=$MYDIR$JNIDIR/arm64-v8a
        ;;
    386)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=386
        export GO386=387
        export TARGETDIR=$MYDIR$JNIDIR/x86
        ;;
    *)
        echo "Invalid architecture"
        exit 1
esac

unset GOPATH #Set by build.go
export GOROOT=${MYDIR}/ext/golang/dist/go-${GOOS}-${GOARCH}
export PATH=${GOROOT}/bin:${PATH}

case "$(uname)" in
    *CYGWIN*)
        export GOROOT=`cygpath -w $GOROOT`
        ;;
esac

pushd ext/syncthing/src/github.com/syncthing/syncthing

_GOOS=$GOOS
unset GOOS
_GOARCH=$GOARCH
unset GOARCH

# Make sure all tags are available for git describe
# https://github.com/syncthing/syncthing-android/issues/872
git fetch --tags

go run build.go -goos=${_GOOS} -goarch=${_GOARCH} clean
go run build.go -goos=${_GOOS} -goarch=${_GOARCH} -no-upgrade build

export GOOS=$_GOOS
export GOARCH=$_GOARCH

mkdir -p ${TARGETDIR}
mv syncthing ${TARGETDIR}/libsyncthing.so
chmod 644 ${TARGETDIR}/libsyncthing.so

if [[ -e ./build.go ]]; then
    git clean -f
fi

popd

echo "Build Complete"

