#!/bin/sh
# CSC 1.2.0 rev. 24 checks (no Minecraft needed). JDK 17+. Usage: sh tools/rev24/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main/java"; OUT=$(mktemp -d)
javac -nowarn -d "$OUT" "$HERE/MixTest.java"
java -cp "$OUT" MixTest
javac --release 17 -nowarn -d "$OUT" $(find "$SRC/com/concertstage/light/core" "$SRC/com/concertstage/light/analysis" "$SRC/com/concertstage/light/dmx" -name "*.java") "$ROOT/tools/rev21/ZoneTest.java" "$HERE/Rev24Test.java"
java -cp "$OUT" Rev24Test
