@echo off
setlocal enabledelayedexpansion
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM 
REM Script Consts.
SET GIT_BIN="git.exe"
REM 
echo [INFO] Checking prerequisites ...
SET GIT_BIN=
FOR /F "tokens=*" %%A IN ('where git 2^> NUL:') DO SET GIT_BIN="%%A"
IF NOT EXIST %GIT_BIN% echo [ERROR] GIT_BIN not found. & goto :pauseExit
REM 
REM Script parameters.
SET BRANCH_TO_MERGE=%1
IF NOT DEFINED BRANCH_TO_MERGE echo [ERROR] Parameter #1 BRANCH_TO_MERGE is missing. & goto :pauseExit
REM 
call :runGit merge --squash --no-commit "%BRANCH_TO_MERGE%"
REM call :runGit commit -m "Merged branch \"%BRANCH_TO_MERGE%\""
REM 
goto :eof


REM ====================
REM FUNCTION BLOCK START
REM ====================
:runGit
echo [INFO] git %*
%GIT_BIN% %*
SET RESULT=%ERRORLEVEL%
IF NOT "%RESULT%" == "0" echo [ERROR] git FAILED with error code #%RESULT%. & goto :pauseExit
goto :eof


:pauseExit
pause
goto :eof
REM ==================
REM FUNCTION BLOCK END
REM ==================
