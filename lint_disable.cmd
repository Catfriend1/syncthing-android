@echo off
REM 
REM Disable lint during build
REM 
SET APP_BUILD_GRADLE=%~dps0app\build.gradle
REM 
cls
echo [INFO] Disabling lint during build ...
sed -e "s/\stask.dependsOn lint/ \/\/task.dependsOn lint/gI" -e "s/\stask.mustRunAfter lint/ \/\/task.mustRunAfter lint/gI" "%APP_BUILD_GRADLE%" > "%APP_BUILD_GRADLE%.tmp"
move /y "%APP_BUILD_GRADLE%.tmp" "%APP_BUILD_GRADLE%"
echo [INFO] Done.
timeout 3
goto :eof
