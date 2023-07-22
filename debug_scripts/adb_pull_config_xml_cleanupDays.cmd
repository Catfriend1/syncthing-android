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
adb shell "su root cat /data/data/%PACKAGE_NAME%/files/config.xml" > "%SCRIPT_PATH%config.xml"
IF EXIST "%SCRIPT_PATH%config.xml" TYPE "%SCRIPT_PATH%config.xml" | findstr /c:"<folder id=" /c:"<cleanupIntervalS>"  | findstr /v /c:"<folder id=""""" | ..\psreplace ".*label=" "" | ..\psreplace "path=.*" ""
echo.
pause
echo.
echo ==========================================================
echo.
goto :loopMe
