#!/usr/bin/env bash
# 台架测量：默认重复 3 次报中位数。gid 在本场景不稳定，只当参考，不当语义证据。
# 用法: REPEATS=5 TICKS=1500 scripts/bench.sh [-Dbench.* ...]
set -uo pipefail
PACK="${RWX_PACK:-/d/Game/RWX-整合包}"
export JAVA_HOME="${JAVA_HOME:-D:/Game/tools/zulu25.38.109-ca-jdk25.0.5.0.3-win_x64}"
REPEATS="${REPEATS:-3}"
TICKS="${TICKS:-1500}"
MAP='--map=maps\\skirmish\\[z;p10]Crossing Large (10p).tmx'
EXTRA="${*:-}"
cd "$PACK" || exit 1
[ -f preferences.toml ] && cp preferences.toml /tmp/pref-bench-backup.toml

vals=()
for i in $(seq 1 "$REPEATS"); do
  out="/tmp/bench-$$-run$i.log"
  # shellcheck disable=SC2086
  RWX_PROF=1 "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 \
    $EXTRA -jar RWX-windows-x64-1.1.4.jar --render-backend=headless "$MAP" \
    --team-layout=2sides --ticks="$TICKS" --auto-start-battleroom > "$out" 2>&1
  line=$(grep -a "RWX_PROF tick=1200" "$out" | tail -1)
  t=$(echo "$line" | grep -o "triggers=[0-9.]*/" | tr -d 'triggers=/')
  g=$(echo "$line" | grep -o "gid=[0-9]*" | cut -d= -f2)
  echo "run$i triggers_avg=${t:-?}ms gid=${g:-?}"
  [ -n "${t:-}" ] && vals+=("$t")
done
if [ "${#vals[@]}" -gt 0 ]; then
  med=$(printf '%s\n' "${vals[@]}" | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
  echo "中位数 triggers_avg=${med}ms （样本: ${vals[*]}）"
fi
[ -f /tmp/pref-bench-backup.toml ] && cp /tmp/pref-bench-backup.toml preferences.toml
