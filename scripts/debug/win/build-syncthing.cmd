@echo off
setlocal enabledelayedexpansion
::
:: Command line.
::  "F:\GitHub\build-syncthing.cmd"
::
SET BUILD_HOST=Catfriend1-syncthing-android
SET BUILD_USER=reproducible-build
SET EXTRA_LDFLAGS=-buildid=
:: SET SYNCTHING_VERSION=-version "%SYNCTHING_VERSION%"
::
where go 2>NUL: || SET "PATH=%PATH%;F:\GitHub\syncthing-android-prereq\go_1.24.1\bin"
where go >NUL: || (echo "[ERROR] go missing on PATH." & goto :eof)
::
cd /d "F:\GitHub\syncthing" || (echo "[ERROR] cd FAILED." & goto :eof)
::
go mod download
:: go run build.go version
::
go run build.go -goos windows -goarch amd64 %SYNCTHING_VERSION% -no-upgrade build
::
goto :eof
