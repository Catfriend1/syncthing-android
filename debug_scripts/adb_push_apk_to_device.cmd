@echo off
cls
cd /d "%~dps0"
REM 
REM Purpose
REM 	Installs the current debug APK built by Android studio or gradlew to a physical device via ADB.
REM 
SET APK_FULLFN="..\app\build\outputs\apk\debug\app-debug.apk"
SET APK_PACKAGE_NAME=com.github.catfriend1.syncthingandroid.debug
SET APK_USER_INSTALL=
REM SET APK_USER_INSTALL=--user 0
REM 
REM Check prerequisites
IF NOT EXIST "%APK_FULLFN%" echo [ERROR] APK_FULLFN=[%APK_FULLFN%] does not exist. Stop. & pause & goto :eof
REM 
echo [INFO] Installing APK to attached USB device ...
adb install -r %APK_USER_INSTALL% "%APK_FULLFN%"
REM 
REM echo [INFO] Starting app ...
REM adb shell monkey -p "%APK_PACKAGE_NAME%" 1
REM 
timeout 2
goto :eof
