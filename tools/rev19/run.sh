#!/bin/sh
# CSC 1.2.0 rev. 19 lighting checks (strobes, budgets, gobos in every mode, migration, tone curve, motors). JDK 17+. Usage: sh tools/rev19/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main/java"; OUT=$(mktemp -d)
javac --release 17 -nowarn -d "$OUT" $(find "$SRC/com/concertstage/light/core" "$SRC/com/concertstage/light/analysis" "$SRC/com/concertstage/light/dmx" -name "*.java") \
  "$SRC/com/concertstage/client/light/FrameBudget.java" "$HERE/LightingFixTest.java"
java -cp "$OUT" LightingFixTest
