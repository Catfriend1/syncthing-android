@echo off
REM 
where git 2> NUL: || call :addGitToPath
SET PATH=C:\Program Files\Android\Android Studio\jre\bin;"%GIT_INSTALL_DIR%\bin";"%GIT_INSTALL_DIR%\cmd";%PATH%
REM 
REM End of Script.
REM 
goto :eof


:addGitToPath
REM 
IF DEFINED GIT_INSTALL_DIR goto :eof
IF NOT EXIST "%ProgramFiles%\Git\cmd\git.exe" goto :eof
REM 
SET GIT_INSTALL_DIR=%ProgramFiles%\Git
echo [INFO] Set GIT_INSTALL_DIR to [%GIT_INSTALL_DIR%] ...
REM 
goto :eof
