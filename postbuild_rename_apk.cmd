@echo off
REM 
REM Purpose:
REM 	Rename and move built APK's to match this style:
REM 		[APPLICATION_ID]_v[VERSION_NAME]_[COMMIT_SHORT_HASH].apk
REM 	Example:
REM 		com.github.catfriend1.syncthingandroid_v1.0.0.1_7d59e75.apk
REM 
title %~nx0
setlocal enabledelayedexpansion
cls
SET SCRIPT_PATH=%~dps0
SET TEMP_OUTPUT_FOLDER=X:\
SET GIT_INSTALL_DIR=%ProgramFiles%\Git
SET GIT_BIN="%GIT_INSTALL_DIR%\bin\git.exe"
REM 
SET PATH=%PATH%;"%GIT_INSTALL_DIR%\bin"
REM 
REM Get "applicationId"
SET APPLICATION_ID=
FOR /F "tokens=2 delims= " %%A IN ('type "%SCRIPT_PATH%app\build.gradle" 2^>^&1 ^| findstr "applicationId"') DO SET APPLICATION_ID=%%A
SET APPLICATION_ID=%APPLICATION_ID:"=%
echo [INFO] applicationId="%APPLICATION_ID%"
REM 
REM Get "versionMajor"
SET VERSION_MAJOR=
FOR /F "tokens=2 delims== " %%A IN ('type "%SCRIPT_PATH%app\versions.gradle" 2^>^&1 ^| findstr "versionMajor"') DO SET VERSION_MAJOR=%%A
SET VERSION_MAJOR=%VERSION_MAJOR:"=%
REM echo [INFO] versionMajor="%VERSION_MAJOR%"
REM 
REM Get "versionMinor"
SET VERSION_MINOR=
FOR /F "tokens=2 delims== " %%A IN ('type "%SCRIPT_PATH%app\versions.gradle" 2^>^&1 ^| findstr "versionMinor"') DO SET VERSION_MINOR=%%A
SET VERSION_MINOR=%VERSION_MINOR:"=%
REM echo [INFO] versionMinor="%VERSION_MINOR%"
REM 
REM Get "versionPatch"
SET VERSION_PATCH=
FOR /F "tokens=2 delims== " %%A IN ('type "%SCRIPT_PATH%app\versions.gradle" 2^>^&1 ^| findstr "versionPatch"') DO SET VERSION_PATCH=%%A
SET VERSION_PATCH=%VERSION_PATCH:"=%
REM echo [INFO] versionPatch="%VERSION_PATCH%"
REM 
REM Get "versionWrapper"
SET VERSION_WRAPPER=
FOR /F "tokens=2 delims== " %%A IN ('type "%SCRIPT_PATH%app\versions.gradle" 2^>^&1 ^| findstr "versionWrapper"') DO SET VERSION_WRAPPER=%%A
SET VERSION_WRAPPER=%VERSION_WRAPPER:"=%
REM echo [INFO] versionWrapper="%VERSION_WRAPPER%"
REM
SET VERSION_NAME=%VERSION_MAJOR%.%VERSION_MINOR%.%VERSION_PATCH%.%VERSION_WRAPPER%
echo [INFO] VERSION_NAME=[%VERSION_NAME%]
REM 
REM Get short hash of last commit.
IF NOT EXIST %GIT_BIN% echo [ERROR] git.exe not found. & pause & goto :eof
pushd %SCRIPT_PATH%
FOR /F "tokens=1" %%A IN ('git rev-parse --short --verify HEAD 2^>NUL:') DO SET COMMIT_SHORT_HASH=%%A
popd
echo [INFO] commit="%COMMIT_SHORT_HASH%"
REM 
REM Copy APK to be ready for upload to the GitHub release page.
SET APK_GITHUB_NEW_FILENAME=%APPLICATION_ID%_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.apk
REM call :renIfExist %SCRIPT_PATH%app\build\outputs\apk\debug\app-debug.apk %APK_GITHUB_NEW_FILENAME%
call :copyIfExist %SCRIPT_PATH%app\build\outputs\apk\debug\app-debug.apk %SCRIPT_PATH%app\build\outputs\apk\debug\%APK_GITHUB_NEW_FILENAME%
REM 
SET APK_GPLAY_NEW_FILENAME=%APPLICATION_ID%_gplay_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.apk
REM call :renIfExist %SCRIPT_PATH%app\build\outputs\apk\release\app-release.apk %APK_GPLAY_NEW_FILENAME%
call :copyIfExist %SCRIPT_PATH%app\build\outputs\apk\release\app-release.apk %SCRIPT_PATH%app\build\outputs\apk\release\%APK_GPLAY_NEW_FILENAME%
echo [INFO] APK file copy step complete.
REM 
REM Copy both APK to temporary storage location if the storage is available.
IF EXIST %TEMP_OUTPUT_FOLDER% copy /y %SCRIPT_PATH%app\build\outputs\apk\debug\%APK_GITHUB_NEW_FILENAME% %TEMP_OUTPUT_FOLDER% 2> NUL:
IF EXIST %TEMP_OUTPUT_FOLDER% copy /y %SCRIPT_PATH%app\build\outputs\apk\release\%APK_GPLAY_NEW_FILENAME% %TEMP_OUTPUT_FOLDER% 2> NUL:
REM 
echo [INFO] End of Script.
timeout 3
goto :eof

:copyIfExist
REM 
REM Syntax:
REM 	call :copyIfExist [FULL_FN_ORIGINAL] [FILENAME_COPY_TARGET]
REM IF EXIST %1 REN %1 %2 & goto :eof
IF EXIST %1 copy /y %1 %2 & goto :eof
echo [INFO] File not found: %1
REM 
goto :eof

:renIfExist
REM 
REM Syntax:
REM 	call :renIfExist [FULL_FN_ORIGINAL] [FILENAME_RENAMED]
IF EXIST %1 REN %1 %2 & goto :eof
echo [INFO] File not found: %1
REM 
goto :eof
