#!/bin/sh
# Off-game tests of the optical profiles and of the common optical mask (GLSL compared with Java on Mesa when the harness builds).
# Usage: sh tools/lightshow_tests/run_optics.sh
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
gcc -O2 -o /tmp/harness tools/shader_harness/harness.c -ldl 2>/dev/null || echo "(no gcc / EGL: GLSL comparison skipped)"
python3 -I tools/shader_harness/cells.py . > tools/lightshow_tests/optics_cells.txt
javac -nowarn -d "$OUT" tools/lightshow_tests/OpticsTest.java $(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx -name "*.java")
java -cp "$OUT" OpticsTest
