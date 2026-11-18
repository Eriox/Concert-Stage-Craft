#!/bin/sh
# Tests hors jeu du système lumière 1.2.0 (moteur, commandes, effets, analyse musicale, AutoShow, mesures de temps).
# Besoin : JDK 17+. Usage : sh tools/lightshow_tests/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main/java"; OUT=$(mktemp -d)
javac --release 17 -d "$OUT" $(find "$SRC/com/concertstage/light/core" "$SRC/com/concertstage/light/analysis" "$SRC/com/concertstage/light/dmx" -name "*.java") "$HERE/LightshowTest.java"
java -cp "$OUT" LightshowTest
