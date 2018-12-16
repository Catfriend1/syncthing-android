@echo off
cls
cd /d "%~dps0"
REM 
gradlew buildNative
REM 
pause
