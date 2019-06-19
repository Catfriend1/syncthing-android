@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
cls
REM 
REM Script Consts.
SET CLEANUP_BEFORE_BUILD=1
SET SKIP_RELEASE_BUILD=0
REM
REM Runtime Variables.
REM 
REM SET SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE=%userprofile%\.android\play_key.json"
REM SET SYNCTHING_RELEASE_STORE_FILE="%userprofile%\.android\signing_key.jks"
SET SYNCTHING_RELEASE_KEY_ALIAS=Syncthing-Fork
title %SYNCTHING_RELEASE_KEY_ALIAS% - Build Debug and Release APK
REM 
SET GIT_INSTALL_DIR=%ProgramFiles%\Git
SET GIT_BIN="%GIT_INSTALL_DIR%\bin\git.exe"
REM 
SET PATH=C:\Program Files\Android\Android Studio\jre\bin;"%GIT_INSTALL_DIR%\bin";%PATH%
REM 
echo [INFO] Checking if SyncthingNative was built before starting this script ...
SET LIBCOUNT=
for /f "tokens=*" %%A IN ('dir /s /a "%SCRIPT_PATH%app\src\main\jniLibs\*" 2^>NUL: ^| find /C "libsyncthing.so"') DO SET LIBCOUNT=%%A
IF NOT "%LIBCOUNT%" == "3" echo [ERROR] SyncthingNative[s] "libsyncthing.so" are missing. Please run "gradlew buildNative" first. & goto :eos
REM 
REM Check if we should skip the release build and just make a debug build.
IF "%SKIP_RELEASE_BUILD%" == "1" goto :absLint
REM
echo [INFO] Let's prepare a new "%SYNCTHING_RELEASE_KEY_ALIAS%" GPlay release.
REM 
echo [INFO] Checking release prerequisites ...
IF NOT EXIST "%SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE%" echo [ERROR] SYNCTHING_RELEASE_PLAY_ACCOUNT_CONFIG_FILE env var not set or file does not exist. & goto :eos
REM 
REM User has to enter the signing password if it is not filled in here.
SET SIGNING_PASSWORD=
:enterSigningPassword
IF NOT DEFINED SIGNING_PASSWORD SET /p SIGNING_PASSWORD=Enter signing password:
IF NOT DEFINED SIGNING_PASSWORD echo [ERROR] Signing password is required. Please retry. & goto :enterSigningPassword
REM 
:absLint
REM 
echo [INFO] Running lint before building ...
call gradlew --quiet lint
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew lint" exited with code #%RESULT%. & goto :eos
REM 
echo [INFO] Building Android APK variant "debug" ...
IF "%CLEANUP_BEFORE_BUILD%" == "1" del /f "%SCRIPT_PATH%app\build\outputs\apk\debug\app-debug.apk" 2> NUL:
call gradlew --quiet assembleDebug
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew assembleDebug" exited with code #%RESULT%. & goto :eos
type "app\build\intermediates\merged_manifests\debug\AndroidManifest.xml" | findstr /i "android:version"
REM 
REM Check if we should skip the release build and just make a debug build.
IF "%SKIP_RELEASE_BUILD%" == "1" goto :absPostBuildScript
REM
IF "%CLEANUP_BEFORE_BUILD%" == "1" del /f "%SCRIPT_PATH%app\build\outputs\apk\release\app-release.apk" 2> NUL:
echo [INFO] Building Android APK variant "release" ...
call gradlew --quiet assembleRelease
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew assembleRelease" exited with code #%RESULT%. & goto :eos
type "app\build\intermediates\merged_manifests\release\AndroidManifest.xml" | findstr /i "android:version"
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
REM Remove "app\src\main\play\listings\[lang]\graphics\icon" folders because of GPlay API Error #500.
rd /s /q "app\src\main\play\listings\de-DE\graphics\icon"
rd /s /q "app\src\main\play\listings\en-GB\graphics\icon"
REM 
REM Publish text and image resources to GPlay
echo [INFO] Publishing descriptive resources to GPlay ...
call gradlew --quiet publishReleaseListing
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew publishReleaseListing" exited with code #%RESULT%. & goto :eos
REM 
REM Publish APK to GPlay
echo [INFO] Publishing APK to GPlay ...
call gradlew --quiet publishRelease
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] "gradlew publishRelease" exited with code #%RESULT%. & goto :eos
REM 
REM Revert removed play icon resources.
git checkout -- "app\src\main\play\listings\de-DE\graphics\icon\*"
git checkout -- "app\src\main\play\listings\en-GB\graphics\icon\*"
REM 
goto :eos
:eos
REM 
echo [INFO] End of Script.
REM
pause
goto :eof


