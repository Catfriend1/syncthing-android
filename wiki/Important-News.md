Dear Syncthing user,

Syncthing-Fork v1.30.0.4 was the last release for v1. Your action is required to update to v2.0.7.0+ when you feel ready to do it.

Please make sure to read upstream's release notes first before attempting to upgrade your devices. See https://github.com/syncthing/syncthing/releases/tag/v2.0.7 .

Upgrading Syncthing-Fork from v2.0.3.0 or v1.30.x to v2 involves the following steps:

⚠️ Make sure you have a backup of your data. I've not yet heard of someone losing files during the upgrade, but a backup won't hurt in case you need to restore.

📂 Check your folder paths. If you shared folders from `(externalStorage)/Android/media|data/com.github.catfriend1.syncthingandroid/(files|yourSharedFolderName)`, some Android versions might delete their contents when you uninstall the app at a later step. Their contents must be part of your backup and stored in another location outside the "app-specific data or media directory". 📂▶️🗄️

Open your currently installed version of Syncthing-Fork.

Go to Settings > Import & Export. Export your app config.

⚠️ Make sure that the app config has finished exporting, wait for the confirmation toast or check that the file size of the export has stopped increasing.

Uninstall the Syncthing-Fork app.

Install the latest v2 version, that is v2.0.7.0 or higher.

Go to Settings > Import & Export. If you've used a password during export, enter it. Import your app config.

Your upgrade is finished. Due to changes to our packageId, you can upgrade via F-Droid or GitHub as you please to future versions as they will be the same APK files starting from v2.0.7+ and no longer conflicting with the play releases by nel0x. 🎉
