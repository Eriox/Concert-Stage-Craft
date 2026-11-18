#!/bin/sh
# Compares CurvedTrussGeometry.java with the triangle counts of the Blender files (tools/v13/export_out stats, written by
# export_vision_blender.py). Expected output: see expected.txt (identical).
HERE=$(cd "$(dirname "$0")" && pwd); T=$(mktemp -d)
sed 's/^package .*;//' "$HERE/../../../common/src/main/java/com/concertstage/block/ring/CurvedTrussGeometry.java" > "$T/CurvedTrussGeometry.java"
cp "$HERE/CheckTrussGeometry.java" "$T/"
javac -d "$T" "$T"/*.java && java -cp "$T" CheckTrussGeometry > "$T/out.txt" 2>/dev/null
if diff -q "$T/out.txt" "$HERE/expected.txt" >/dev/null; then echo "truss geometry: identical to the Blender variants (19 shapes x 4 LOD)"; else diff "$T/out.txt" "$HERE/expected.txt"; exit 1; fi
