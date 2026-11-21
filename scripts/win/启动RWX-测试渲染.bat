@echo off
rem RWX render backend A/B: immediate instead of vao (for texture/quad artifacts)
setlocal
cd /d "%~dp0"
set "JAR=RWX-windows-x64-1.1.4.jar"
if not exist "%JAR%" ( echo jar missing: %JAR% & pause & exit /b 1 )
if not exist "runtime\bin\javaw.exe" ( echo runtime javaw missing & pause & exit /b 1 )
start "" "runtime\bin\javaw.exe" -Drwx.slickRenderer=immediate -jar "%JAR%"
exit /b 0
