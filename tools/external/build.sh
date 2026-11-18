#!/bin/sh
# Rebuilds the external files (GDTF, glTF, OBJ, 3DS, QLC+) and the DMX charts from the game data. Usage: sh tools/external/build.sh
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
javac -nowarn -d "$OUT" tools/external/DumpModes.java tools/external/DumpSecondaryGobos.java $(find common/src/main/java/com/concertstage/light/core common/src/main/java/com/concertstage/light/dmx -name "*.java")
java -cp "$OUT" DumpModes > tools/external/modes.json
java -Djava.awt.headless=true -cp "$OUT" DumpSecondaryGobos common/src/main/resources/assets/concertstage/textures/lightshow
python3 -I tools/external/build_external_assets.py .
python3 -I tools/external/ma2_profiles.py .
python3 -I tools/external/pixel_base_gdtf_qlc.py common/src/main/resources/assets/concertstage/external
python3 -I tools/external/dmx_charts.py . > Documentation/External_Control/DMX_CHARTS.md
python3 -I tools/external/external_lang.py

python3 -I tools/lightshow/build_fixture_resources.py .
