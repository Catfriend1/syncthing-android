@echo off
REM
REM Runtime Variables.
IF NOT DEFINED JAVA_HOME SET JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr
IF EXIST "%LocalAppData%\Android\Sdk" SET "ANDROID_SDK_ROOT=%LocalAppData%\Android\Sdk"
REM
where /q java
IF NOT "%ERRORLEVEL%" == "0" SET PATH=%PATH%;%JAVA_HOME%\bin
where /q java
IF NOT "%ERRORLEVEL%" == "0" echo [ERROR] java.exe not found on PATH env var. Download 'https://www.oracle.com/java/technologies/downloads/#java17' and run the installer & pause & goto :checkPrerequisites
REM
goto :eof
