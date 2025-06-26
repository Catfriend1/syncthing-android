@echo off
setlocal enabledelayedexpansion
title Update "fdroid" with "main" branch
::
:: Relauch from local drive.
SET "RELAUNCH_PATH=%TEMP%\%~nx0"
IF /I NOT "%~dpnx0" == "%RELAUNCH_PATH%" SET "PROJECT_ROOT=%~dp0.." & copy /y "%~dpnx0" "%RELAUNCH_PATH%" >NUL: & echo [INFO] Relaunch from local drive & call "%RELAUNCH_PATH%" %1 %2 %3 %4 %5 %6 %7 %8 %9 & goto :eof
::
echo [INFO] Relaunched.
cd /d "%PROJECT_ROOT%"
::
:: Runtime Variables.
SET "APP_BUILD_GRADLE=app\build.gradle.kts"
::
:: Consts.
SET DRY_RUN=0
::
call :runGit fetch --all
::
IF "%DRY_RUN%" == "0" call :runGit checkout fdroid
IF "%DRY_RUN%" == "0" call :runGit merge --no-commit main
::
call :readVersionFromVersionsGradle
echo [INFO] VERSION_NAME=[%VERSION_NAME%]
echo [INFO] VERSION_CODE=[%VERSION_CODE%]
::
:: Write "versionName" and "versionCode" to "build.gradle".
TYPE "%APP_BUILD_GRADLE%" 2>NUL: | psreplace "\sversionCode = .*" " versionCode = %VERSION_CODE%" | psreplace "\sversionName = .*" " versionName = `%VERSION_NAME%`" "%APP_BUILD_GRADLE%"
::
echo [INFO] Done.
pause
::
goto :eof










:: ====================
:: FUNCTION BLOCK START
:: ====================
:readVersionFromVersionsGradle
::
:: Get "versionMajor"
SET VERSION_MAJOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "build.gradle.kts" 2^>^&1 ^| findstr "versionMajor"') DO SET VERSION_MAJOR=%%A
SET VERSION_MAJOR=%VERSION_MAJOR:"=%
:: echo [INFO] versionMajor="%VERSION_MAJOR%"
::
:: Get "versionMinor"
SET VERSION_MINOR=
FOR /F "tokens=2 delims==) " %%A IN ('type "build.gradle.kts" 2^>^&1 ^| findstr "versionMinor"') DO SET VERSION_MINOR=%%A
SET VERSION_MINOR=%VERSION_MINOR:"=%
:: echo [INFO] versionMinor="%VERSION_MINOR%"
::
:: Get "versionPatch"
SET VERSION_PATCH=
FOR /F "tokens=2 delims==) " %%A IN ('type "build.gradle.kts" 2^>^&1 ^| findstr "versionPatch"') DO SET VERSION_PATCH=%%A
SET VERSION_PATCH=%VERSION_PATCH:"=%
:: echo [INFO] versionPatch="%VERSION_PATCH%"
::
:: Get "versionWrapper"
SET VERSION_WRAPPER=
FOR /F "tokens=2 delims==) " %%A IN ('type "build.gradle.kts" 2^>^&1 ^| findstr "versionWrapper"') DO SET VERSION_WRAPPER=%%A
SET VERSION_WRAPPER=%VERSION_WRAPPER:"=%
:: echo [INFO] versionWrapper="%VERSION_WRAPPER%"
::
SET VERSION_NAME=%VERSION_MAJOR%.%VERSION_MINOR%.%VERSION_PATCH%.%VERSION_WRAPPER%
::
:: Calculate "versionCode".
SET VERSION_CODE_MAJOR=%VERSION_MAJOR%
:: 
call :addTrailingZerosToVar %VERSION_MINOR%
SET VERSION_CODE_MINOR=%ATZTV_PADDED%
::
call :addTrailingZerosToVar %VERSION_MINOR%
SET VERSION_CODE_MINOR=%ATZTV_PADDED%
::
call :addTrailingZerosToVar %VERSION_PATCH%
SET VERSION_CODE_PATCH=%ATZTV_PADDED%
::
call :addTrailingZerosToVar %VERSION_WRAPPER%
SET VERSION_CODE_WRAPPER=%ATZTV_PADDED%
::
SET VERSION_CODE=%VERSION_CODE_MAJOR%%VERSION_CODE_MINOR%%VERSION_CODE_PATCH%%VERSION_CODE_WRAPPER%
::
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
:: ==================
:: FUNCTION BLOCK END
:: ==================
