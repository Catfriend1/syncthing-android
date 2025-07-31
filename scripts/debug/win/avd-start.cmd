@echo off
setlocal enabledelayedexpansion
::
:: Consts.
SET EMULATOR_NAME="Pixel_9_API_35_A15_AOSP"
::
where emulator.exe >NUL: 2>&1 || SET "PATH=%PATH%;%ANDROID_HOME%\emulator"
IF NOT DEFINED ANDROID_AVD_HOME SET "ANDROID_AVD_HOME=%ANDROID_USER_HOME%\AVD"
::
emulator -list-avds | findstr /i %EMULATOR_NAME%
start "" conhost --headless emulator -avd %EMULATOR_NAME%
::
goto :eof
