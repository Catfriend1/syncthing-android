@echo off
cls
SET "SCRIPT_PATH=%~dp0"
cd /d "%SCRIPT_PATH%"
REM 
REM Script Consts.
REM 
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
REM 
REM 	SET DATA_ROOT=/data/user/0
SET DATA_ROOT=/data/data
REM
echo Enter filter:
SET /P FILTER=
REM 
:loopMe
cls
adb shell "su root cat %DATA_ROOT%/%PACKAGE_NAME%/files/config.xml" > "%SCRIPT_PATH%config.xml"
call :psConvertFileFromCRLFtoLF "%SCRIPT_PATH%config.xml"
IF EXIST "%SCRIPT_PATH%config.xml" TYPE "%SCRIPT_PATH%config.xml" | findstr /i /c:"%FILTER%"  | findstr /v /c:"<device id="""""
echo.
pause
echo.
echo ==========================================================
echo.
goto :loopMe


:psConvertFileFromCRLFtoLF
REM 
REM Syntax:
REM 	call :psConvertFileFromCRLFtoLF [FULLFN]
REM 
REM Variables.
SET TMP_PSCFFCL_FULLFN=%1
IF DEFINED TMP_PSCFFCL_FULLFN SET TMP_PSCFFCL_FULLFN=%TMP_PSCFFCL_FULLFN:"=%
REM 
IF NOT EXIST %TMP_PSCFFCL_FULLFN% call :logAdd "[ERROR] psConvertFileFromCRLFtoLF: TMP_PSCFFCL_FULLFN=[%TMP_PSCFFCL_FULLFN%] does not exist." & goto :eof
REM call :logAdd "[INFO] psConvertFileFromCRLFtoLF: Converting [%TMP_PSCFFCL_FULLFN%] ..."
powershell -NoLogo -NoProfile -ExecutionPolicy ByPass -Command "Set-Content -Path '%TMP_PSCFFCL_FULLFN%' -NoNewLine -Value (Get-Content '%TMP_PSCFFCL_FULLFN%' -Raw).Replace(\"`r`n\",\"`n\")" 2> NUL:
REM 
goto :eof
