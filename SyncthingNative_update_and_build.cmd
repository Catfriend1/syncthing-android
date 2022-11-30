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
SET USE_GO_DEV=0
SET DESIRED_SUBMODULE_VERSION=v1.22.2-rc.3
SET GRADLEW_PARAMS=-q
REM
REM Runtime Variables.
IF NOT DEFINED ANDROID_SDK_ROOT SET "ANDROID_SDK_ROOT=%SCRIPT_PATH%..\syncthing-android-prereq"
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
IF NOT "%ERRORLEVEL%" == "0" SET PATH=%PATH%;%CommonProgramFiles%\Oracle\Java\javapath\
where /q java
IF NOT "%ERRORLEVEL%" == "0" echo [ERROR] java.exe not found on PATH env var. Download 'https://www.oracle.com/java/technologies/downloads/#java11-windows' and run the installer & pause & goto :checkPrerequisites
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
echo [INFO] Checking out syncthing_%DESIRED_SUBMODULE_VERSION% ...
git checkout %DESIRED_SUBMODULE_VERSION% 2>&1 | find /i "HEAD is now at"
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git checkout FAILED. & goto :eos
REM
:afterCheckoutSrc
cd /d "%SCRIPT_PATH%"
REM
IF "%USE_GO_DEV%" == "1" call :applyGoDev
REM
echo [INFO] Building submodule syncthing_%DESIRED_SUBMODULE_VERSION% ...
call gradlew %GRADLEW_PARAMS% buildNative
SET RESULT=%ERRORLEVEL%
IF "%USE_GO_DEV%" == "1" call :revertGoDev
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

:applyGoDev
REM
REM Syntax:
REM 	call :applyGoDev
REM
echo [INFO] Using go-dev instead of go-stable for this build ...
type "syncthing\build-syncthing.py" 2> NUL: | psreplace "GO_EXPECTED_SHASUM_WINDOWS = '2f4849b512fffb2cf2028608aa066cc1b79e730fd146c7b89015797162f08ec5'" "GO_EXPECTED_SHASUM_WINDOWS = '2fff556d0adaa6fda8300b0751a91a593c359f27265bc0fc7594f9eba794f907'" "syncthing\build-syncthing.py"
REM
goto :eof

:revertGoDev
REM
REM Syntax:
REM 	call :revertGoDev
REM
echo [INFO] Reverting to go-stable ...
git checkout -- "syncthing\build-syncthing.py"
SET TMP_RESULT=%ERRORLEVEL%
IF NOT "%TMP_RESULT%" == "0" echo [ERROR] git checkout "build-syncthing.py" FAILED. & pause & goto :eof
REM
goto :eof

:applyGoDevByCommit
REM
REM [UNUSED-FUNC]
REM
REM Syntax:
REM 	call :applyGoDevByCommit [commit/revert]
REM
REM Consts.
SET GO_DEV_COMMIT=032c562105b871c2a77e59e3be3de2ada26a365d
REM
IF "%1" == "commit" echo [INFO] Using go-dev instead of go-stable for this build ... & git cherry-pick --quiet %GO_DEV_COMMIT% & goto :eof
REM
REM Revert without leaving a commit on the master.
SET TMP_GODEV_COMMIT_CNT=0
for /f "delims= " %%A IN ('git log -1 --pretty^=oneline 2^>^&1 ^| findstr /I /C:"Build with godev"') do SET TMP_GODEV_COMMIT_CNT=1
IF NOT "%TMP_GODEV_COMMIT_CNT%" == "1" echo [ERROR] Failed to revert go-dev to go-stable - commit not found. & pause & goto :eof
echo [INFO] Reverting to go-stable ...
git reset --quiet --hard HEAD~1
SET TMP_RESULT=%ERRORLEVEL%
IF NOT "%TMP_RESULT%" == "0" echo [ERROR] git reset FAILED. & pause & goto :eof
REM
goto :eof

:eos
REM
echo [INFO] End of Script.
REM
pause
goto :eof
