@echo off
cls
REM
SET PATH=%PATH%;"%ProgramFiles%\Git\bin"
SET DESIRED_SUBMODULE_VERSION=v1.2.0
REM 
cd /d "%~dps0\syncthing\src\github.com\syncthing\syncthing"
REM 
git fetch --all
git checkout %DESIRED_SUBMODULE_VERSION%
REM 
pause
