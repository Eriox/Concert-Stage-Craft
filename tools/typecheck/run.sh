#!/bin/sh
# Vérification de types du module commun contre les classes de Minecraft 1.20.1 (mojmap), sans Gradle ni réseau.
# Usage : sh tools/typecheck/run.sh <minecraft-merged-1.20.1.jar> <dossier contenant slf4j-api, guava, gson, fastutil>
# Le jar « merged » est celui du cache Loom (.gradle/loom-cache/minecraftMaven/.../minecraft-merged-*.jar).
# JOML et com.mojang.logging sont remplacés par de petits stubs (tools/typecheck/stubs) ; LWJGL, Netty, Authlib et les API
# Forge / Fabric ne sont pas fournis : les erreurs qui les concernent sont attendues et listées à part.
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; MC="$1"; L="$2"; OUT=$(mktemp -d)
mkdir -p "$OUT/stubs" "$OUT/classes"
javac -d "$OUT/stubs" -cp "$L/slf4j-api-1.7.36.jar" $(find "$HERE/stubs" -name "*.java")
CP="$MC:$OUT/stubs:$L/slf4j-api-1.7.36.jar:$L/guava-33.4.6-jre.jar:$L/gson-2.10.jar:$L/fastutil-8.5.2-min.jar"
javac -proc:none -XDshould-stop.ifError=FLOW -Xmaxerrs 3000 -nowarn -d "$OUT/classes" -cp "$CP" $(find "$ROOT/common/src/main/java" -name "*.java") > "$OUT/log.txt" 2>&1 || true
TOTAL=$(grep -c "error:" "$OUT/log.txt" || true)

echo "erreurs : $TOTAL — attendues : LWJGL (GLFW, BufferUtils), Netty (Unpooled), Authlib (GameProfile), interfaces injectées par Forge/Fabric, méthodes absentes des stubs JOML"
grep "error:" "$OUT/log.txt" | sed -E 's/:[0-9]+: error: /: /' | sort | uniq -c | sort -rn
