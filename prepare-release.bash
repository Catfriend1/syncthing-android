#!/bin/bash

set -e

NEW_VERSION_NAME=$1
OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle" | awk '{print $2}')
if [[ -z ${NEW_VERSION_NAME} ]]
then
    echo "New version name is empty. Please set a new version. Current version: $OLD_VERSION_NAME"
    exit
fi

echo "

Checking for Syncthing Update
-----------------------------
"
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "syncthing/src/github.com/syncthing/syncthing/"
git fetch
CURRENT_TAG=$(git describe)
# Also consider Syncthing rc releases if we are building beta or rc.
if [[ "$NEW_VERSION_NAME" == *beta* ]] || [[ "$NEW_VERSION_NAME" == *rc* ]]; then
    LATEST_TAG=$(git tag --sort=taggerdate | tail -1)
else
    LATEST_TAG=$(git tag --sort=taggerdate | awk '!/rc/' | tail -1)
fi

if [ ${CURRENT_TAG} != ${LATEST_TAG} ]; then
    git checkout -f ${LATEST_TAG}
    cd ${PROJECT_DIR}
    git add "syncthing/src/github.com/syncthing/syncthing/"
    git commit -m "Updated Syncthing to $LATEST_TAG"
    ./gradlew cleanNative buildNative
fi
cd ${PROJECT_DIR}


echo "

Updating Translations
-----------------------------
"
tx push -s
# Force push/pull to make sure this is executed. Apparently tx only compares timestamps, not file
# contents. So if a file was `touch`ed, it won't be updated by default.
# Use multiple transifex instances for pulling to speed things up.
#
# tx pull -a --mode reviewed -r "syncthing-android-1.stringsxml"
# tx pull -l de --mode reviewed -r "syncthing-android-1.stringsxml"
#
tx pull -a --mode reviewed -r "syncthing-android-1.stringsxml" &
tx pull -a --mode reviewed -r "syncthing-android-1.description_fulltxt" &
tx pull -a --mode reviewed -r "syncthing-android-1.description_shorttxt" &
tx pull -a --mode reviewed -r "syncthing-android-1.titletxt" &
wait
./gradlew deleteUnsupportedPlayTranslations
git add -A "app/src/main/play/"
git add -A "app/src/main/res/values-*/strings.xml"
if ! git diff --cached --exit-code;
then
    git commit -m "Imported translations"
fi


echo "

Running Lint
-----------------------------
"
./gradlew clean lintVitalRelease

echo "

Enter Changelog for $NEW_VERSION_NAME
-----------------------------
"
changelog_file="build/changelog.tmp"
touch ${changelog_file}
nano ${changelog_file}

cat ${changelog_file}
mv ${changelog_file} "app/src/main/play/en-GB/whatsnew"

echo "

Updating Version
-----------------------------
"
OLD_VERSION_CODE=$(grep "versionCode" "app/build.gradle" -m 1 | awk '{print $2}')
NEW_VERSION_CODE=$(($OLD_VERSION_CODE + 1))
sed -i "s/versionCode $OLD_VERSION_CODE/versionCode $NEW_VERSION_CODE/" "app/build.gradle"

OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle" | awk '{print $2}')
sed -i "s/$OLD_VERSION_NAME/\"$1\"/" "app/build.gradle"
git add "app/build.gradle" "app/src/main/play/en-GB/whatsnew"
git commit -m "Bumped version to $NEW_VERSION_NAME"
git tag ${NEW_VERSION_NAME}

echo "
Update ready.
"
