### Why is the Syncthing's Web UI not supported?

✅ It's fine to get more detailed information about the Syncthing status from the web UI.

The web UI can be used, but should as far as possible be <b>considered as read only</b>. For example, you may review failed items or help diagnose the running Syncthing native instance. The web UI contains a lot more information than the wrapper because the wrapper has not implemented user interface equivalents yet to reflect everything. The wrapper's Android UI will improve in future releases of the app, but don't expect too much. We would need <b>a lot of new code</b> to do so. And that code would have to be maintained as well with every upcoming Syncthing release.

⛔ Please do not modify settings using the web UI unless we explicitly advise you to do so.

The wrapper at the state of writing this article requires frequent reading and writing Syncthing's configuration file `config.xml`. Parts of the running config are cached in the wrapper's [model](../../app/src/main/java/com/nutomic/syncthingandroid/model).

If you make changes through Syncthing's web UI, the wrapper might not "notice" the changes and things could go severely wrong seen from a worst-case scenario point of view. Or your changes are reverted instead of remaining persistent like you assumed. The wrapper might overwrite your changes with the last known configuration managed by it shortly afterwards. This will at least cause confusion, at most let a sync operation fail or do something weird you don't want it to do so. ⚠️ You have been warned.

💡 If you'd like to see web UI setting "X" or "Y" supported by this app, the wrapper for Syncthing, you need to file a PR and handle these aspects in code:

* Contribute new Android UI code to our app to display the current state of the setting.

* Add code to allow the user to change the setting through our [Android UI](../../app/src/main/java/com/nutomic/syncthingandroid/activities).

* Take care of [applying the change correctly to Syncthing's "config.xml"](../../app/src/main/java/com/nutomic/syncthingandroid/util/ConfigXml.java)

* Send the change to Syncthing via its [RestAPI](../../app/src/main/java/com/nutomic/syncthingandroid/service/RestApi.java)

* Some config changes require a restart of the running Syncthing binary. Please keep that in mind.

* Take a look at the ["ConfigRouter" class](../../app/src/main/java/com/nutomic/syncthingandroid/util/ConfigRouter.java) to get an idea how we currently handle config changes.
