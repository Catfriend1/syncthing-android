@echo off
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM
REM Runtime Variables.
SET SYNCTHING_RELEASE_KEY_ALIAS=Syncthing-Fork
REM 
where java 2>&1 1> NUL: || call ..\setenv.cmd
REM 
REM Check prerequisites.
IF NOT DEFINED SYNCTHING_RELEASE_KEY_ALIAS echo [ERROR] Env var [SYNCTHING_RELEASE_KEY_ALIAS] not defined. & pause & goto :eof
IF NOT DEFINED SYNCTHING_RELEASE_STORE_FILE echo [ERROR] Env var [SYNCTHING_RELEASE_STORE_FILE] not defined. & pause & goto :eof
REM 
REM Download Google BundleTool from GitHub.
IF NOT EXIST "bundletool.jar" echo [INFO] Downloading [bundletool.jar] ... & wget --no-check-certificate -q -O"bundletool.jar" "https://github.com/google/bundletool/releases/download/0.11.0/bundletool-all-0.11.0.jar" 2> NUL:
IF NOT EXIST "bundletool.jar" echo [ERROR] Download [bundletool.jar] FAILED. & pause & goto :eof
REM 
REM Extract APKs from Android Bundle "AAB" file.
DEL /F "%SCRIPT_PATH%extracted.apks"
IF NOT EXIST "%SCRIPT_PATH%extracted.apks" echo [INFO] Extracting Android Bundle ... & java -jar "bundletool.jar" build-apks --bundle="..\app\build\outputs\bundle\release\app-release.aab" --output="%SCRIPT_PATH%extracted.apks" --ks="%SYNCTHING_RELEASE_STORE_FILE%" --ks-key-alias=%SYNCTHING_RELEASE_KEY_ALIAS%
IF NOT EXIST "%SCRIPT_PATH%extracted.apks" echo [ERROR] Extracting Android Bundle FAILED. & pause & goto :eof
REM 
where unzip 2>&1 1> NUL: && call :unzipExtractedApks
REM 
goto :eof


:unzipExtractedApks
REM 
REM Syntax:
REM 	call :unzipExtractedApks
REM 
rd /s /q "%SCRIPT_PATH%extracted_apks" 2> NUL:
md "%SCRIPT_PATH%extracted_apks" 2> NUL:
echo [INFO] Extracting APKs ...
unzip -q -o -d "%SCRIPT_PATH%extracted_apks" "%SCRIPT_PATH%extracted.apks"
SET UNZIP_RESULT=%ERRORLEVEL%
REM IF "%UNZIP_RESULT%" == "0" DEL /F "%SCRIPT_PATH%extracted.apks"
REM 
goto :eof
