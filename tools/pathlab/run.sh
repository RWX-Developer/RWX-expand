#!/usr/bin/env bash
set -e
J=/d/Game/tools/zulu25.38.109-ca-jdk25.0.5.0.3-win_x64
export PATH="$J/bin:$PATH"
cd "$(dirname "$0")"
rm -rf out/classes && mkdir -p out/classes
javac -nowarn -d out/classes $(find src -name "*.java")
java -cp out/classes pathlab.PathLab data/cost-LAND.csv "$@"
