@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_ID_DEBUG=com.github.catfriend1.syncthingandroid.debug"
SET "PACKAGE_ID_RELEASE=com.github.catfriend1.syncthingandroid"
::
where adb >NUL: 2>&1 || (echo [ERROR] adb is missing. Please install it first. Stop. & goto :eof)
::
FOR /F "delims=" %%I IN ('adb shell pidof %PACKAGE_ID_RELEASE%') DO SET PID=%%I
IF NOT DEFINED PID FOR /F "delims=" %%I IN ('adb shell pidof %PACKAGE_ID_DEBUG%') DO SET PID=%%I
IF NOT DEFINED PID echo [ERROR] Syncthing-Fork is NOT installed. Please install it first. Stop. & goto :eof
::
echo [INFO] Found PID: %PID%
timeout /nobreak 1 >NUL:
adb logcat --pid=%PID% *:V
::
goto :eof
