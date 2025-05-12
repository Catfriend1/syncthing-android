@echo off
REM
:loopMe
adb shell su root rm /data/data/com.github.catfriend1.syncthingandroid.debug/files/config.xml
pause
goto :loopMe
REM
goto :eof
