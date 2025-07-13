@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
::
attrib +h "%PROJECT_ROOT%\.git" >NUL:
attrib +h "%PROJECT_ROOT%\.github" >NUL:
attrib +h "%PROJECT_ROOT%\.gradle" >NUL:
attrib +h "%PROJECT_ROOT%\.idea" >NUL:
attrib +h "%PROJECT_ROOT%\.kotlin" >NUL:
attrib +h "%PROJECT_ROOT%\app\build" >NUL:
attrib +h "%PROJECT_ROOT%\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing\pkg" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing\src\github.com\syncthing\syncthing" >NUL:
::
goto :eof
