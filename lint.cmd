@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET LINT_RESULTS_DEBUG="%SCRIPT_PATH%app\build\reports\lint-results-debug.html"
::
IF EXIST %LINT_RESULTS_DEBUG% start "lint-results" %LINT_RESULTS_DEBUG%
::
goto :eof
