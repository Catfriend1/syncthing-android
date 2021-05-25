@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
cls
REM
REM Script Consts.
SET CLEANUP_BEFORE_BUILD=1
SET SKIP_RELEASE_BUILD=1
REM
REM Runtime Variables.
REM
REM SET SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE=%userprofile%\.android\play_key.json"
REM SET SYNCTHING_RELEASE_STORE_FILE="%userprofile%\.android\signing_key.jks"
SET SYNCTHING_RELEASE_KEY_ALIAS=Syncthing-Fork
SET BUILD_FLAVOUR_GPLAY=gplay
title %SYNCTHING_RELEASE_KEY_ALIAS% - Build Debug and Release APK
REM
SET GIT_INSTALL_DIR=%ProgramFiles%\Git
SET GIT_BIN="%GIT_INSTALL_DIR%\bin\git.exe"
REM
SET PATH=C:\Program Files\Android\Android Studio\jre\bin;"%GIT_INSTALL_DIR%\bin";%PATH%
REM
echo [INFO] Checking if SyncthingNative was built before starting this script ...
SET LIBCOUNT=
for /f "tokens=*" %%A IN ('dir /s /a "%SCRIPT_PATH%app\src\main\jniLibs\*" 2^>NUL: ^| find /C "libsyncthingnative.so"') DO SET LIBCOUNT=%%A
IF NOT "%LIBCOUNT%" == "4" echo [ERROR] SyncthingNative[s] "libsyncthingnative.so" are missing. Please run "gradlew buildNative" first. & goto :eos
REM
REM Check if we should skip the release build and just make a debug build.
IF "%SKIP_RELEASE_BUILD%" == "1" goto :absLint
REM
echo [INFO] Let's prepare a new "%SYNCTHING_RELEASE_KEY_ALIAS%" release.
REM
echo [INFO] Checking release prerequisites ...
IF NOT EXIST "%SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE%" echo [ERROR] SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE env var not set or file does not exist. & goto :eos
REM
REM User has to enter the signing password if it is not filled in here.
SET SIGNING_PASSWORD=
IF DEFINED SIGNING_PASSWORD goto :absLint
:enterSigningPassword
setlocal DisableDelayedExpansion
set "psCommand=powershell -Command "$pword = read-host 'Enter signing password' -AsSecureString ; ^
	$BSTR=[System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($pword); ^
		[System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)""
for /f "usebackq delims=" %%p in (`%psCommand%`) do SET SIGNING_PASSWORD=%%p
setlocal EnableDelayedExpansion
IF NOT DEFINED SIGNING_PASSWORD echo [ERROR] Signing password is required. Please retry. & goto :enterSigningPassword
REM
:absLint
REM
echo [INFO] Running lint before building ...
IF "%SKIP_RELEASE_BUILD%" == "1" call gradlew --quiet lintDebug & SET RESULT=%ERRORLEVEL%
IF NOT "%SKIP_RELEASE_BUILD%" == "1" call gradlew --quiet lint & SET RESULT=%ERRORLEVEL%
IF NOT "!RESULT!" == "0" echo [ERROR] "gradlew lint" exited with code #%RESULT%. & goto :eos
REM
call :buildApk debug
REM
REM Check if we should skip the release build and just make a debug build.
IF "%SKIP_RELEASE_BUILD%" == "1" goto :absPostBuildScript
REM
call :buildApk release
call :buildApk %BUILD_FLAVOUR_GPLAY%
REM
IF "%CLEANUP_BEFORE_BUILD%" == "1" del /f "%SCRIPT_PATH%app\build\outputs\bundle\%BUILD_FLAVOUR_GPLAY%\app-%BUILD_FLAVOUR_GPLAY%.aab" 2> NUL:
echo [INFO] Building Android BUNDLE variant "%BUILD_FLAVOUR_GPLAY%" ...
call gradlew --quiet bundle%BUILD_FLAVOUR_GPLAY%
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew bundle%BUILD_FLAVOUR_GPLAY%" exited with code #%RESULT%. & goto :eos
REM
:absPostBuildScript
REM
echo [INFO] Running OPTIONAL post build script ...
call gradlew --quiet postBuildScript
REM
echo [INFO] Deleting unsupported play translations ...
call gradlew --quiet deleteUnsupportedPlayTranslations
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew deleteUnsupportedPlayTranslations" exited with code #%RESULT%. & goto :eos
REM
REM Copy build artifacts with correct file name to upload folder.
call "%SCRIPT_PATH%postbuild_copy_apk.cmd"
REM
REM Check if we should skip the release upload and finish here.
IF "%SKIP_RELEASE_BUILD%" == "1" goto :eos
REM
:askUserReadyToPublish
SET UI_ANSWER=
SET /p UI_ANSWER=Are you ready to publish this release to GPlay? [y/n]
IF NOT "%UI_ANSWER%" == "y" goto :askUserReadyToPublish
REM
REM Workaround for play-publisher issue, see https://github.com/Triple-T/gradle-play-publisher/issues/597
:clearPlayPublisherCache
IF EXIST "app\build\generated\gpp" rd /s /q "app\build\generated\gpp"
IF EXIST "app\build\generated\gpp" TASKKILL /F /IM java.exe & sleep 1 & goto :clearPlayPublisherCache
REM
REM Publish text and image resources to GPlay
echo [INFO] Publishing descriptive resources to GPlay ...
call gradlew --quiet publish%BUILD_FLAVOUR_GPLAY%Listing
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew publish%BUILD_FLAVOUR_GPLAY%Listing" exited with code #%RESULT%. & pause & goto :clearPlayPublisherCache
REM
REM Publish APK to GPlay
echo [INFO] Publishing APK to GPlay ...
REM call gradlew --quiet publish%BUILD_FLAVOUR_GPLAY%
REM SET RESULT=%ERRORLEVEL%
REM IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew publish%BUILD_FLAVOUR_GPLAY%" exited with code #%RESULT%. & goto :eos
call gradlew --quiet publish%BUILD_FLAVOUR_GPLAY%Bundle
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew publishBundle" exited with code #%RESULT%. & pause & goto :clearPlayPublisherCache
REM
goto :eos
:eos
REM
echo [INFO] End of Script.
REM
pause
goto :eof


:buildApk
REM
REM Syntax:
REM 	call :buildApk [BUILD_TYPE]
REM
REM Variables.
SET "BA_BUILD_TYPE=%1"
IF NOT DEFINED BA_BUILD_TYPE echo [ERROR] buildApk: Parameter 1 BUILD_TYPE missing. & pause & goto :eof
REM
IF "%CLEANUP_BEFORE_BUILD%" == "1" del /f "%SCRIPT_PATH%app\build\outputs\apk\%BA_BUILD_TYPE%\app-%BA_BUILD_TYPE%.apk" 2> NUL:
echo [INFO] Building Android APK variant "%BA_BUILD_TYPE%" ...
call gradlew --quiet assemble%BA_BUILD_TYPE%
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew assemble%BA_BUILD_TYPE%" exited with code #%RESULT%. & goto :eos
type "app\build\intermediates\merged_manifests\%BA_BUILD_TYPE%\AndroidManifest.xml" | findstr /i "android:version"
REM
goto :eof
