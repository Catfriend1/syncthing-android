@echo off
setlocal enabledelayedexpansion
cls
SET SCRIPT_PATH=%~dp0
cd /d "%SCRIPT_PATH%"
REM
REM Script Configuration.
REM 	SET LOGFILE="%TEMP%\%~n0.log"
SET SYNCTHING_EXE_NAME=syncthing-test.exe
SET CLEAR_CONFIG_XML=0
REM 
REM Check prerequisites.
where sed 2>&1 1> NUL: || goto :eof
where %SYNCTHING_EXE_NAME% 2>&1 1> NUL: || goto :eof
REM 
call :logAdd "[INFO] Ending previously started %SYNCTHING_EXE_NAME% instances ..."
taskkill /f /im "%SYNCTHING_EXE_NAME%" 2> NUL:
REM 
REM 									GUI		Data
call :startSyncthingInstance Anna	8801	8811
call :startSyncthingInstance Dad		8802	8812
call :startSyncthingInstance Felix	8803	8813
call :startSyncthingInstance Kevin	8804	8814
call :startSyncthingInstance Mum		8805	8815
REM 
call :logAdd "[INFO] %SYNCTHING_EXE_NAME% instances have been started." 
pause
REM 
taskkill /f /im "%SYNCTHING_EXE_NAME%" 2> NUL:
REM 
goto :eof


:startSyncthingInstance
REM 
REM Syntax:
REM 	call :startSyncthingInstance [INSTANCE_NAME] [GUI_TCP_PORT] [LISTEN_TCP_PORT]
REM 
REM Called By:
REM 	MAIN
REM 
REM Variables.
SET TMP_SSI_NAME=%1
IF NOT DEFINED TMP_SSI_NAME goto :eof
SET TMP_SSI_HOME_FOLDER="%SCRIPT_PATH%%TMP_SSI_NAME%"
SET TMP_SSI_CONFIG_XML="%SCRIPT_PATH%%TMP_SSI_NAME%\config.xml"
SET TMP_SSI_START_BATCH="%SCRIPT_PATH%%TMP_SSI_NAME%\start_%TMP_SSI_NAME%.cmd"
REM 
SET TMP_SSI_GUI_PORT=%2
IF NOT DEFINED TMP_SSI_GUI_PORT goto :eof
REM 
SET TMP_SSI_DATA_PORT=%3
IF NOT DEFINED TMP_SSI_DATA_PORT goto :eof
REM 
REM Create home folder.
IF "%CLEAR_CONFIG_XML%" == "1" RD /S /Q %TMP_SSI_HOME_FOLDER% 2> NUL:
MD %TMP_SSI_HOME_FOLDER% 2> NUL:
REM 
REM Detect a fresh installation.
IF NOT EXIST "%TMP_SSI_CONFIG_XML%" (
	call :generateKeysAndConfig %TMP_SSI_NAME%
	call :applyFirstTimeConfig %TMP_SSI_CONFIG_XML% %TMP_SSI_NAME% %TMP_SSI_GUI_PORT% %TMP_SSI_DATA_PORT%
)
REM 
REM Create instance start batch script.
(echo @echo off) > %TMP_SSI_START_BATCH%
REM -gui-address=tcp4://127.0.0.1:%TMP_SSI_GUI_PORT%
(echo "%SCRIPT_PATH%%SYNCTHING_EXE_NAME%" -home=%TMP_SSI_HOME_FOLDER% -no-console"") >> %TMP_SSI_START_BATCH%
REM 
call :logAdd "[INFO] Starting Syncthing Instance [%TMP_SSI_NAME%], gui=[%TMP_SSI_GUI_PORT%], data=[%TMP_SSI_DATA_PORT%] ..."
start "%TMP_SSI_NAME%" %TMP_SSI_START_BATCH%
REM 
goto :eof


:generateKeysAndConfig
REM 
REM Syntax:
REM 	call :generateKeysAndConfig [INSTANCE_NAME]
REM 
REM Called By:
REM 	:startSyncthingInstance
REM 
REM Global Variables.
REM 	[IN] SYNCTHING_EXE_NAME
REM 
REM Variables.
SET TMP_GKAC_INSTANCE_NAME=%1
IF NOT DEFINED TMP_GKAC_INSTANCE_NAME goto :eof
SET TMP_GKAC_INSTANCE_HOME=%SCRIPT_PATH%%TMP_GKAC_INSTANCE_NAME%
REM 
REM 	Generate a fresh deviceID, keys and config.
call :logAdd "[INFO] generateKeysAndConfig: Generating new keys and config for instance [%TMP_GKAC_INSTANCE_NAME%] ..."
REM 
"%SCRIPT_PATH%%SYNCTHING_EXE_NAME%" -logflags=0 -generate "%TMP_GKAC_INSTANCE_HOME%"
"%SCRIPT_PATH%%SYNCTHING_EXE_NAME%" -device-id -home "%TMP_GKAC_INSTANCE_HOME%" > "%TMP_GKAC_INSTANCE_HOME%\device_id.txt"
REM 
goto :eof


:applyFirstTimeConfig
REM 
REM Syntax:
REM 	call :applyFirstTimeConfig [CONFIG_XML_FULLFN] [INSTANCE_NAME] [GUI_TCP_PORT] [DATA_TCP_PORT]
REM 
REM Called By:
REM 	:startSyncthingInstance
REM 
REM Variables.
SET TMP_AFTC_CONFIG_XML=%1
SET TMP_AFTC_CONFIG_XML_TMP=%TMP_AFTC_CONFIG_XML:.xml=.xml.tmp%
IF NOT EXIST %TMP_AFTC_CONFIG_XML% call :logAdd "[WARN] applyFirstTimeConfig: Skipping non-existant config [%TMP_AFTC_CONFIG_XML%:"=]." & goto :eof
REM 
SET TMP_AFTC_INSTANCE_NAME=%2
IF NOT DEFINED TMP_AFTC_INSTANCE_NAME goto :eof
REM 
SET TMP_AFTC_GUI_PORT=%3
IF NOT DEFINED TMP_AFTC_GUI_PORT goto :eof
REM 
SET TMP_AFTC_DATA_PORT=%3
IF NOT DEFINED TMP_AFTC_DATA_PORT goto :eof
REM 
REM Adjust syncthing "config.xml".
call :logAdd "[INFO] applyFirstTimeConfig: Adjusting [%TMP_AFTC_CONFIG_XML:"=%] ..."
REM 
REM 	WebGUI Port, Data Port, Do not auto-upgrade, Do not start browser
sed.exe -e "s/<address>127\.0\.0\.1:8384<\/address>/<address>127.0.0.1:%TMP_AFTC_GUI_PORT%<\/address>/g" -e "s/<listenAddress>.*<\/listenAddress>/<listenAddress>tcp4:\/\/:%TMP_AFTC_DATA_PORT%<\/listenAddress>/g" -e "s/<autoUpgradeIntervalH>12<\/autoUpgradeIntervalH>/<autoUpgradeIntervalH>0<\/autoUpgradeIntervalH>/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM 	Global Discovery=0, Local discovery=1, Relays=0, NAT Traversal=0
sed.exe -e "s/<relaysEnabled>true<\/relaysEnabled>/<relaysEnabled>false<\/relaysEnabled>/g" -e "s/<natEnabled>true<\/natEnabled>/<natEnabled>false<\/natEnabled>/g" -e "s/<globalAnnounceEnabled>true<\/globalAnnounceEnabled>/<globalAnnounceEnabled>false<\/globalAnnounceEnabled>/g" -e "s/<localAnnounceEnabled>false<\/localAnnounceEnabled>/<localAnnounceEnabled>true<\/localAnnounceEnabled>/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM 	Crash Reporting
sed.exe -e "s/<crashReportingEnabled>.*<\/crashReportingEnabled>/<crashReportingEnabled>false<\/crashReportingEnabled>/g" -e "s/<crashReportingURL>.*<\/crashReportingURL>/<crashReportingURL>http:\/\/localhost\/crash.syncthing.net\/newcrash<\/crashReportingURL>/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM 	Usage Reporting
sed.exe -e "s/<urAccepted>.*<\/urAccepted>/<urAccepted>-1<\/urAccepted>/g" -e "s/<urSeen>.*<\/urSeen>/<urSeen>3<\/urSeen>/g" -e "s/<urURL>.*<\/urURL>/<urURL>http:\/\/localhost\/data.syncthing.net\/newdata<\/urURL>/g" -e "s/<releasesURL>.*<\/releasesURL>/<releasesURL>http:\/\/localhost\/upgrades.syncthing.net\/meta.json<\/releasesURL>/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM 	Remove Default Shared Folder
sed.exe -e "/<folder.*>/,/<\/folder>/d" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM 	Rename Local Device
sed.exe -e "s/\" name=\"%COMPUTERNAME%\" compression=\"/\" name=\"%TMP_AFTC_INSTANCE_NAME%\" compression=\"/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML% 1> NUL:
REM 
REM   	Default Folder Path
sed.exe -e "s/<defaultFolderPath>~<\/defaultFolderPath>/<defaultFolderPath>%SystemDrive%\\Server\\Sync<\/defaultFolderPath>/g" %TMP_AFTC_CONFIG_XML% > %TMP_AFTC_CONFIG_XML_TMP%
move /y %TMP_AFTC_CONFIG_XML_TMP% %TMP_AFTC_CONFIG_XML%
REM 
goto :eof


:logAdd
REM Syntax:
REM		logAdd [TEXT]
SET LOG_TEXT=%1
SET LOG_TEXT=%LOG_TEXT:"=%
SET LOG_DATETIMESTAMP=%DATE:~-4%-%DATE:~-7,-5%-%DATE:~-10,-8%_%time:~-11,2%:%time:~-8,2%:%time:~-5,2%
SET LOG_DATETIMESTAMP=%LOG_DATETIMESTAMP: =0%
echo %LOG_DATETIMESTAMP%: %LOG_TEXT%
REM echo %LOG_DATETIMESTAMP%: %LOG_TEXT% >> "%LOGFILE%"
goto :eof
