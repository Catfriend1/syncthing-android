@echo off
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM
REM Runtime Variables.
REM 
where java 2>&1 1> NUL: || call ..\setenv.cmd
REM 
REM Download Google BundleTool from GitHub.
IF NOT EXIST "bundletool.jar" echo [INFO] Downloading [bundletool.jar] ... & wget --no-check-certificate -q -O"bundletool.jar" "https://github.com/google/bundletool/releases/download/0.11.0/bundletool-all-0.11.0.jar" 2> NUL:
IF NOT EXIST "bundletool.jar" echo [ERROR] Download [bundletool.jar] FAILED. & pause & goto :eof
REM 
REM Install ".apks" file which was previously extracted from ".aab" file.
IF NOT EXIST "%SCRIPT_PATH%extracted.apks" echo [ERROR] Extracted ".apks" file not found. & pause & goto :eof
echo [INFO] Installing APKs to device ... & java -jar "bundletool.jar" install-apks --apks="%SCRIPT_PATH%extracted.apks"
REM 
pause
goto :eof
