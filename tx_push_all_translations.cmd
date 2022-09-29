@echo off
title %~nx0
cls
cd /d "%~dps0"
REM
SET FORCE_FLAG=-f --no-interactive
REM 
echo Pushing all translations ...
tx push -s -t --parallel %FORCE_FLAG%
REM 
pause
