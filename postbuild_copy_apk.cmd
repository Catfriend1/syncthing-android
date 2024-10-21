@echo off
REM 
REM Purpose:
REM 	Copy built APK's to match this style:
REM 		[APPLICATION_ID]_v[VERSION_NAME]_[COMMIT_SHORT_HASH].apk
REM 	Example:
REM 		com.github.catfriend1.syncthingandroid_v1.0.0.1_7d59e75.apk
REM 
title %~nx0
setlocal enabledelayedexpansion
REM 
REM Runtime variables.
SET SCRIPT_PATH=%~dps0
SET PACKAGE_SOURCE_CODE=1
SET TEMP_OUTPUT_FOLDER=X:
REM 
REM SET GIT_INSTALL_DIR=%ProgramFiles%\Git
REM SET GIT_BIN="%GIT_INSTALL_DIR%\bin\git.exe"
REM SET PATH=%PATH%;"%GIT_INSTALL_DIR%\bin"
REM 
echo [INFO] *** postbuild_copy_apk BEGIN ***
REM 
IF NOT DEFINED BUILD_FLAVOUR_GPLAY echo [ERROR] Env var BUILD_FLAVOUR_GPLAY not defined. & SET "BUILD_FLAVOUR_GPLAY=release"
REM 
REM Get "applicationId"
SET APPLICATION_ID=
FOR /F "tokens=3 delims= " %%A IN ('type "%SCRIPT_PATH%app\build.gradle.kts" 2^>^&1 ^| findstr /c:"applicationId "') DO SET APPLICATION_ID=%%A
SET APPLICATION_ID=%APPLICATION_ID:"=%
echo [INFO] applicationId="%APPLICATION_ID%"
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
REM Get short hash of last commit.
REM IF NOT EXIST %GIT_BIN% echo [ERROR] git.exe not found. & pause & goto :eof
pushd %SCRIPT_PATH%
SET COMMIT_LONG_HASH=
FOR /F "tokens=1" %%A IN ('git rev-parse --verify HEAD 2^>NUL:') DO SET COMMIT_LONG_HASH=%%A
REM 
SET COMMIT_SHORT_HASH=
FOR /F "tokens=1" %%A IN ('git rev-parse --short --verify HEAD 2^>NUL:') DO SET COMMIT_SHORT_HASH=%%A
popd
echo [INFO] VERSION_NAME=[%VERSION_NAME%], commit=[%COMMIT_SHORT_HASH%]=[%COMMIT_LONG_HASH%].
echo [INFO] Copying APK to same directory ...
REM 
REM Copy APK to be ready for upload to the GitHub release page.
SET APK_GITHUB_NEW_FILENAME=%APPLICATION_ID%_github_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.apk
call :copyIfExist %SCRIPT_PATH%app\build\outputs\apk\debug\app-debug.apk %SCRIPT_PATH%app\build\outputs\apk\debug\%APK_GITHUB_NEW_FILENAME%
REM 
SET APK_GPLAY_NEW_FILENAME=%APPLICATION_ID%_gplay_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.apk
IF NOT "%SKIP_RELEASE_BUILD%" == "1" call :copyIfExist %SCRIPT_PATH%app\build\outputs\apk\%BUILD_FLAVOUR_GPLAY%\app-%BUILD_FLAVOUR_GPLAY%.apk %SCRIPT_PATH%app\build\outputs\apk\%BUILD_FLAVOUR_GPLAY%\%APK_GPLAY_NEW_FILENAME%
REM 
REM Copy both APK to temporary storage location if the storage is available.
IF EXIST %TEMP_OUTPUT_FOLDER%\ (
	echo [INFO] Copying APK to [%TEMP_OUTPUT_FOLDER%] ...
	copy /y %SCRIPT_PATH%app\build\outputs\apk\debug\%APK_GITHUB_NEW_FILENAME% %TEMP_OUTPUT_FOLDER%\ 2> NUL:
	IF NOT "%SKIP_RELEASE_BUILD%" == "1" copy /y %SCRIPT_PATH%app\build\outputs\apk\%BUILD_FLAVOUR_GPLAY%\%APK_GPLAY_NEW_FILENAME% %TEMP_OUTPUT_FOLDER%\ 2> NUL:
)
REM 
IF "%PACKAGE_SOURCE_CODE%" == "1" call :packageSourceCode
REM 
echo [INFO] *** postbuild_copy_apk END ***
REM timeout 3
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


:getFileSize
REM 
REM Get file size to variable defined in parameter #2.
SET %~2=%~z1
REM 
goto :eof


:packageSourceCode
REM 
REM Syntax:
REM 	call :packageSourceCode
REM 
REM Global variables.
REM 	[IN] COMMIT_LONG_HASH
REM 	[IN] COMMIT_SHORT_HASH
REM 	[IN] TEMP_OUTPUT_FOLDER
REM 	[IN] VERSION_NAME
REM 
REM Variables.
SET TMP_DSC_ZIPFILE_FULLFN="%TEMP_OUTPUT_FOLDER%\%DATE:~-4%-%DATE:~-7,-5%-%DATE:~-10,-8%_com.github.catfriend1.syncthingandroid_v%VERSION_NAME%_%COMMIT_SHORT_HASH%.zip"
SET TMP_DSC_SEVENZIP_EXE="%ProgramFiles%\7-Zip\7z.exe"
REM 
REM Check prerequisites.
where curl 1> NUL: 2>&1 || (echo [ERROR] curl not found on PATH. & goto :eof)
IF NOT EXIST %TEMP_OUTPUT_FOLDER%\ echo [ERROR] TEMP_OUTPUT_FOLDER=[%TEMP_OUTPUT_FOLDER%] not found. & goto :eof
IF NOT EXIST %TMP_DSC_SEVENZIP_EXE% echo [ERROR] TMP_DSC_SEVENZIP_EXE=[%TMP_DSC_SEVENZIP_EXE%] not found. & goto :eof
REM 
REM Download source code for current build commit as ZIP.
:packageSourceCodeRetry
echo [INFO] Downloading source code ZIP from GitHub ...
curl -s -k -L -o %TMP_DSC_ZIPFILE_FULLFN% "https://github.com/Catfriend1/syncthing-android/archive/%COMMIT_LONG_HASH%.zip"
IF NOT EXIST %TMP_DSC_ZIPFILE_FULLFN% echo [ERROR] Download source code FAILED #1. & pause & goto :eof
call :getFileSize %TMP_DSC_ZIPFILE_FULLFN% FILE_SIZE
IF "%FILE_SIZE%" == "" echo [ERROR] Download source code FAILED #2. & pause & goto :eof
IF %FILE_SIZE% LSS 23 echo [ERROR] Download source code FAILED #3. & DEL /F %TMP_DSC_ZIPFILE_FULLFN% & pause & goto :packageSourceCodeRetry
REM 
REM Package built APKs into ZIP.
echo [INFO] Adding built APKs to source code ZIP ...
%TMP_DSC_SEVENZIP_EXE% -y -bso0 a %TMP_DSC_ZIPFILE_FULLFN% %TEMP_OUTPUT_FOLDER%\%APK_GITHUB_NEW_FILENAME%
IF NOT "%SKIP_RELEASE_BUILD%" == "1" %TMP_DSC_SEVENZIP_EXE% -y -bso0 a %TMP_DSC_ZIPFILE_FULLFN% %TEMP_OUTPUT_FOLDER%\%APK_GPLAY_NEW_FILENAME%
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
