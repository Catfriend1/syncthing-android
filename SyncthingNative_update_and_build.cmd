@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
title Update and Build SyncthingNative "libsyncthingnative.so"
cls
REM
REM Script Consts.
SET CLEAN_BEFORE_BUILD=1
SET SKIP_CHECKOUT_SRC=0
SET SYNCTHING_NATIVE_REQUIRED_VERSION=
SET GRADLEW_PARAMS=-q
REM
REM Runtime Variables.
IF EXIST "%LocalAppData%\Android\Sdk" SET "ANDROID_SDK_ROOT=%LocalAppData%\Android\Sdk"
IF NOT DEFINED ANDROID_SDK_ROOT SET "ANDROID_SDK_ROOT=%SCRIPT_PATH%..\syncthing-android-prereq"
IF NOT DEFINED JAVA_HOME SET JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr
REM
:checkPrerequisites
echo [INFO] Checking prerequisites ...
REM
SET GIT_BIN=
FOR /F "tokens=*" %%A IN ('where git 2^> NUL:') DO SET GIT_BIN="%%A"
IF NOT DEFINED GIT_BIN echo [ERROR] git not found. Install "Git for Windows" first and put it to the PATH env var. & pause & goto :checkPrerequisites
IF NOT EXIST %GIT_BIN% echo [ERROR] git not found. Install "Git for Windows" first and put it to the PATH env var. & pause & goto :checkPrerequisites
REM
where /q java
IF NOT "%ERRORLEVEL%" == "0" SET PATH=%PATH%;%JAVA_HOME%\bin
where /q java
IF NOT "%ERRORLEVEL%" == "0" echo [ERROR] java.exe not found on PATH env var. Download 'https://www.oracle.com/java/technologies/downloads/#java17' and run the installer & pause & goto :checkPrerequisites
REM
where /q python
IF NOT "%ERRORLEVEL%" == "0" echo [ERROR] python.exe not found on PATH env var. Download 'https://www.python.org/ftp/python/3.9.6/python-3.9.6-amd64.exe' and run 'python-3.9.6-amd64.exe /quiet InstallAllUsers=1 PrependPath=1 Include_test=0' & pause & goto :checkPrerequisites
REM
gradlew 2>&1 | find "ANDROID_SDK_ROOT" >NUL: && (echo [WARN] gradlew FAILED: Env var ANDROID_SDK_ROOT not set. Trying to run 'python install_minimum_android_sdk_prerequisites.py' ... & call python install_minimum_android_sdk_prerequisites.py)
gradlew 2>&1 | find "ANDROID_SDK_ROOT" >NUL: && (echo [ERROR] gradlew FAILED: Env var ANDROID_SDK_ROOT not set, run 'python install_minimum_android_sdk_prerequisites.py' first. & pause & goto :checkPrerequisites)
REM
IF "%CLEAN_BEFORE_BUILD%" == "1" call :cleanBeforeBuild
REM
IF "%SKIP_CHECKOUT_SRC%" == "1" goto :afterCheckoutSrc
REM
echo [INFO] Fetching submodule "Syncthing" 1/2 ...
md "%SCRIPT_PATH%syncthing\src\github.com\syncthing\syncthing" 2> NUL:
git submodule init
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git submodule init FAILED. & goto :eos
REM
echo [INFO] Fetching submodule "Syncthing" 2/2 ...
git submodule update --init --recursive --quiet
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git submodule update FAILED. & goto :eos
REM
cd /d "%SCRIPT_PATH%syncthing\src\github.com\syncthing\syncthing"
echo [INFO] Fetching GitHub tags ...
git fetch --quiet --all
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git fetch FAILED. & goto :eos
REM
echo [INFO] Reading required SyncthingNative from build.gradle.kts ...
IF NOT DEFINED SYNCTHING_NATIVE_REQUIRED_VERSION call :getRequiredSynchtingNativeVersion
REM
echo [INFO] Checking out syncthing_%SYNCTHING_NATIVE_REQUIRED_VERSION% ...
git checkout %SYNCTHING_NATIVE_REQUIRED_VERSION% 2>&1 | find /i "HEAD is now at"
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git checkout FAILED. & goto :eos
REM
:afterCheckoutSrc
cd /d "%SCRIPT_PATH%"
REM
echo [INFO] Building submodule syncthing_%SYNCTHING_NATIVE_REQUIRED_VERSION% ...
call gradlew %GRADLEW_PARAMS% buildNative
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] gradlew buildNative FAILED. & goto :eos
REM
echo [INFO] Reverting "go.mod", "go.sum" to checkout state ...
cd /d "%SCRIPT_PATH%syncthing\src\github.com\syncthing\syncthing"
git checkout -- go.mod
git checkout -- go.sum
cd /d "%SCRIPT_PATH%"
REM
echo [INFO] Checking if SyncthingNative was built successfully ...
REM
SET LIBCOUNT=
for /f "tokens=*" %%A IN ('dir /s /a "%SCRIPT_PATH%app\src\main\jniLibs\*" 2^>NUL: ^| find /C "libsyncthingnative.so"') DO SET LIBCOUNT=%%A
IF NOT "%LIBCOUNT%" == "4" echo [ERROR] SyncthingNative[s] "libsyncthingnative.so" are missing. You should fix that first. & goto :eos
REM
goto :eos


:cleanBeforeBuild
REM
REM Syntax:
REM 	call :cleanBeforeBuild
REM
echo [INFO] Performing cleanup ...
rd /s /q "app\src\main\jniLibs" 2> NUL:
IF NOT "%SKIP_CHECKOUT_SRC%" == "1" rd /s /q "syncthing\src\github.com\syncthing\syncthing" 2> NUL:
REM
goto :eof


:getRequiredSynchtingNativeVersion
REM 
REM Get "versionMajor"
SET VERSION_MAJOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionMajor"') DO SET VERSION_MAJOR=%%A
SET VERSION_MAJOR=%VERSION_MAJOR:"=%
REM 
REM Get "versionMinor"
SET VERSION_MINOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionMinor"') DO SET VERSION_MINOR=%%A
SET VERSION_MINOR=%VERSION_MINOR:"=%
REM 
REM Get "versionPatch"
SET VERSION_PATCH=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionPatch"') DO SET VERSION_PATCH=%%A
SET VERSION_PATCH=%VERSION_PATCH:"=%
REM
SET "SYNCTHING_NATIVE_REQUIRED_VERSION=v%VERSION_MAJOR%.%VERSION_MINOR%.%VERSION_PATCH%"
echo [INFO] SYNCTHING_NATIVE_REQUIRED_VERSION=[%SYNCTHING_NATIVE_REQUIRED_VERSION%]"
REM
goto :eof


:eos
REM
echo [INFO] End of Script.
REM
pause
goto :eof
