@echo off
cd ..\..\..
::
attrib +h .git
attrib +h .github
attrib +h .gradle
attrib +h .idea
attrib +h .kotlin
attrib +h app\build
attrib +h build
attrib +h syncthing\pkg
timeout 3
::
goto :eof
