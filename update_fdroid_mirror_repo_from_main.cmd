@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
cls
title Syncthing-Fork - Update F-Droid Mirror Repo from main
REM
REM Runtime Variables.
SET APP_BUILD_GRADLE=%~dps0app\build.gradle.kts
REM
call :runGit fetch --all
REM
git remote remove upstream 2>NUL:
call :runGit remote add upstream https://github.com/Catfriend1/syncthing-android.git
call :runGit pull upstream main
REM
call :readVersionFromVersionsGradle
echo [INFO] applicationId="%APPLICATION_ID%"
echo [INFO] VERSION_NAME=[%VERSION_NAME%]
echo [INFO] VERSION_CODE=[%VERSION_CODE%]
REM
REM Write "versionName" and "versionCode" to "build.gradle".
TYPE "%APP_BUILD_GRADLE%" 2>NUL: | psreplace "\sversionCode = .*" " versionCode = %VERSION_CODE%" | psreplace "\sversionName = .*" " versionName = `%VERSION_NAME%`" "%APP_BUILD_GRADLE%"
REM
goto :eof










REM ====================
REM FUNCTION BLOCK START
REM ====================
:readVersionFromVersionsGradle
REM
REM Get "applicationId"
SET APPLICATION_ID=
FOR /F "tokens=3 delims=) " %%A IN ('type "%SCRIPT_PATH%app\build.gradle.kts" 2^>^&1 ^| findstr "applicationId"') DO SET APPLICATION_ID=%%A
SET APPLICATION_ID=%APPLICATION_ID:"=%
REM
REM Get "versionMajor"
SET VERSION_MAJOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionMajor"') DO SET VERSION_MAJOR=%%A
SET VERSION_MAJOR=%VERSION_MAJOR:"=%
REM echo [INFO] versionMajor="%VERSION_MAJOR%"
REM
REM Get "versionMinor"
SET VERSION_MINOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionMinor"') DO SET VERSION_MINOR=%%A
SET VERSION_MINOR=%VERSION_MINOR:"=%
REM echo [INFO] versionMinor="%VERSION_MINOR%"
REM
REM Get "versionPatch"
SET VERSION_PATCH=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionPatch"') DO SET VERSION_PATCH=%%A
SET VERSION_PATCH=%VERSION_PATCH:"=%
REM echo [INFO] versionPatch="%VERSION_PATCH%"
REM
REM Get "versionWrapper"
SET VERSION_WRAPPER=
FOR /F "tokens=2 delims==) " %%A IN ('type "%SCRIPT_PATH%build.gradle.kts" 2^>^&1 ^| findstr "versionWrapper"') DO SET VERSION_WRAPPER=%%A
SET VERSION_WRAPPER=%VERSION_WRAPPER:"=%
REM echo [INFO] versionWrapper="%VERSION_WRAPPER%"
REM
SET VERSION_NAME=%VERSION_MAJOR%.%VERSION_MINOR%.%VERSION_PATCH%.%VERSION_WRAPPER%
REM
REM Calculate "versionCode".
SET VERSION_CODE_MAJOR=%VERSION_MAJOR%
REM 
call :addTrailingZerosToVar %VERSION_MINOR%
SET VERSION_CODE_MINOR=%ATZTV_PADDED%
REM
call :addTrailingZerosToVar %VERSION_MINOR%
SET VERSION_CODE_MINOR=%ATZTV_PADDED%
REM
call :addTrailingZerosToVar %VERSION_PATCH%
SET VERSION_CODE_PATCH=%ATZTV_PADDED%
REM
call :addTrailingZerosToVar %VERSION_WRAPPER%
SET VERSION_CODE_WRAPPER=%ATZTV_PADDED%
REM
SET VERSION_CODE=%VERSION_CODE_MAJOR%%VERSION_CODE_MINOR%%VERSION_CODE_PATCH%%VERSION_CODE_WRAPPER%
REM
goto :eof


:runGit
echo [INFO] git %1 %2 %3 %4 %5 %6 %7 %8 %9
git %1 %2 %3 %4 %5 %6 %7 %8 %9
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git FAILED with error code #%RESULT%. & goto :pauseExit
goto :eof


:addTrailingZerosToVar
SET ATZTV_P1=0000000000000%1
SET "ATZTV_PADDED=!ATZTV_P1:~-2!"
goto :eof


:pauseExit
pause
goto :eof
REM ==================
REM FUNCTION BLOCK END
REM ==================
