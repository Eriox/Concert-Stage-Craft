#!/bin/sh
# 1.1.1 audio fix: measurements before / after (Bench) and checks (AudioFixTest). Needs JDK 17+.
# Usage: sh tools/audio_fix/run.sh [before-sources-dir]   (before-sources-dir: a copy of common/src/main/java of the code before the fix)
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main/java"; OUT=$(mktemp -d)
core() { find "$1/com/concertstage/lib/jlayer" "$1/com/concertstage/audio/core" -name "*.java"; echo "$1/com/concertstage/audio/SpeakerModels.java"; echo "$1/com/concertstage/audio/AudioGroups.java"; }
javac --release 17 -nowarn -d "$OUT/after" $(core "$SRC") "$HERE/Rig.java" "$HERE/Bench.java" "$HERE/after/M.java" "$HERE/AudioFixTest.java" "$ROOT/tools/audio_tests/LevelChainTest.java"
java -cp "$OUT/after" AudioFixTest
if [ -n "$1" ]; then
  javac --release 17 -nowarn -d "$OUT/before" $(core "$1") "$HERE/Rig.java" "$HERE/Bench.java" "$HERE/before/M.java" "$ROOT/tools/audio_tests/LevelChainTest.java"
  java -cp "$OUT/before" Bench all
fi
java -cp "$OUT/after" Bench all
