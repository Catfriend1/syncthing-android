@echo off
title %~nx0
cls
cd /d "%~dps0"
REM 
SET FORCE_FLAG=-f
REM 
echo Pulling all reviewed translations ...
tx pull -a --mode reviewed %FORCE_FLAG% -r "syncthing-android-1.stringsxml"
tx pull -a --mode reviewed %FORCE_FLAG% -r "syncthing-android-1.description_fulltxt"
tx pull -a --mode reviewed %FORCE_FLAG% -r "syncthing-android-1.description_shorttxt"
tx pull -a --mode reviewed %FORCE_FLAG% -r "syncthing-android-1.titletxt"
REM 
pause
