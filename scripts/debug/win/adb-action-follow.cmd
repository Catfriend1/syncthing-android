@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
::
adb shell am broadcast -a %PACKAGE_NAME%.action.FOLLOW -p %PACKAGE_NAME%
::
goto :eof
