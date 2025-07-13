@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET "APK_FULLFN=%PROJECT_ROOT%\app\build\outputs\apk\debug\app-debug.apk"
::
dir "%APK_FULLFN%"
adb install -r "%APK_FULLFN%"
::
goto :eof
