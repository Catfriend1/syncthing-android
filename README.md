# Syncthing-Fork - A Syncthing Wrapper for Android

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
<a href="https://github.com/Catfriend1/syncthing-android/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/Catfriend1/syncthing-android/all.svg" /></a>
<a href="https://f-droid.org/packages/com.github.catfriend1.syncthingandroid" alt="F-Droid release"><img src="https://img.shields.io/f-droid/v/com.github.catfriend1.syncthingandroid.svg" /></a>
<a href="https://tooomm.github.io/github-release-stats/?username=Catfriend1&repository=syncthing-android" alt="GitHub Stats"><img src="https://img.shields.io/github/downloads/Catfriend1/syncthing-android/total.svg" /></a>
<a href="https://hosted.weblate.org/projects/syncthing/android/catfriend1/"><img src="https://hosted.weblate.org/widget/syncthing/android/catfriend1/svg-badge.svg" alt="Translation status" /></a>[![Build App](https://github.com/Catfriend1/syncthing-android/actions/workflows/build-app.yaml/badge.svg)](https://github.com/Catfriend1/syncthing-android/actions/workflows/build-app.yaml)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android. Head to the "releases" section or F-Droid for builds. I'm currently short on time. Please seek help on the forum and/or social media apps first before consuming my time moderating issues on the tracker here. Thank you.

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png" alt="screenshot 1" width="200" /> · <img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/2.png" alt="screenshot 2" width="200" /> · <img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/4.png" alt="screenshot 3" width="200" />

## I am no longer publishing this on Google Play

See [detailed info](https://github.com/Catfriend1/syncthing-android/blob/main/wiki/Switch-between-releases_Verify-APK-is-genuine.md) about release variants and which is recommended to fit your needs.

"nel0x" has announced to continue publishing on the play store. I welcome his help and the work I think he'll put into his mission. It's up to you, reading this, to decide if to trust and/or support him or go with the F-Droid release channel of this app instead. You don't know me and I don't know him... we are all volunteers in the spirit of open source.

## Switching from the (now deprecated) official version

Switching is easier than you may think!

- On Syncthing on the official app, go into the settings and create a backup.
- Confirm you can see that backup in your files.
- Now stop the official app entirely using the system app settings for Syncthing (force stop the app basically - we need to ensure it's not running).
- Install Syncthing-Fork [v1.29.7.1](https://github.com/Catfriend1/syncthing-android/releases/tag/v1.29.7.1)
- Now start Syncthing-Fork.
- In the Syncthing-Fork settings, restore the backup you created earlier.
- Like magic, everything should be as it was in Syncthing official.
- Confirm everything looks good.
- Uninstall the official Syncthing app.
- Delete the syncthing configuration backup from `backups/syncthing`.
- Upgrade to the [latest Syncthing-Fork version](https://github.com/Catfriend1/syncthing-android/releases/latest)

## Privacy Policy

See our document on privacy: [privacy-policy.md](https://github.com/Catfriend1/syncthing-android/blob/main/privacy-policy.md).

## Building and Development Notes

See [detailed info](https://github.com/Catfriend1/syncthing-android/blob/main/wiki/Building-and-Development.md).

## License

The project is licensed under [MPLv2](LICENSE).
