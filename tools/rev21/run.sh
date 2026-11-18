#!/bin/sh
# CSC 1.2.0 rev. 21: light zones of the PULSESTRIKE M / FLASHCORE FX (DMX Full / Compact, strobes per zone, migration, AutoShow). JDK 17+.
# Usage: sh tools/rev21/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main/java"; OUT=$(mktemp -d)
javac --release 17 -nowarn -d "$OUT" $(find "$SRC/com/concertstage/light/core" "$SRC/com/concertstage/light/analysis" "$SRC/com/concertstage/light/dmx" -name "*.java") "$HERE/ZoneTest.java"
java -cp "$OUT" ZoneTest
