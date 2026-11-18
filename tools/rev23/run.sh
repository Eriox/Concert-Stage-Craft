#!/bin/sh
# Rev. 23 off-game tests (pure Java). Usage: sh tools/rev23/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; OUT=$(mktemp -d)
javac -nowarn -d "$OUT" "$ROOT/common/src/main/java/com/concertstage/client/light/FrameBudget.java" "$HERE/BudgetGroupsTest.java"
java -cp "$OUT" BudgetGroupsTest
