Syncthing-Fork "Wrapper for Syncthing" has these release channels:

<b>1. F-Droid release build</b>

* <b>"COMMON USER" - please choose this!</b>
* Published on [F-Droid](https://f-droid.org/packages/com.github.catfriend1.syncthingandroid/) and [GitHub release page](https://github.com/Catfriend1/syncthing-android/releases/latest)
* File name is like: com.github.catfriend1.syncthingandroid_fdroid_1.29.6.0_7d59e75.apk
* Certificate hash: yei5Ybhe9jZNx7NG4WE9MgjnfsPqbHCzUhHvfedPEuU=
* Signing Certificate SHA256 fingerprint: <details>C9:E8:B9:61:B8:5E:F6:36:4D:C7:B3:46:E1:61:3D:32:08:E7:7E:C3:EA:6C:70:B3:52:11:EF:7D:E7:4F:12:E5</details>

<b>2. GitHub release build</b>

* If you don't like to use F-Droid for some reason, please choose this!
* Only published on [GitHub release page](https://github.com/Catfriend1/syncthing-android/releases/latest)
* File name is like: com.github.catfriend1.syncthingandroid_release_1.29.6.0_7d59e75.apk
* Certificate hash: +OAnc7gs9+lKqwFiAp9XiF/wcg+uwQxrzvSfffjJSpk=
* Signing Certificate SHA256 fingerprint: <details>F8:E0:27:73:B8:2C:F7:E9:4A:AB:01:62:02:9F:57:88:5F:F0:72:0F:AE:C1:0C:6B:CE:F4:9F:7D:F8:C9:4A:99</details>

<b>3. Google Play release build</b>

* Published by [nel0x](https://github.com/nel0x) at [Google Play Store](https://play.google.com/store/apps/details?id=com.github.catfriend1.syncthingandroid)
* May contain limited functionality due to play policies.
* It's an appetizer for your family and friends to start their Syncthing experience.
* File name is like: com.github.catfriend1.syncthingandroid_gplay_1.29.6.0_7d59e75.aab
* Certificate hash: +OAnc7gs9+lKqwFiAp9XiF/wcg+uwQxrzvSfffjJSpk=
* Signing Certificate SHA256 fingerprint: <details>F8:E0:27:73:B8:2C:F7:E9:4A:AB:01:62:02:9F:57:88:5F:F0:72:0F:AE:C1:0C:6B:CE:F4:9F:7D:F8:C9:4A:99</details>

<b>4. GitHub debug build</b>

* Only published on GitHub
* Can be obtained by looking at the [action workflow builds](https://github.com/Catfriend1/syncthing-android/actions) and artifacts.
* It's for maintainers and contributors who require a second installation of the app on their phone for testing purposes.
* File name is like: com.github.catfriend1.syncthingandroid_debug_1.29.6.0_7d59e75.apk
* TRUSTED builds from this repository owners:
* * Certificate hash: x9QGpAqFQXg1+79ADsY1k0uBrj7+W1HF+PN3BunPZrM=
* * Signing Certificate SHA256 fingerprint: <details>C7:D4:06:A4:0A:85:41:78:35:FB:BF:40:0E:C6:35:93:4B:81:AE:3E:FE:5B:51:C5:F8:F3:77:06:E9:CF:66:B3</details>
* UNTRUSTED builds from contributors of forks:
* * Debug builds triggered from potentially untrusted sources, e.g. fork repositories use a [PUBLIC signing certificate](https://github.com/Catfriend1/syncthing-android/blob/main/debug_scripts/debug.keystore.pub). This offers NO security at all. The content of these builds is NOT authored NOR approved by this repository. They are offered for TESTING PURPOSES only and are NOT production ready.
* * If they would not be signed by the same PUBLIC key, contributors who forked the app and opened a PR here would not be able to try out their contributed changes on their own phone or emulator by upgrading from their previous build as the CI build process would use a different debug signing key for each superseding build.
* * Public UNTRUSTED key <details>Certificate hash: 0fTGzY6Ii7fxLbtKzA5t94Zid/ECP5Gj5w/s5xRLOGM=<br>SHA256 fingerprint: D1:F4:C6:CD:8E:88:8B:B7:F1:2D:BB:4A:CC:E:6D:F7:86:62:77:F1:2:3F:91:A3:E7:F:EC:E7:14:4B:38:63</details>


The signing on these release channels differ, so if you wish to change to a different channel:

* Run existing app installation
  * Open the drawer on the left side > Import & Export > Export configuration
* Uninstall app
* Install app from your preferred release channel
* Run app
  * Complete the welcome wizard
  * Open the drawer on the left side > Import & Export > Import configuration

To verify your downloaded APK, compare the certificate hash of the APK to the one's listed above. It has to match one of them to indicate you have a genuine version of the app. Here is a quick way of getting the certificate hash out of an APK file on Linux:

```
keytool -list -printcert -jarfile "/path/to/release.apk" | grep "SHA256: " | cut -d " " -f 3 | xxd -r -p | openssl base64
#
# Alternative
## https://sisik.eu/cert
```

Future releases might be signed with GPG. Here is the public key which allows you to verify a release is "genuine". This happens under the assumption that the build process on GitHub Actions was not tampered with.

Public key: Catfriend1
```
-----BEGIN PGP PUBLIC KEY BLOCK-----

mDMEaBogpxYJKwYBBAHaRw8BAQdAwmm+DaidLg6ywZR6hGaYccNN2b9KdXSAxG5k
uQ3tBzy0OUNhdGZyaWVuZDEgPDE2MzYxOTEzK0NhdGZyaWVuZDFAdXNlcnMubm9y
ZXBseS5naXRodWIuY29tPoiZBBMWCgBBFiEEKue4YzEYcJQ2hvieVyYqmI5VFYQF
AmgaIKcCGwMFCQWkqPkFCwkIBwICIgIGFQoJCAsCBBYCAwECHgcCF4AACgkQVyYq
mI5VFYREswD/WNLLZlO/4K12PwFHEHEg7W1Rcge7tbMPbFCIM9DIhPIBAOeXtEA9
9LkmDWv+TlYZ4gdk/tuAZKiOPl2gx1yMqdYGuDgEaBogpxIKKwYBBAGXVQEFAQEH
QB7NWczPOLSQIMDIxx4mbAJWnhNBBccFJKOJHSnusgZMAwEIB4h+BBgWCgAmFiEE
Kue4YzEYcJQ2hvieVyYqmI5VFYQFAmgaIKcCGwwFCQWkqPkACgkQVyYqmI5VFYRw
jQD/a2Fx/Nls5+ZvvyUqlX7oFERf6v+eYoi/0qB5em5ce6sA/AsnYRyBbd5gdHgb
VUTQ/RYTeUCdkM1SuArDflF0rIwP
=c0Mr
-----END PGP PUBLIC KEY BLOCK-----
```

Notes:

```
# Generate SHA256 hash
powershell "$hex = 'C7:D4:06:A4:0A:85:41:78:35:FB:BF:40:0E:C6:35:93:4B:81:AE:3E:FE:5B:51:C5:F8:F3:77:06:E9:CF:66:B3'; $hexBytes = $hex.Split(':') | ForEach-Object { $_.PadLeft(2, '0') }; $hexClean = ($hexBytes -join ''); $bytes = for ($i = 0; $i -lt $hexClean.Length; $i += 2) { [Convert]::ToByte($hexClean.Substring($i, 2), 16) }; [Convert]::ToBase64String($bytes)"

# Decode SHA256 hash
powershell "$base64 = 'x9QGpAqFQXg1+79ADsY1k0uBrj7+W1HF+PN3BunPZrM='; $bytes = [System.Convert]::FromBase64String($base64); $hex = ($bytes | ForEach-Object { $_.ToString('X2') }) -join ':'; $hex"

# Verify GPG signature
gpg --edit-key 2AE7B863311870943686F89E57262A988E551584
## trust
## 5
## quit
gpg --verify sha256sum.txt.asc
gpg --output sha256sum.txt --decrypt sha256sum.txt.asc

# Verify SHA256 checksum of each file
powershell -Command "Get-Content 'sha256sum.txt' | ForEach-Object { if ($_ -match '^([a-fA-F0-9]{64})\s+[* ]?(.+)$') { $expected = $matches[1]; $file = $matches[2]; if (Test-Path $file) { $actual = (Get-FileHash $file -Algorithm SHA256).Hash; if ($actual -ieq $expected) { Write-Host \"${file}: OK\" -ForegroundColor Green } else { Write-Host \"${file}: FAILED\" -ForegroundColor Red; Write-Host \"  Expected: $expected\"; Write-Host \"  Found : $actual\" } } else { Write-Warning \"File not found [$file].\" } } }"
```
