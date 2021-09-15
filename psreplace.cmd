@echo off
REM
REM Version 1.0
REM
SET TMP_PSR_SEARCH=%1
IF DEFINED TMP_PSR_SEARCH SET "TMP_PSR_SEARCH=%TMP_PSR_SEARCH:"=%"
IF DEFINED TMP_PSR_SEARCH SET "TMP_PSR_SEARCH=%TMP_PSR_SEARCH:`=""%"
REM 
SET TMP_PSR_REPLACE=%2
IF DEFINED TMP_PSR_REPLACE SET "TMP_PSR_REPLACE=%TMP_PSR_REPLACE:"=%"
IF DEFINED TMP_PSR_REPLACE SET "TMP_PSR_REPLACE=%TMP_PSR_REPLACE:`=\"%"
REM 
SET TMP_PSR_TARGET_FULLFN=%3
IF NOT DEFINED TMP_PSR_TARGET_FULLFN powershell -NoLogo -NoProfile -ExecutionPolicy ByPass -Command "Write-Host -NoNewLine ((($Input | Out-String) -Replace '%TMP_PSR_SEARCH%', '%TMP_PSR_REPLACE%') -Replace '§n', \"`n\" -Replace '§r', \"`r\")" 2> NUL: & goto :eof
REM
powershell -NoLogo -NoProfile -ExecutionPolicy ByPass -Command "Set-Content -Path '%3' -NoNewLine -Value ((($Input | Out-String) -Replace '%TMP_PSR_SEARCH%', '%TMP_PSR_REPLACE%') -Replace '§n', \"`n\" -Replace '§r', \"`r\")"
REM 
goto :eof
