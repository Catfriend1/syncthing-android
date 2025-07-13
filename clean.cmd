@echo off
::
RD /S /Q ".gradle" 2>NUL:
RD /S /Q ".kotlin" 2>NUL:
RD /S /Q "app\build" 2>NUL:
RD /S /Q "app\src\main\jniLibs" 2>NUL:
RD /S /Q "build" 2>NUL:
RD /S /Q "syncthing\pkg\mod" 2>NUL:
::
goto :eof
