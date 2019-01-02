@echo off
REM 
REM adb forward local_port to emulator_port
echo Running ADB to setup port forwarding on the emulated Android device ...
adb forward tcp:18384 tcp:8384
start https://127.0.0.1:18384
echo Done.
timeout 3
