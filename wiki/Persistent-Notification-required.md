### Persistent notification - required or optional?

The persistent notification is necessary to run a so called foreground service to avoid the app being put asleep by Android and missing run condition changes or synchronization activity. While some users reported a foreground service necessary since Android 8+ and others reported it's working without we are in the same mess here in-between differently behaving, manufacturer specific Android versions that we cannot ensure it working for all users when we would remove the persistent notification. That's why it's required and cannot be configured in Syncthing-Fork as we want the app to work out of the box for all users out there.

We also require the notification until the current device and folder share notifications have been moved to an in-app UI.
