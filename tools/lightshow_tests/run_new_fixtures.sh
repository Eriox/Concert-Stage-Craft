#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 17 -d "$OUT" tools/lightshow_tests/NewFixturesTest.java $(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx common/src/main/java/com/concertstage/light/analysis -name '*.java')
java -cp "$OUT" NewFixturesTest
