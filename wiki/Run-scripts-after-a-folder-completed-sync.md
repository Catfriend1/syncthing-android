## Run scripts after a folder completed its sync progress

This is an expert option to run scripts after a folder changes its sync state. Scripts at '.stfolder/*.sh' will be executed, other locations are not supported for security considerations.

Example script call:
>  cd /storage/emulated/0/DCIM/Camera/.stfolder/..; sh /storage/emulated/0/DCIM/Camera/.stfolder/rename-files.sh sync_complete

Demo scripts:
- demo1.sh
``` bash
#!/bin/sh
echo "[INFO] Demo 1: param1=[${1}], pwd=[$(pwd)]"
exit 0
```

- demo2.sh
``` bash
#!/bin/sh
echo "[INFO] Demo 2"
exit 0
```

Demo push to AVD:
```
adb push ".stfolder" /storage/emulated/0/DCIM/
```

Test:
```
2025-05-13 17:54:50.162 21622-21622 RestApi  D  setRemoteCompletionInfo: Completed folder=[android_sdk_built_for_x86_64_u3dz-photos]
2025-05-13 17:54:50.168 21622-21622 Util  D  runScriptSet: Exec [sh "/storage/emulated/0/DCIM/.stfolder/demo1.sh" "sync_complete"]
2025-05-13 17:54:50.173 21622-21622 Util  D  runShellCommandGetOutput: sh "/storage/emulated/0/DCIM/.stfolder/demo1.sh" "sync_complete"
2025-05-13 17:54:50.188 21622-21622 Util  I  runShellCommandGetOutput: Exited with code 1
2025-05-13 17:54:50.188 21622-21622 Util  V  runScriptSet: Exec result [[INFO] Demo 1]
2025-05-13 17:54:50.188 21622-21622 Util  D  runScriptSet: Exec [sh "/storage/emulated/0/DCIM/.stfolder/demo2.sh" "sync_complete"]
2025-05-13 17:54:50.191 21622-21622 Util  D  runShellCommandGetOutput: sh "/storage/emulated/0/DCIM/.stfolder/demo2.sh" "sync_complete"
2025-05-13 17:54:50.207 21622-21622 Util  I  runShellCommandGetOutput: Exited with code 1
2025-05-13 17:54:50.207 21622-21622 Util  V  runScriptSet: Exec result [[INFO] Demo 2]
2025-05-13 18:18:25.876 22547-22547 Util  V  runScriptSet: No script files found within folder: /storage/emulated/0/DCIM/.stfolder
2025-05-13 18:18:51.140 22547-22547 Util  V  runScriptSet: No script files found within folder: /storage/emulated/0/Pictures/.stfolder
```
