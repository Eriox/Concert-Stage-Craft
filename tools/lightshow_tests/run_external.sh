#!/bin/sh
# Off-game tests of CSC External Show Control (pure Java, no Minecraft). Usage: sh tools/lightshow_tests/run_external.sh
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
CORE=$(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx -name "*.java")
javac -nowarn -d "$OUT" tools/lightshow_tests/ExternalTest.java tools/lightshow_tests/export/ExportTest.java tools/lightshow_tests/export/MaProfileTest.java common/src/main/java/com/concertstage/light/ExternalExport.java $CORE
java -cp "$OUT" ExternalTest
java -cp "$OUT:common/src/main/resources" com.concertstage.light.ExportTest
java -cp "$OUT:common/src/main/resources" com.concertstage.light.MaProfileTest
