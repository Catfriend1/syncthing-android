@echo off
title %~nx0
cls
cd /d "%~dps0"
REM 
SET FORCE_FLAG=-f
REM 
echo Pulling all reviewed translations ...
tx pull -a --parallel %FORCE_FLAG% -r "syncthing-android-1.stringsxml"
tx pull -a --parallel %FORCE_FLAG% -r "syncthing-android-1.description_fulltxt"
tx pull -a --parallel %FORCE_FLAG% -r "syncthing-android-1.description_shorttxt"
tx pull -a --parallel %FORCE_FLAG% -r "syncthing-android-1.titletxt"
REM 
pause
