## Switching from the deprecated official version

Switching is easier than you may think!

- Preparation
  - In the official Syncthing app, go to the settings screen and export your config.
  - Now stop the official app entirely using the system app settings for Syncthing.
    -  Force stop the app basically. We need to ensure it's not running.
  - Confirm you can see the config export by using a file browser.
    - Default folder location: e.g. `/storage/emulated/0/backups/syncthing`
  - Create a new file called `config.zip` comprised of the following files:
    - cert.pem
    - https-cert.pem
    - https-key.pem
    - key.pem
    - config.xml
      - Tip: Use the [Material Files app](https://f-droid.org/packages/me.zhanghai.android.files/) to create the zip file.
  - You can remove the original *.pem and *.xml files after creating the `config.zip` file.
  - Syncthing-Fork will require the `config.zip` to restore your config.

- Migration
  - Install our [latest release](https://github.com/Catfriend1/syncthing-android/releases/latest) from GitHub or F-Droid.
  - Start Syncthing-Fork.
  - Complete the welcome slides.
  - In the Syncthing-Fork settings, go to "Import & Export".
    - Import the `config.zip` you created earlier.
  - Like magic, everything should be as it was in Syncthing official.
    - You should see your shared folders and devices.
    - Confirm everything looks good.

- Cleanup
  - Uninstall the official Syncthing app.
  - Delete the old configuration export.
    - e.g. `/storage/emulated/0/backups/syncthing/config.zip`

- Further advice
  - In the Syncthing-Fork settings, go to "Import & Export".
  - Set a password to protect future config exports.
  - Tap "Export config" to export your config into a password encrypted `config.zip`.
