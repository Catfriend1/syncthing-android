@echo off
setlocal enabledelayedexpansion
::
call gradlew %1 assembledebug --warning-mode all
::
call scripts\debug\win\hide-folders-from-notepad++.cmd
::
endlocal
where update.cmd >NUL: 2>&1 || SET "PATH=%PATH%;%~dp0scripts\debug\win"
::
goto :eof
