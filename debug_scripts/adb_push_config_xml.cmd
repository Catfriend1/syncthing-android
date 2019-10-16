@echo off
cls
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM 
REM Script Consts.
REM 
REM 	SET PACKAGE_NAME=com.github.catfriend1.syncthingandroid
SET PACKAGE_NAME=com.github.catfriend1.syncthingandroid.debug
REM 
REM 	SET DATA_ROOT=/data/user/0
SET DATA_ROOT=/data/data
REM 
SET USE_ROOT=0
SET PACKAGE_UID=u0_a96
REM 
:loopMe
cls
adb root
IF "%USE_ROOT%" == "1" adb shell ls -a -l "%DATA_ROOT%/%PACKAGE_NAME%/files/config.xml"
adb push "%SCRIPT_PATH%config.xml" "%DATA_ROOT%/%PACKAGE_NAME%/files/config.xml"
IF "%USE_ROOT%" == "1" adb shell chmod 0600 "%DATA_ROOT%/%PACKAGE_NAME%/files/config.xml"
IF "%USE_ROOT%" == "1" adb shell chown %PACKAGE_UID%:%PACKAGE_UID% "%DATA_ROOT%/%PACKAGE_NAME%/files/config.xml"
IF "%USE_ROOT%" == "1" adb shell ls -a -l "%DATA_ROOT%/%PACKAGE_NAME%/files/config.xml"
echo.
pause
echo.
echo ==========================================================
echo.
goto :loopMe
