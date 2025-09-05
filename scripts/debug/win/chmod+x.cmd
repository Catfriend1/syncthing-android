@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
::
git update-index --chmod=+x "%PROJECT_ROOT%\gradlew"
::
git commit -m "chmod +x"
::
goto :eof
