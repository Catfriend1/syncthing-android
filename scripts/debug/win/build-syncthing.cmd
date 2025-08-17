@echo off
setlocal enabledelayedexpansion
::
:: Command line.
::  "F:\GitHub\syncthing-android\scripts\debug\win\build-syncthing.cmd"
::
SET BUILD_HOST=Catfriend1-syncthing-android
SET BUILD_USER=reproducible-build
SET SOURCE_DATE_EPOCH=1
SET SYNCTHING_VERSION=-version "v2.0.0-reproducibleBuildTest"
::
where go 2>NUL: || SET "PATH=%PATH%;F:\GitHub\syncthing-android-prereq\go_1.25.0\bin"
where go >NUL: || (echo "[ERROR] go missing on PATH." & goto :eof)
::
cd /d "F:\GitHub\syncthing" || (echo "[ERROR] cd FAILED." & goto :eof)
::
go mod download
:: go run build.go version
::
go run build.go -goos windows -goarch amd64 %SYNCTHING_VERSION% -no-upgrade build
::
pause
::
goto :eof
