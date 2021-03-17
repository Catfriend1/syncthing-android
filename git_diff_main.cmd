@echo off
setlocal enabledelayedexpansion
mode con lines=70 cols=150
powershell.exe -command "& {$pshost = Get-Host;$pswindow = $pshost.UI.RawUI;$newsize = $pswindow.BufferSize;$newsize.height = 2000;$pswindow.buffersize = $newsize;}
cls
SET SCRIPT_PATH=%~dps0
cd /d "%SCRIPT_PATH%"
REM 
REM Script Consts.
echo [INFO] Checking prerequisites ...
SET GIT_BIN=
FOR /F "tokens=*" %%A IN ('where git 2^> NUL:') DO SET GIT_BIN="%%A"
IF NOT EXIST %GIT_BIN% echo [ERROR] GIT_BIN not found. & goto :pauseExit
REM 
call :runGit --no-pager diff main
REM 
pause
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
