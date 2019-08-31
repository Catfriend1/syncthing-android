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
:loopMe
cls
adb root
adb push "%SCRIPT_PATH%config.xml" "/data/data/%PACKAGE_NAME%/files/config.xml"
echo.
pause
echo.
echo ==========================================================
echo.
goto :loopMe
