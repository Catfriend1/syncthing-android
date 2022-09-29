@echo off
title %~nx0
cls
cd /d "%~dps0"
REM 
echo Pushing all translations ...
tx push -s -t --parallel
REM 
pause
