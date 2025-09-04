@echo off
setlocal enabledelayedexpansion
::
:: Example
:: 	call build && echo OK
::
IF /I "%1" == "clean" SET PARAM_NO_BUILD_CACHE=--no-build-cache
::
call gradlew --no-daemon %PARAM_NO_BUILD_CACHE% --warning-mode all %* assembledebug
SET GRADLEW_ERRORLEVEL=%ERRORLEVEL%
::
call scripts\debug\win\hide-folders-from-notepad++.cmd
::
endlocal & (
	where update.cmd >NUL: 2>&1 || SET "PATH=%PATH%;%~dp0scripts\debug\win"
	::
	exit /b %GRADLEW_ERRORLEVEL%
)
