Errors covered by this article:
```
INSTALL_FAILED_DUPLICATE_PERMISSION
(appName) conflicts with another package already installed
```

This error may occur on some Android systems while trying to update the app to v1.29.6.5 or higher.

Workaround:
- Open the Syncthing-Fork menu -> Exit
- Android settings -> Apps -> Syncthing-Fork -> Force stop
- Android settings -> Apps -> Syncthing-Fork -> Uninstall, do NOT keep data when Android asks (this refers to the app's config, not your synced folders)
- Install latest release
