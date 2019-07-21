@echo off
REM 
REM Enable lint during build
REM 
SET APP_BUILD_GRADLE=%~dps0app\build.gradle
REM 
cls
echo [INFO] Enabling lint during build ...
sed -e "s/\s\/\/task.dependsOn lint/ task.dependsOn lint/gI" -e "s/\s\/\/task.mustRunAfter lint/ task.mustRunAfter lint/gI" "%APP_BUILD_GRADLE%" > "%APP_BUILD_GRADLE%.tmp"
move /y "%APP_BUILD_GRADLE%.tmp" "%APP_BUILD_GRADLE%"
echo [INFO] Done.
timeout 3
goto :eof
