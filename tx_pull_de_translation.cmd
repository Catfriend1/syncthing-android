@echo off
cls
cd /d "%~dps0"
REM 
echo Pulling reviewed DE translation ...
tx pull -l de --mode reviewed -r "syncthing-android-1.stringsxml"
pause
