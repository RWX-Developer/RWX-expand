#!/usr/bin/env bash
# 安全部署：查进程 → 备份 → 原子替换 → 校验。不做记账，记账靠人。
# 用法: scripts/deploy-pack.sh [整合包目录]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="${1:-${RWX_PACK:-/d/Game/RWX-整合包}}"
JAR="RWX-windows-x64-1.1.4.jar"
SRC="$ROOT/desktop/build/compose/jars/$JAR"

[ -f "$SRC" ] || { echo "找不到构建产物: $SRC（先跑 scripts/build.sh）"; exit 1; }
tasklist //FI "IMAGENAME eq RWX.exe" //NH 2>/dev/null | grep -qi rwx && { echo "RWX.exe 在运行，jar 会被锁；先关掉"; exit 1; }
# 只拦真正的游戏/台架 JVM（命令行里带 RWX-windows 的那个 jar）；Gradle 守护进程不算。
if powershell -NoProfile -c "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='RWX.exe'\" | Where-Object { \$_.CommandLine -like '*RWX-windows*' -or \$_.Name -eq 'RWX.exe' } | Select-Object -ExpandProperty ProcessId" 2>/dev/null | grep -qiE '[0-9]'; then
  echo "有游戏/台架 JVM 在跑（RWX-windows）；先停掉再部署"; exit 1;
fi

cd "$PACK" || exit 1
mkdir -p logs-archive
old_sz=$(stat -c%s "$JAR" 2>/dev/null || echo 0)
if [ "$old_sz" -gt 0 ]; then
  cp "$JAR" "logs-archive/$JAR.prev-$(date +%Y%m%d-%H%M)-$old_sz" || exit 1
fi
tmp="$JAR.tmp.$$"
cp "$SRC" "$tmp" && mv -f "$tmp" "$JAR" || { rm -f "$tmp"; exit 1; }
echo "部署完成: $(stat -c%s "$JAR") bytes  md5=$(md5sum "$JAR" | cut -c1-32)"
echo "旧包备份: $old_sz bytes → logs-archive/"
echo "记一笔: CHANGED-FILES.txt 追加本轮改动，并写下 git HEAD=$(cd "$ROOT" && git rev-parse --short HEAD)"
