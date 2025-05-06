@echo off
setlocal enabledelayedexpansion
::
echo [INFO] Create TAG: %1
git tag %1 && (echo [INFO] Push TAG, trigger release & git push origin %1)
::
goto :eof
