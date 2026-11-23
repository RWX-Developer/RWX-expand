@echo off
rem RWX 命令轮换演示：开一局，机器人每隔若干 tick 换一种命令（MOVE / ATTACK_MOVE / PATROL / STOP）
rem 想只看某一种，把 OPS 改成 MOVE、ATTACK_MOVE、PATROL 或 STOP。
setlocal
cd /d "%~dp0"
if "%TARGET%"=="" set TARGET=40
if "%OPS%"=="" set OPS=CYCLE
if "%EVERY%"=="" set EVERY=120
if not exist "logs" mkdir "logs"
echo 命令轮换: op=%OPS% target=%TARGET% every=%EVERY% tick
start "" "runtime\bin\javaw.exe" --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 -Dbench.pace=false -Dbench.target=%TARGET% -Dbench.batch=20 -Dbench.interval=4 -Dbench.spread=8 -Dbench.agent=1 -Dbench.agentMax=12 -Dbench.agentMoveTick=%EVERY% -Dbench.agentOp=%OPS% -jar RWX-windows-x64-1.1.4.jar
exit /b 0
