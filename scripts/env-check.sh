#!/usr/bin/env bash
# 环境体检：磁盘/内存/代理/进程/git/构建缓存/整合包备份。不做任何修改。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="${RWX_PACK:-/d/Game/RWX-整合包}"

echo "== 磁盘"
df -h /d /c 2>/dev/null | sed -n '1,3p'

echo "== 内存 (GB)"
powershell -NoProfile -c "[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB,1)" 2>/dev/null || echo unknown

echo "== gh 代理"
curl -s -m 3 -o /dev/null -w "127.0.0.1:18099 -> %{http_code}\n" http://127.0.0.1:18099 \
  || echo "代理不可用：推送前先起 D:/Game/tools/github-pin-proxy.js"

echo "== 残留进程"
tasklist //FI "IMAGENAME eq RWX.exe" //NH 2>/dev/null | grep -qi rwx && echo "有 RWX.exe 在跑" || echo "无 RWX.exe"
tasklist //FI "IMAGENAME eq java.exe" //NH 2>/dev/null | grep -qi java && echo "有 java.exe 在跑" || echo "无 java.exe"

echo "== 源码树"
cd "$ROOT" || exit 1
echo "dirty files: $(git status --short | wc -l)"
git log --oneline -1

echo "== 构建缓存"
du -sh "$ROOT"/core/build "$ROOT"/ui/build "$ROOT"/desktop/build "$ROOT"/android/build 2>/dev/null
du -sh ~/.gradle 2>/dev/null

echo "== 整合包"
[ -f "$PACK/RWX-windows-x64-1.1.4.jar" ] && stat -c 'jar = %s bytes' "$PACK/RWX-windows-x64-1.1.4.jar"
echo "jar 备份份数: $(ls "$PACK"/logs-archive/*.jar.prev* 2>/dev/null | wc -l)"
du -sh "$PACK/logs-archive" 2>/dev/null
