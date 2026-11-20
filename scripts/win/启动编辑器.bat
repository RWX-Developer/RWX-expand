@echo off
setlocal
cd /d "%~dp0"
set "JAR=editor\RWX-Editor-windows-x64-0.1.0.jar"
set "WORKSPACE=%~dp0mods\units"
if not exist "%JAR%" (
  echo [RWX Editor] jar not found: %JAR%
  echo Build it first: scripts\package-editor.sh
  pause
  exit /b 1
)
set "JAVA="
if exist "%~dp0runtime\bin\javaw.exe" set "JAVA=%~dp0runtime\bin\javaw.exe"
if not defined JAVA if exist "%~dp0runtime\bin\java.exe" set "JAVA=%~dp0runtime\bin\java.exe"
if not defined JAVA if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA (
  echo [RWX Editor] no java found: looked in runtime\bin and JAVA_HOME
  pause
  exit /b 1
)
if not exist "%APPDATA%\RWXEditor" mkdir "%APPDATA%\RWXEditor" >nul 2>&1
start "" "%JAVA%" --enable-native-access=ALL-UNNAMED -jar "%JAR%" "%WORKSPACE%"
exit /b 0
