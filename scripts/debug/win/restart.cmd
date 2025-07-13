@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthingandroid.debug"
::
adb shell am force-stop "%PACKAGE_NAME%"
adb shell am start -n "%PACKAGE_NAME%/com.nutomic.syncthingandroid.activities.FirstStartActivity"
::
goto :eof
