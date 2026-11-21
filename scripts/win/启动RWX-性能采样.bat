@echo off
rem JFR sampling launcher: records 180s of CPU samples into logs\render-<time>.jfr
setlocal
cd /d "%~dp0"
set "JAR=RWX-windows-x64-1.1.4.jar"
if not exist "%JAR%" ( echo jar missing & pause & exit /b 1 )
if not exist "logs" mkdir "logs"
for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value ^| find "="') do set "DT=%%a"
set "OUT=logs\render-%DT:~0,8%-%DT:~8,6%.jfr"
echo Recording to %OUT%  (180 seconds)
start "" "runtime\bin\javaw.exe" -XX:StartFlightRecording=filename="%OUT%",settings=profile,duration=180s -jar "%JAR%"
exit /b 0
