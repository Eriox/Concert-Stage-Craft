#!/bin/sh
# CSC 1.2.0 rev. 18 checks: hanging hardware on every truss, far-view rules, OBJ index order. Usage: sh tools/rev18/run.sh
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd); OUT=$(mktemp -d)
javac -d "$OUT" "$ROOT/common/src/main/java/com/concertstage/client/light/MountGeometry.java" \
  "$ROOT/common/src/main/java/com/concertstage/client/light/FarView.java" "$ROOT/tools/rev18/MountTest.java" "$ROOT/tools/rev18/FarViewTest.java"
java -cp "$OUT" MountTest "$ROOT" "$OUT/mounts" | tail -4
java -cp "$OUT" FarViewTest
python3 -I "$ROOT/tools/rev18/check_obj_order.py" "$ROOT"
