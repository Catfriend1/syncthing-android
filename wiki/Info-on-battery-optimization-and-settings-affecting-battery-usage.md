First things first:
* Syncthing-Fork requires to be exempted from doze which is asked during the welcome wizard. The app will cease to function if the permission is revoked.
* The app doesn't require the permission to "waste" your battery and the app doesn't use it to set wakelocks. It does run a reliable so called "Android foreground service".
* Technically speaking, it's required to avoid database corruption in case Android can't communicate with the SyncthingNative before putting the app and SyncthingNative in suspend.

Here is how you can reduce battery usage even further by optimizing Syncthing's settings:
* If you've configured your Syncthing instances to connect using DynDNS or static IP addresses, turn off all discovery options: Go to "App/Settings/Syncthing Options" and disable "Local discovery", "Global discovery", "NAT Traversal" and "Relay".
* If you've configured your Syncthing instances to connect devices which are located on the same local network, turn off global discovery and relay options: Go to "App/Settings/Syncthing Options" and disable "Global discovery", "NAT Traversal" and "Relay".
* If you don't need your changes immediately synced between devices and can wait for up to an hour until the changes are synced, go to "App/Settings/Run conditions" and check "Sync every hour for 5 minutes". The app will then stay in the background showing its notification all time but will only do its work in "5-minute-time-windows" every hour. This will save a lot of battery but practically require an always-on device on the other side.

Short summary:
* Optimize for better battery life
  * Pros:
    * You'll totally love Syncthing's efficiency.
    * Good for advanced and expert users.
  * Cons:
    * You have to do more "caretaking" on how you setup communication between devices yourself.
    * You might come to the point where you like to use an always-on server as a partner for your phone.

* Optimize for faster sync
  * Pros:
    * Syncing just works out of the box.
    * Less need of "caretaking".
  * Cons:
    * Good for beginner users.
    * Because of discovery mechnisms active by default, you'll have open connections all the time even if no partner device is available to sync data with. Those connections, especially WAN connections, keep your modem active and therefore permanently consume battery.
