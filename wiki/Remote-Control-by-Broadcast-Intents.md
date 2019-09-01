**Important Notice**
Since version v0.14.54.8, remote control by broadcast intents has to be enabled under "Settings" > "Experimental" before syncthing listens to broadcast intents sent by third-party automation apps.

SyncThing can be controlled externally by sending Broadcast-Intents. Applications like **Tasker**, **Llama** or **Automate** now can _start_ or _stop_ Syncthing on behalf of the user.
Use cases would be to run SyncThing only in special conditions - like at home and charging, or once every night, ...

The following intent actions are available:
* Start Syncthing: com.github.catfriend1.syncthingandroid.action.START
* Stop Syncthing: com.github.catfriend1.syncthingandroid.action.STOP

The intents should be set to 'broadcast' rather than starting an activity of service. Note that some apps, e.g. **Llama**, are sensitive to trailing spaces so be careful not to leave any when entering the action.

Tasker example action to start Syncthing:
* Action: Send Intent
> Action: com.github.catfriend1.syncthingandroid.action.START

> Type: None

> Mime type: [ leave empty ]

> Data: [ leave empty ]

> Extra: [ leave empty ]

> Package: com.github.catfriend1.syncthingandroid / for developers: com.github.catfriend1.syncthingandroid.debug

> Class: [ leave empty ]

> Target: Broadcast Receiver

> Description: Start Syncthing

If Syncthing is configured to *Start automatically on boot* those intents are ignored and the configuration in Syncthing takes precedence over the intents – resulting in ignoring them.

For the **Automate** app there is an example-flow available in the Automate-Community that demonstrates the start- and the stop-intent. Search for *Syncthing*.
