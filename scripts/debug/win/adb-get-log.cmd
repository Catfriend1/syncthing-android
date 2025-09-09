@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
::
where adb >NUL: 2>&1 || (echo [ERROR] adb is missing. Please install it first. Stop. & goto :eof)
::
echo [INFO] Looking for app [%PACKAGE_NAME%]
FOR /F "delims=" %%I IN ('adb shell pidof %PACKAGE_NAME%') DO SET PID=%%I
IF NOT DEFINED PID echo [ERROR] Syncthing-Fork DEBUG is NOT installed. & FOR /F "delims=" %%I IN ('adb shell pidof %PACKAGE_NAME:.debug=%') DO SET PID=%%I
IF NOT DEFINED PID echo [ERROR] Syncthing-Fork RELEASE is NOT installed. Please install it first. Stop. & goto :eof
::
echo [INFO] Found PID: %PID%
timeout /nobreak 3 >NUL:
adb logcat --pid=%PID% *:V
::
goto :eof
