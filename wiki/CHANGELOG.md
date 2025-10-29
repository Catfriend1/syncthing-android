
---

## Syncthing-Fork v2.0.10.2 (2025-10-16)

## What's Changed
### Features
Add pref to allow overwrite existing files when another app shares to our app (#1704)

---

## Syncthing-Fork v2.0.10.1 (2025-09-29)

## What's Changed
### Features
Add floating exit button, for one-shot sync users (#1659)
Remove redundant buttons from device edit dialog (#1662)

---

## Syncthing-Fork v2.0.10.0 (2025-09-24)

## What's Changed
### Features
* fix(dagger): Replace circular dependency  in NotificationHandler #1658

---

## Syncthing-Fork v2.0.9.2 (2025-09-22)

This version supports v1 config import from the deprecated Syncthing official app.

---

## Syncthing-Fork v2.0.9.0 (2025-09-13)

## What's Changed
### Fixes
* SyncthingNative now takes care of cleaning up the old index-v1 database after migration to v2 (instead of the wrapper)

---

## Syncthing-Fork v2.0.8.1 (2025-09-09)

## What's Changed
### Fixes
* SyncthingService/exportConfig: Overwrite existing zip instead of appending files #1618
### Features
* Show QR code for existing paired device #1619
* SyncthingRunnable: Cleanup index-v0.14.0.db-migrated if dir exists #1622
* Settings: Add eye toggle to show/hide the import/export password #1621

---

## Syncthing-Fork v2.0.8.0 (2025-09-08)

---

## Syncthing-Fork v2.0.7.0 (2025-09-05)

## What's Changed
### Fixes
* NAT PMP permission denied by Android 14+ (#1514)
* Local discovery denied by Android 10+ (#1500)

---

## Syncthing-Fork v1.30.0.3 (2025-08-07)

## What's Changed
* Fixed UI crash if index sequence number for a folder exceeds a limit. This affected a minority of users, especially Syncthing-Lite users. But if it happened, users were unable to export config and could no longer open the app.

---

## Syncthing-Fork v1.30.0.2 F-Droid (2025-07-16)

Updated dependencies, build using Java 21. Functionally the same as v1.30.0.1. Released due to a change in the fdroid build process which allows us to build future releases off the release branch instead of using the separate fdroid branch. You can, but do not have to update if you already got v1.30.0.1-fdroid installed.
## What's Changed
### Other
* Move version-code, version-name to libs.versions.toml by @Catfriend1 in #1544

---

## Syncthing-Fork v1.30.0.1 (2025-07-01)

## What's Changed
### Fixes
* Fix crash referring to libsyncthingnative.so
* Fix duplicate .nomedia and DO_NOT_DELETE.txt files created if folder is readded by @Catfriend1 in #1515
* Fix executorService crash while exiting the app is in progress by @Catfriend1 in #1516
### Other
* Move wiki to repo folder /wiki, add actions builds for branches "accrescent" and "fdroid" by @Catfriend1 in #1508
* F-Droid builds: Use correct go version by @Catfriend1 in #1512
* Fix install_minimum_android_sdk_prerequisites.py by @Catfriend1 in #1513
* model/Folder: maxConcurrentWrites = 0 by @Catfriend1 in #1511
* Update SyncthingNative to v1.30.0 by @Catfriend1 in #1517

---

## Syncthing-Fork v1.29.7.5 (2025-06-23)

## What's Changed
### Fixes
* Edge2edge view improvements (fixes #1493) by @Catfriend1 in #1494
### Features
* Add "cleanOutDays" to simple file versioning dialog (fixes #1439) by @Catfriend1 in #1474
### Other
* Set BUILD_HOST in fdroiddata yml (fixes #1383) by @Catfriend1 in #1480
* build.gradle: static BUILD_HOST/BUILD_USER for reproducible builds by @eighthave in #1485
* Android 16 support targetApi 36: APK version 1.29.7.5 by @Catfriend1 in #1492
## New Contributors
* @eighthave made their first contribution in #1485
🤷‍♂️ Problems upgrading? See release notes of v1.29.6.5.

---

## Syncthing-Fork v1.29.7.4 F-Droid (2025-06-27)
Functionally the same as v1.29.7.3 with changes to support F-Droid's reproducible builds.

---

## Syncthing-Fork v1.29.7.3 (2025-06-06)

## What's Changed
* Disable android backup integration by @Catfriend1 in #1470
* After folders complete, run additional workloads in a seperate thread by @Catfriend1 in #1471

---

## Syncthing-Fork v1.29.7.2 (2025-06-04)

⚠️ Breaking change:
The config import and export feature now reads or writes the file "(InternalStorage)/backups/syncthing/config.zip". This file can be encrypted by a user-defined password to protect sensitive parts of your Syncthing configuration like, for example, device trust and which data you share with other devices. The change makes it harder for a bad actor to gain unauthorized access to your files using stolen config exports.

ℹ️ Action required:
- Please go to "Settings - Import and Export"
- Set an individual password, be sure to remember it
- Click "Export Configuration" and confirm
- Open your file manager, e.g. Material Files
- Browse to "(InternalStorage)/backups/syncthing"
- Delete all ".pem", ".xml" files because they contain an outdated and unencrypted config from prior exports.

⚡If you'd like to import an old and unencrypted config consisting of multiple files instead of the zip archive, please follow [these steps](migration/Switching-from-the-deprecated-official-version.md).

🤷‍♂️ Problems upgrading? See release notes of v1.29.6.5.

---

## Syncthing-Fork v1.29.7.1 (2025-06-03)

**Checkpoint release - Use this if migrating from the deprecated "syncthing-android" to this app.**

What's Changed:
- Improve log file share by file provider (fixes #1454) (#1459)

🤷‍♂️ Problems upgrading? See release notes of v1.29.6.5.

---

## Syncthing-Fork v1.29.7.0 (2025-06-02)

What's Changed:
- Updated SyncthingNative to v1.29.7
- Updated dependency

🤷‍♂️ Problems upgrading? See release notes of v1.29.6.5.

---

## Syncthing-Fork v1.29.6.6 (2025-06-01)

**Hotfix release** to address UI lags affecting some users. Only update if you have trouble with UI lags.

What's Changed:
- Fix web UI username null pointer exception (fixes #1450) (#1455) (thanks to @fran-tor)
- Do not create default folder on first launch according to user voice (fixes #1363) (#1458) (@Catfriend1)
- Updated dependencies

🤷‍♂️ Problems upgrading? See release notes of v1.29.6.5.

---

## Syncthing-Fork v1.29.6.5 (2025-05-22)

**Updated release signing key which was over 5 years old for security reasons.**
**To upgrade from a previous release:**

* Slide out the menu > Import and Export > Export config
* Exit app
* Uninstall app - if Android asks, do NOT keep data (this refers to app data, not your synced files)
* (Re-)Install app using the latest release
* Complete the welcome slides
* Slide out the menu > Import and Export > Import config

🤷‍♂️ Still problems? Find more help on the [wiki article](known-bug-workarounds/Install-or-upgrade-failed.md)

## What's Changed
* Fixed INSTALL_FAILED_DUPLICATE_PERMISSION (#1443) by @Catfriend1
* Fix conflicts between debug and release APK (fixes #1442) by @Catfriend1
* Allow debug and release version of the app to run in parallel for testing purposes by @Catfriend1
* Bump kotlin from 2.1.20 to 2.1.21 by @dependabot in #1432
* Update release key which was over 5 years old (fixes #1440) by @Catfriend1 in #1441

---

## Syncthing-Fork v1.29.6.4 (2025-05-17)

Upgrade notes:
The backup folder location UI preference on the settings screen is now relative to the internal storage root. Before, it was interpreted relative to the "(int-stor)/backups" folder. Please review that setting if you upgrade from a previous release.

* Fix missing library error on app start
* Show sync conflicts on the folder tab (fixes #1130) by @Catfriend1 in #1416
* Persist backup folder setting on restore, restoring from backup is not wise (fixes #1375) by @Catfriend1 in #1417
* Allow follow run conditions, force start/stop using service control broadcasts (fixes #1192) by @Catfriend1 in #1418
* Update dependencies, enable kotlin compiler, migrate to TOML versions config by @Catfriend1 in #1402
* Switch from oss-licenses-plugin to AboutLicenses plugin (fixes #1400) by @Catfriend1 in #1401
* Remove play publisher plugin and script by @Catfriend1 in #1403
* remove cfgOption insecureAllowOldTLSVersions by @Catfriend1 in #1404
* Bump androidx.documentfile:documentfile from 1.0.1 to 1.1.0 by @dependabot in #1408
* Detect when folder sync has been completed locally or remote by @Catfriend1 in #1406
* Send an intent when a shared folder finished syncing (fixes #1409) by @Catfriend1 in #1410
* Expert option: Run custom shell script after folder sync completed by @Catfriend1 in #1412
* model/Device: Add maxRecvKbps, maxSendKbps by @Catfriend1
* build-syncthing.py: Remove GO BUILDID by @Catfriend1 (fixes #1383)
* gradle/buildNative: Add verifySyncthingNativeVersionMatchesApp by @Catfriend1
* Updated settings about screen
* Update README.md by @Catfriend1 in PR #1379
* Let's differentiate between TRUSTED and UNTRUSTED debug builds. by @Catfriend1 in PR #1382
* Support floats for fsWatcherDelayS option (fixes #1365) by @Catfriend1 in PR #1384
* Add license info to Settings / About screen by @Catfriend1 in PR #1385
* Syncthing Web UI: default HOME env folder = /storage/emulated/0/syncthing (fixes #764) by @Catfriend1 in PR #1386
* Fix sdcard root path not offered in gear icon folder picker (fixes #1309) by @Catfriend1 in PR #1388
* Add forum and privacy policy links to Settings/About (fixes #1387) by @Catfriend1 in PR #1389
* Make release builds reproducible #698 (fixes #1383) by @Catfriend1 in PR #1390
* Fix SyncthingService going stuck because of improper bind_active_network by @Catfriend1 in PR #1394
* Add "empty versioning folders" button (fixes #603) by @Catfriend1 in PR #1395
* Create ".nomedia" in ".stversions" subfolder if a new folder is added (fixes #1396) by @Catfriend1 in PR #1397
* Offer "Movies" folder in gear-icon folder picker by @Catfriend1

---

## Syncthing-Fork v1.29.6.0 (2025-05-06)

Upgrade notes:
The backup folder location UI preference on the settings screen is now relative to the internal storage root. Before, it was interpreted relative to the "(int-stor)/backups" folder. Please review that setting if you upgrade from a previous release.

Notes:
We will now prefer CI builds over builds done on a personal device.
Debug APK signing was upgraded from SHA1 to SHA256 signing.
We are preparing for GPG signed release artifacts.

## What's Changed
* Update README.md by @Catfriend1 in #1373
* importConfig: Ignore outdated user pref (fixes #1375) by @Catfriend1 in #1376
* Bump the dagger group with 2 updates by @dependabot in #1370
* Update README.md by @Catfriend1 in #1378
* Update SyncthingNative to v1.29.6 / Update debug cert to SHA256 / Prepare GPG sign (fixes #1284) (fixes #1311) by @Catfriend1 in #1377

---

## Syncthing-Fork v1.29.4.0 (2025-04-03)

## What's Changed
* Add Hebrew and Galician translation templates by @acolomb in #1310
* Update Readme, NDK, Go and support build on WSL by @Catfriend1 in #1313
* chore(strings): shorten notification text (fixes #1113) by @marbens-arch in #1319
* Bump org.jetbrains.kotlin:kotlin-stdlib-jdk7 from 2.1.10 to 2.1.20 by @dependabot in #1324
* Bump org.jetbrains.kotlin:kotlin-stdlib-jdk8 from 2.1.10 to 2.1.20 by @dependabot in #1323
* Bump the dagger group with 2 updates by @dependabot in #1322
* Bump com.google.guava:guava from 33.4.0-android to 33.4.5-android by @dependabot in #1321
* Bring back launch into web gui (fixes #1331) by @Catfriend1 in #1334
* Fix MediaStore DB update (fixes #1330)
* Fix release builds crashing

---

## Syncthing-Fork v1.29.3.0 (2025-03-13)

## What's Changed
* Bump org.jetbrains.kotlin:kotlin-stdlib-jdk8 from 2.1.0 to 2.1.10 by @dependabot in #1262
* Bump com.github.ben-manes:gradle-versions-plugin from 0.51.0 to 0.52.0 by @dependabot in #1261
* Bump org.jetbrains.kotlin:kotlin-stdlib-jdk7 from 2.1.0 to 2.1.10 by @dependabot in #1260
* Bump androidx.recyclerview:recyclerview from 1.3.2 to 1.4.0 by @dependabot in #1250
* Add Ukranian (uk) translation template for Google Play. by @acolomb in #1245
* Bump the dagger group with 2 updates by @dependabot in #1239
* Delete unnecessary screenshots by @leoheitmannruiz in #1224
* Bump com.google.code.gson:gson from 2.11.0 to 2.12.1 by @dependabot in #1267
* Allow HTTP*S* URLs for the HTTP(S) proxy setting by @lenerd in #1276
* update GitHub APK hash and keytool command by @craslaw in #1279
* README: clarify that we are talking about the original by @marbens-arch in #1281
* Bump androidx.fragment:fragment from 1.8.5 to 1.8.6 by @dependabot in #1283

## New Contributors
* @lenerd made their first contribution in #1276
* @craslaw made their first contribution in #1279
* @marbens-arch made their first contribution in #1281

---

## Syncthing-Fork v1.29.2.0 (2025-01-19)

---

## Syncthing-Fork v1.29.0.0 (2025-01-06)

## What's Changed
* Bump androidx.preference:preference from 1.1.1 to 1.2.1 by @dependabot in #1218
* Bump androidx.core:core from 1.3.0 to 1.15.0 by @dependabot in #1219
* Bump com.journeyapps:zxing-android-embedded from 4.2.0 to 4.3.0 by @dependabot in #1220
* Bump the dagger group with 2 updates by @dependabot in #1229
* Bump com.google.guava:guava from 33.3.1-android to 33.4.0-android by @dependabot in #1230

---

## Syncthing-Fork v1.28.1.1 (2024-12-10)

## What's Changed
* Updated dependencies
* Add exit action to notification (fixes #1121) by @Catfriend1 in #1210

---

## Syncthing-Fork v1.28.1.0 (2024-12-10)

## What's Changed
* Add to migration instructions a note to delete the Syncthing configuration backup folder by @valentinstn in #1165
* Update README.md by @mrlasers in #1173
* Move default metadata from en-GB to en-US by @leoheitmannruiz in #1182
* Readme: fixed screenshot links and markdownlint rules by @lucaspar in #1191
* Allow configuring time schedule interval by @ImranR98 in #1168
* SyncthingNative v1.28.1 by @Catfriend1 in #1194
* Allow empty device name (fixes #1185) by @Catfriend1 in #1195
* WebUI password not migrated after switching from official app to forked app (fixes #1189) by @Catfriend1 in #1196
* Default folder path: Home shortcut (~) broken by @Catfriend1 in #1197

## New Contributors
* @valentinstn made their first contribution in #1165
* @mrlasers made their first contribution in #1173
* @leoheitmannruiz made their first contribution in #1182
* @lucaspar made their first contribution in #1191
* @ImranR98 made their first contribution in #1168

---

## Syncthing-Fork v1.28.0.0 (2024-10-21)

Fixes:
- Android 15: disable edge-to-edge rendered UI
- Android 15: Allow foreground service to run longer than 6 hours
- Device address: allow quic4:// and quic6:// values to be entered

---

## Syncthing-Fork v1.27.12.0 (2024-09-13)

---

## Syncthing-Fork v1.27.9.0 (2024-07-27)

---

## Syncthing-Fork v1.27.7.0 (2024-05-12)

---

## Syncthing-Fork v1.27.6.0 (2024-04-10)

---

## Syncthing-Fork v1.27.4.0 (2024-03-08)

---

## Syncthing-Fork v1.27.3.0 (2024-02-08)

---

## Syncthing-Fork v1.27.2.1 (2024-01-07)

Other
--
- Experimental: Make ignore doze permission recommended instead of mandatory (#1058)
- Thanks to @acolomb we got big improvements in community translation handling :-)

---

## Syncthing-Fork v1.27.2.0 (2023-12-14)

Fixes
--
- (upstream) update Syncthing to version 1.27.2-rc.1, which (hopefully) fixes app crashes @ChronosXYZ

Other
--
- Experimental: Make ignore doze permission recommended instead of mandatory (#1058)
- Thanks to @acolomb we got big improvements in community translation handling :-)

---

## Syncthing-Fork v1.27.1.0 (2023-12-08)

Fixes
--
- (upstream) hopefully those exit code 2 crashes were fixed

---

## Syncthing-Fork v1.27.0.1 (2023-12-05)

Other
--
- Experimental: Make ignore doze permission recommended instead of mandatory (#1058)
- Thanks to @acolomb we got big improvements in community translation handling :-)

---

## Syncthing-Fork v1.27.0.0 (2023-11-22)

Enhanced
--
- Do not create default folder in Syncthing, if /DCIM/ contains .stfolder from previous install (#1042)
- Switch the language of the app to a different one than the phone setting (#1044)

Fixes
--
- Fix themed icon (#1041)
- Fix set file versioning to none (#1043)

---

## Syncthing-Fork v1.26.1.0 (2023-11-15)

NOTE: If you are upgrading instead of doing a fresh install, go to "Settings" - "Syncthing Options" and set a password. This is required prior to opening the "Web UI".

Fixes
--
- Fix notification permission revoked case

Other
--
- Updated Go dependency to 1.21.4
- Updated NDK dependency to r26b
- Translations moved to Weblate and were cleaned up (Big thanks to @acolomb)

---

## Syncthing-Fork v1.26.0.2 (2023-11-06)

NOTE: If you are upgrading instead of doing a fresh install, go to "Settings" - "Syncthing Options" and set a password. This is required prior to opening the "Web UI".

Enhanced
--
- Allow username and password change (#1019)
- Show sync cycle duration on qs tile (#1013)

Fixes
--
- Disallow too high number of minutes for sync cycle (#1014)
- Persist device allowNetwork
- Persist device numConnections

Other
--
- Support Android 14
- Support HTML login form (Web UI)

---

## Syncthing-Fork v1.26.0.1 (2023-10-21)

NOTE: If you are upgrading instead of doing a fresh install, go to "Settings" - "Syncthing Options" and set a password. This is required prior to opening the "Web UI".

Enhanced
--
- Allow username and password change (#1019)
- Show sync cycle duration on qs tile (#1013)

Fixes
--
- Disallow too high number of minutes for sync cycle (#1014)
- Persist device allowNetwork
- Persist device numConnections

Other
--
- Support Android 14
- Support HTML login form (Web UI)

---

## Syncthing-Fork v1.26.0.0 (2023-10-14)

Enhanced
--
- Show sync cycle duration on qs tile (#1013)

Fixes
--
- Disallow too high number of minutes for sync cycle (#1014)
- Persist device allowNetwork
- Persist device numConnections

Other
--
- Support Android 14
- Support HTML login form (Web UI)

---

## Syncthing-Fork v1.23.7.0 (2023-08-09)

---

## Syncthing-Fork v1.23.6.0 (2023-07-22)

Other
--
- Workaround .stversions cleanup timer not firing: Force cleanup on startup (#1001)

---

## Syncthing-Fork v1.23.5.1 (2023-06-18)

Enhanced
--
- Configurable backup folder name (#992)
- Default folder is sendReceive

---

## Syncthing-Fork v1.23.5.0 (2023-06-11)

Fixed issues: #987 #974 #984

---

## Syncthing-Fork v1.23.2.2 (2023-03-09)

Fixes
--
- Do not account total percentage completion for disconnected devices (#913)

---

## Syncthing-Fork v1.23.2.1 (2023-03-07)

---

## Syncthing-Fork v1.23.0.0 (2023-01-05)

---

## Syncthing-Fork v1.22.2.2 (2022-12-08)

Enhanced
--
- Support app settings backup to g-drive
- Backup/restore https cert (#948)

Other
--
- Target API level 33 (#918)

---

## Syncthing-Fork v1.22.2.1 (2022-12-07)

Enhanced
--
- Add pref "log to file" (#945)
- Hint if location services are off during wifi selection dialog (#924)

Fixes
--
- Fix sync cycle set to empty (#937)

---

## Syncthing-Fork v1.22.2.0 (2022-11-09)

---

## Syncthing-Fork v1.22.1.0 (2022-11-02)

* Not available on Google Play - see issue #929

---

## Syncthing-Fork v1.21.0.3 (2022-09-29)

Enhanced
--
- Route network request to specific network (#914)

---

## Syncthing-Fork v1.21.0.2 (2022-09-28)

Enhanced
--
- Show pending bytes of disconnected devices (#916)

Fixes
--
- Fix total completion percentage (#915)

---

## Syncthing-Fork v1.21.0.1 (2022-09-21)

Enhanced
--
- App is removed from recents after exit

---

## Syncthing-Fork v1.21.0.0 (2022-09-08)


---

## Syncthing-Fork v1.20.3.1 (2022-07-13)

Enhanced
--
- Show remaining bytes for devices (#897)
- Allow to configure sync duration when on schedule (#679)

Fixes
--
- Support for quic:// URIs in device dialog (#893)
- Prevent MediaStore from deleting files during case-only renames

---

## Syncthing-Fork v1.20.3.0 (2022-07-06)


---

## Syncthing-Fork v1.20.1.0 (2022-05-05)

Fixes
--
- Removed in-app language switch option

---

## Syncthing-Fork v1.19.1.1 (2022-03-16)

Fixes
--
- Logs no longer written to external sdcard (#817)
- Theme applied after config import (#852)
- Accept custom device relay address (#882)
- Improve charging status detection (#875)

---

## Syncthing-Fork v1.19.1.0 (2022-03-02)

---

## Syncthing-Fork v1.19.0.0 (2022-02-16)

---

## Syncthing-Fork v1.18.6.0 (2022-01-16)

---

## Syncthing-Fork v1.18.2.1 (2021-09-19)

Enhanced
--
- UI for untrusted devices, encrypted folder sharing (#725) @Catfriend1 
- Schedule a backup stop job when running on schedule (#837) @Helium314

---

## Syncthing-Fork v1.16.0.6 (2021-05-25)

Enhanced
--
- Android 11: keep synced user data when app is uninstalled (#806)
- Support APK install from "Recent Changes" dialog (#812)
- Improve PhotoShootActivity (#813)

Fixes
--
- Minor theme and UI fixes (#807, #811)

Others
--
- Breaking change: Syncthing Camera folder is now /Android/media (#809)

---

## Syncthing-Fork v1.14.0.1 (2021-03-13)

* Syncthing v1.14.0
* Support new v1.14.x config format in the wrapper (#750)

Fixes
--
- Fixed "maxConflicts" setting not preserved by wrapper (#750)
- Fixed Work profile path bug (#733)

Other issues
--
- File Watcher fails to start, see forum.
- Play version
-+ "whitelisted WiFi" feature no longer available
-+ Android 11? you may experience problems as G limited storage access and doesn't allow the app request the new and required "all files access" permission.

---

## Syncthing-Fork v1.12.1.1 (2021-01-08)

Enhanced
--

Other issues
--
- G Play version
-+ The "sync on whitelisted WiFi SSID" feature is no longer available.
-+ If you got your phone updated to Android 11 you may experience problems as G limited storage access and doesn't allow the app request the new and required "all files access" permission before January 2021 (as their Android dev docs state). See GitHub for more info.

---

## Syncthing-Fork v1.11.1.0 (2020-11-04)

Enhanced
--
- Integrated QR Code Scanner, thanks to @sumit-anantwar
- Theme: Add "Follow system" for API 28+, thanks to @praveenrajput

Other issues
--
- If you got your phone updated to Android 11 and using the G Play version of this app, you may experience problems as G limited storage access and doesn't allow the app request the new and required "all files access" permission before January 2021 (as their Android dev docs state). See GitHub for more info.
- Minimum required OS is Android 5.0

---

## Syncthing-Fork v1.10.0.0 (2020-10-10)

Enhanced
--
- Integrated QR Code Scanner, thanks to @sumit-anantwar
- Theme: Add "Follow system" for API 28+, thanks to @praveenrajput

Other issues
--
- If you got your phone updated to Android 11 and using the G Play version of this app, you may experience problems as G limited storage access and doesn't allow the app request the new and required "all files access" permission before January 2021 (as their Android dev docs state). See GitHub for more info.
- Minimum required OS is Android 5.0

---

## Syncthing-Fork v1.9.0.5 (2020-09-20)
* Syncthing v1.9.0

Other issues
--
- If you got your phone updated to Android 11 and using the G Play version of this app, you may experience problems as G limited storage access and doesn't allow the app request the new and required "all files access" permission before January 2021 (as their Android dev docs state). See GitHub for more info.
- Minimum required OS is Android 5.0

---

## Syncthing-Fork v1.8.0.3 (2020-08-15)

Fixed
--
- RecentChangesDialog: Fix display of filenames containing "#" (#686)
- Disable "file pull order" option for sendOnly folders (#683)
- Minor fixes in model to get compatible with Syncthing v1.8.0 (#684) (#685)

Enhanced
--
- Device tab: Show device last seen date and time (#687)
- Offer button to "rescan all" folders (#681) (#693)

---

## Syncthing-Fork v1.7.0.1 (2020-07-07)

Fixed
--

Enhanced
--
- Initial SDcard read/write support on Android 11+ (#618)

Other issues
--
- Android 11 wrapper parts are use-able, but not complete yet.

---

## Syncthing-Fork v1.6.0.4 (2020-06-02)

Fixed
--
- SyncthingNative doesn't start in root shell (#655)

Enhanced
--

Other issues
--
- Workaround for ip get route bug, some LOS kernels 3.0.x

---

## Syncthing-Fork v1.4.2.4 (2020-05-01)
* Syncthing v1.4.2
* Minor fixes

---

## Syncthing-Fork v1.4.0.4 (2020-03-16)

---

## Syncthing-Fork v1.3.3.4 (2019-12-23)
* Updated build dependencies.
* PLEASE NOTE: Android 10's Scoped Storage can or will render this app unusable in the future. This cannot be solved by devs, check wiki.
Fixed
-
- SyncthingNative not running on x86_64 arch (#583)
- Rename "libsyncthing.so" to "libsyncthingnative.so" (#590)
Enhanced
-
- Trigger sync window after share-to-Syncthing (#575)
- Show device ID and qr code when native is not running (#591)

---

## Syncthing-Fork v1.3.2.4 (2019-12-03)
* Syncthing v1.3.2
* PLEASE NOTE: Android 10's Scoped Storage can or will render this app unusable in the future. This cannot be solved by devs, check wiki.
Fixed
* Selected menu item not visible on Android TV (#557)
* Folder tab not showing failed items (#547)
Enhanced
* Show progress bars and overall progress, better notification (#548) (#552) (#555)
* Save battery by using events API instead of polling (#546)

---

## Syncthing-Fork v1.3.1.6 (2019-11-10)
* Syncthing v1.3.1
* PLEASE NOTE: Android 10's Scoped Storage can or will render this app unusable in the future. This cannot be solved by devs, check wiki.
Enhanced
* Improved device/folder approval notifications to fire faster and on app start (#471)

---

## Syncthing-Fork v1.3.0.5 (2019-10-28)
* Syncthing v1.3.0
* PLEASE NOTE: Android 10's Scoped Storage can or will render this app unusable in the future. This cannot be solved by devs, check wiki.
Permissions
* The camera permission is optional and will be asked only if you decide to use the Syncthing Camera feature.
Fixed
* Revoke "Syncthing Camera" feature consent if user removes the folder (#524)
Enhanced
* Option to hide "Syncthing Camera" launcher icon (#521)

---

## Syncthing-Fork v1.2.2.4 (2019-09-22)
Changelog
* Syncthing v1.2.2
* PLEASE NOTE: Android 10's Scoped Storage can or will render this app unusable in the future. This cannot be solved by devs, check wiki.
Fixed
* SyncthingService/importConfig: Fix unchecked type casts (#489)
* NPE crash when config.xml "version" attr is broken (#497)
Enhanced
* Added tip: Turn off Xiaomi battery saver for the app (#498)
* Prepared Syncthing v1.3.0 API changes (#496)
* Migrated to AndroidX implementations (#488)

---

## Syncthing-Fork v1.2.0.8 (2019-07-16)
Fixes
* Fixed crash in config reader (#445)
Enhancements
* Add button to "force start/stop" regardless of run conditions (#443)
* Add "roaming" option to run conditions (#441)
* Add "Revert local changes" button to UI (#439)
* Override changes: Show confirmation dialog (#436)

---

## Syncthing-Fork v1.1.1.3 (2019-04-27)
Changelog:
- Disable DuraSpeed on HMD Global phones (#410) - see Wiki
- Shorten logcat excerpts (#412)
- Hourly sync - Sync immediately when app starts (#411)
- Show notification badge if user interaction is required (#408)

---

## Syncthing-Fork v1.1.0.6 (2019-03-29)
Changelog:
- Add option to trigger sync every hour (#387)
- Fix back arrow in TipsAndTricks activity on Android 4.4 not working (#389)

---

## Syncthing-Fork v1.0.0.20 (2019-01-31)
Changelog:
- Fix deferred native shutdown not working during State.STARTING (#296)
- Don't crash if config got corrupted and inform the user (#295)
- Remove boilerplate shell code (#289)
- Fix ANR in SyncthingRunnable (#288)

---

