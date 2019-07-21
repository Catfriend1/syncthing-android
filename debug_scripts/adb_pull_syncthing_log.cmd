@echo off
cls
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM 
REM Script Consts.
REM 	SET INTERNAL_STORAGE_MOUNT_POINT=/storage/emulated/0
SET INTERNAL_STORAGE_MOUNT_POINT=/storage/sdcard0
REM 
REM 	SET PACKAGE_NAME=com.github.catfriend1.syncthingandroid
SET PACKAGE_NAME=com.github.catfriend1.syncthingandroid.debug
REM
echo [INFO] Pulling "syncthing.log" from connected Android device ...
SET LOG_DATETIMESTAMP=%DATE:~-4%-%DATE:~-7,-5%-%DATE:~-10,-8%_%time:~-11,2%-%time:~-8,2%-%time:~-5,2%
SET LOCAL_FILE="%SCRIPT_PATH%%LOG_DATETIMESTAMP%_syncthing.log"
SET REMOTE_FILE="%INTERNAL_STORAGE_MOUNT_POINT%/Android/data/%PACKAGE_NAME%/files/syncthing.log"
adb pull %REMOTE_FILE% %LOCAL_FILE%
REM 
REM End of Script.
timeout 3
goto :eof


