#!/bin/sh
# Off-game tests of the tungsten lamp of the conventionals and the blinders (1.2.2): the dimmer law, the filament's inertia and the
# floor pool the surface composite shows. Pure Java, no Minecraft needed.
# Usage: sh tools/lightshow_tests/run_tungsten.sh
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
javac -nowarn -d "$OUT" tools/lightshow_tests/TungstenTest.java \
    $(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx -name "*.java")
java -cp "$OUT" TungstenTest
