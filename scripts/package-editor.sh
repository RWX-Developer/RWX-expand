#!/usr/bin/env bash
# 打包编辑器并部署到整合包：产物放 <pack>/editor/，同时放一个双击启动的 bat。
# 用法: scripts/package-editor.sh [整合包目录]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="${1:-${RWX_PACK:-/d/Game/RWX-整合包}}"
JAR_NAME="RWX-Editor-windows-x64-0.1.0.jar"

cd "$ROOT" || exit 1
bash scripts/build.sh :editor:packageUberJarForCurrentOS || exit 1
SRC="$ROOT/editor/build/compose/jars/$JAR_NAME"
[ -f "$SRC" ] || { echo "构建产物没找到: $SRC"; exit 1; }

mkdir -p "$PACK/editor"
cp "$SRC" "$PACK/editor/$JAR_NAME" || exit 1
cp "$ROOT/scripts/win/启动编辑器.bat" "$PACK/启动编辑器.bat" || exit 1
printf 'RWX Editor %s\r\n' "$(cd "$ROOT" && git rev-parse --short HEAD)" > "$PACK/editor/VERSION.txt"
echo "部署完成:"
stat -c '  %n  %s bytes' "$PACK/editor/$JAR_NAME" "$PACK/启动编辑器.bat"
echo "双击 $PACK/启动编辑器.bat 即可启动（工作区默认 mods\\units）"
