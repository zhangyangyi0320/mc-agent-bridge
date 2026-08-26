#!/usr/bin/env bash
set -e
API='libs/*'
OUT=build
mkdir -p "$OUT"
javac --release 21 -cp "$API" -d "$OUT" src/main/java/mcagent/*.java
cp src/main/resources/plugin.yml "$OUT"/plugin.yml
cp src/main/resources/config.yml "$OUT"/config.yml
jar --create --file McAgentBridge.jar -C "$OUT" .
echo "Built McAgentBridge.jar"
