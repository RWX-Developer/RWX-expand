#!/usr/bin/env bash
# 固定构建环境：不用手动 export JAVA_HOME。默认构建客户端 jar + 台架模组。
# 用法: scripts/build.sh [gradle 任务...]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-D:/Game/tools/zulu25.38.109-ca-jdk25.0.5.0.3-win_x64}"
GRADLE="${GRADLE:-D:/Game/tools/gradle-9.8.0/bin/gradle.bat}"
TASKS="${*:-:desktop:packageUberJarForCurrentOS :bench-mod:build}"
LOG="${BUILD_LOG:-/tmp/rwx-build.log}"

[ -x "$JAVA_HOME/bin/java" ] || { echo "JAVA_HOME 不可用: $JAVA_HOME"; exit 1; }
cd "$ROOT" || exit 1
start=$(date +%s)
# shellcheck disable=SC2086
"$GRADLE" --daemon $TASKS > "$LOG" 2>&1
rc=$?
elapsed=$(( $(date +%s) - start ))
grep -E "^e: |错误:|error:|FAILURE|BUILD |written to" "$LOG" | head -20
echo "耗时 ${elapsed}s  exit=$rc  日志=$LOG"
exit $rc
