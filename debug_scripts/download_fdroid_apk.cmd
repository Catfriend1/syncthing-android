@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
cls
REM 
REM Script Consts.
REM
REM Runtime Variables.
REM 
title Download F-Droid APK
REM 
call :readVersionFromVersionsGradle
echo [INFO] applicationId="%APPLICATION_ID%"
echo [INFO] VERSION_NAME=[%VERSION_NAME%]
echo [INFO] VERSION_CODE=[%VERSION_CODE%]
REM 
REM For testing purposes only.
REM 	SET VERSION_CODE=1020204
REM 
SET FDROID_REPO_APK_URL="https://f-droid.org/repo/%APPLICATION_ID%_%VERSION_CODE%.apk"
echo [INFO] Downloading %FDROID_REPO_APK_URL% ...
wget -q --no-check-certificate %FDROID_REPO_APK_URL% 2> NUL:
SET WGET_RESULT=%ERRORLEVEL%
IF NOT "%WGET_RESULT%" == "0" echo [ERROR] wget FAILED with code #%WGET_RESULT%. & goto :pauseExit
echo [INFO] Download successful.
REM 
echo [INFO] End of Script.
goto :pauseExit


REM ====================
REM FUNCTION BLOCK START
REM ====================
:readVersionFromVersionsGradle
REM 
REM Variables.
SET BUILD_GRADLE="%SCRIPT_PATH%..\app\build.gradle"
SET VERSIONS_GRADLE="%SCRIPT_PATH%..\app\versions.gradle"
REM 
IF NOT EXIST %BUILD_GRADLE% echo [ERROR] BUILD_GRADLE - File not found. & pause & goto :eof
IF NOT EXIST %VERSIONS_GRADLE% echo [ERROR] VERSIONS_GRADLE - File not found. & pause & goto :eof
REM 
REM Get "applicationId"
SET APPLICATION_ID=
FOR /F "tokens=2 delims= " %%A IN ('type %BUILD_GRADLE% 2^>^&1 ^| findstr "applicationId"') DO SET APPLICATION_ID=%%A
SET APPLICATION_ID=%APPLICATION_ID:"=%
REM 
REM Get "versionMajor"
SET VERSION_MAJOR=
FOR /F "tokens=2 delims== " %%A IN ('type %VERSIONS_GRADLE% 2^>^&1 ^| findstr "versionMajor"') DO SET VERSION_MAJOR=%%A
SET VERSION_MAJOR=%VERSION_MAJOR:"=%
REM echo [INFO] versionMajor="%VERSION_MAJOR%"
REM 
REM Get "versionMinor"
SET VERSION_MINOR=
FOR /F "tokens=2 delims== " %%A IN ('type %VERSIONS_GRADLE% 2^>^&1 ^| findstr "versionMinor"') DO SET VERSION_MINOR=%%A
SET VERSION_MINOR=%VERSION_MINOR:"=%
REM echo [INFO] versionMinor="%VERSION_MINOR%"
REM 
REM Get "versionPatch"
SET VERSION_PATCH=
FOR /F "tokens=2 delims== " %%A IN ('type %VERSIONS_GRADLE% 2^>^&1 ^| findstr "versionPatch"') DO SET VERSION_PATCH=%%A
SET VERSION_PATCH=%VERSION_PATCH:"=%
REM echo [INFO] versionPatch="%VERSION_PATCH%"
REM 
REM Get "versionWrapper"
SET VERSION_WRAPPER=
FOR /F "tokens=2 delims== " %%A IN ('type %VERSIONS_GRADLE% 2^>^&1 ^| findstr "versionWrapper"') DO SET VERSION_WRAPPER=%%A
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
