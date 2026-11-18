#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
gcc -O2 -o "$OUT/harness" tools/shader_harness/harness.c -ldl
javac --release 17 -d "$OUT" tools/lightshow_tests/NewOpticsTest.java $(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx -name '*.java')
java -Dharness="$OUT/harness" -cp "$OUT" NewOpticsTest
