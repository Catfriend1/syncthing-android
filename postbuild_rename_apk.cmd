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
FOR /F "tokens=2 delims= " %%A IN ('type "%SCRIPT_PATH%app\build.gradle" 2^>^&1 ^| findstr "applicationId"') DO SET APPLICATION_ID=%%A
SET APPLICATION_ID=%APPLICATION_ID:"=%
echo [INFO] applicationId="%APPLICATION_ID%"
REM 
REM Get "versionName"
FOR /F "tokens=2 delims= " %%A IN ('type "%SCRIPT_PATH%app\build.gradle" 2^>^&1 ^| findstr "versionName"') DO SET VERSION_NAME=%%A
SET VERSION_NAME=%VERSION_NAME:"=%
echo [INFO] versionName="%VERSION_NAME%"
REM 
REM Get short hash of last commit.
IF NOT EXIST %GIT_BIN% echo [ERROR] git.exe not found. & pause & goto :eof
pushd %SCRIPT_PATH%
FOR /F "tokens=1" %%A IN ('git rev-parse --short --verify HEAD 2^>NUL:') DO SET COMMIT_SHORT_HASH=%%A
popd
echo [INFO] commit="%COMMIT_SHORT_HASH%"
REM 
REM Rename APK to be ready for upload to the GitHub release page.
SET APK_NEW_FILENAME=%APPLICATION_ID%_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.apk
call :renIfExist %SCRIPT_PATH%app\build\outputs\apk\debug\app-debug.apk %APK_NEW_FILENAME%
call :renIfExist %SCRIPT_PATH%app\build\outputs\apk\release\app-release-unsigned.apk %APK_NEW_FILENAME%
echo [INFO] APK file rename step complete.
REM 
REM Copy debug APK to temporary storage location if the storage is available.
IF EXIST %TEMP_OUTPUT_FOLDER% copy /y %SCRIPT_PATH%app\build\outputs\apk\debug\%APK_NEW_FILENAME% %TEMP_OUTPUT_FOLDER% 2> NUL:
REM 
echo [INFO] End of Script.
timeout 3
goto :eof

:renIfExist
REM 
REM Syntax:
REM 	call :renIfExist [FULL_FN_ORIGINAL] [FILENAME_RENAMED]
IF EXIST %1 REN %1 %2 & goto :eof
echo [INFO] File not found: %1
REM 
goto :eof
