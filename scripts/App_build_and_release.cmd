@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
cls
REM
REM Script Consts.
SET CLEANUP_BEFORE_BUILD=0
REM
REM Runtime Variables.
IF NOT DEFINED ANDROID_SDK_ROOT SET "ANDROID_SDK_ROOT=%SCRIPT_PATH%..\syncthing-android-prereq"
REM
REM SET SYNCTHING_RELEASE_STORE_FILE="%userprofile%\.android\signing_key.jks"
SET SYNCTHING_RELEASE_KEY_ALIAS=Syncthing-Fork
SET BUILD_FLAVOUR_RELEASE=release
SET BUILD_FLAVOUR_GPLAY=gplay
title %SYNCTHING_RELEASE_KEY_ALIAS% - Build APK
REM
SET GIT_INSTALL_DIR=%ProgramFiles%\Git
SET GIT_BIN="%GIT_INSTALL_DIR%\bin\git.exe"
REM
SET JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr
SET PATH=%JAVA_HOME%\bin;"%GIT_INSTALL_DIR%\bin";%PATH%
REM
:checkPrerequisites
echo [INFO] Checking prerequisites ...
REM
where java 2>&1 >NUL: || (echo [ERROR] Java is missing. Check env. & goto :eof)
REM
IF NOT EXIST "%ANDROID_SDK_ROOT%\.knownPackages" (echo [WARN] Android SDK missing. Trying to run 'python install_minimum_android_sdk_prerequisites.py' ... & call python install_minimum_android_sdk_prerequisites.py)
REM
echo [INFO] Checking if SyncthingNative was built before starting this script ...
SET LIBCOUNT=
for /f "tokens=*" %%A IN ('dir /s /a "%SCRIPT_PATH%app\src\main\jniLibs\*" 2^>NUL: ^| find /C "libsyncthingnative.so"') DO SET LIBCOUNT=%%A
IF NOT "%LIBCOUNT%" == "4" echo [ERROR] SyncthingNative[s] "libsyncthingnative.so" are missing. Please run "gradlew buildNative" first. & goto :eos
REM
echo [INFO] Let's prepare a new "%SYNCTHING_RELEASE_KEY_ALIAS%" release.
REM
echo [INFO] Checking release prerequisites ...
REM
REM User has to enter the signing password if it is not filled in here.
REM SET SIGNING_PASSWORD=
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
REM
call gradlew lint%BUILD_FLAVOUR_RELEASE% & SET RESULT=%ERRORLEVEL%
IF NOT "!RESULT!" == "0" echo [ERROR] "gradlew lint%BUILD_FLAVOUR_RELEASE%" exited with code #%RESULT%. & goto :eos
REM
call gradlew lint%BUILD_FLAVOUR_GPLAY% & SET RESULT=%ERRORLEVEL%
IF NOT "!RESULT!" == "0" echo [ERROR] "gradlew lint%BUILD_FLAVOUR_GPLAY%" exited with code #%RESULT%. & goto :eos
REM
REM Building APK
REM
call :buildApk %BUILD_FLAVOUR_RELEASE%
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
type "app\build\intermediates\merged_manifests\%BA_BUILD_TYPE%\process%BA_BUILD_TYPE%Manifest\AndroidManifest.xml" | findstr /i "android:version"
REM
goto :eof
