#!/bin/sh
# CSC 1.2.0 rev. 25 checks (no Minecraft needed). Usage: sh tools/rev25/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
python3 -I "$HERE/check_ma_gobos.py"
python3 -I "$HERE/check_console_gobos.py"
sh "$HERE/../rev24/run.sh"
