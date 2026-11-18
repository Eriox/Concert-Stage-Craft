#!/bin/sh
# Tests hors jeu du moteur audio (décodeurs MP3 / WAV, recherche à l'échantillon près, tampon partagé, filtres, modèle acoustique).
# Besoin : JDK 17+, ffmpeg (pour fabriquer les fichiers de test). Usage : sh tools/audio_tests/run.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT="$HERE/../.."; SRC="$ROOT/common/src/main"; OUT=$(mktemp -d); AT="$OUT/audio"; mkdir -p "$AT"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "sine=frequency=440:duration=20:sample_rate=44100" -f lavfi -i "sine=frequency=660:duration=20:sample_rate=44100" -filter_complex "[0][1]amerge=inputs=2" -ac 2 "$AT/t.wav"
ffmpeg -hide_banner -loglevel error -y -i "$AT/t.wav" -codec:a libmp3lame -b:a 192k "$AT/t.mp3"
ffmpeg -hide_banner -loglevel error -y -i "$AT/t.wav" -ac 1 -ar 48000 -c:a pcm_s24le "$AT/t24.wav"
ffmpeg -hide_banner -loglevel error -y -i "$AT/t.wav" -ac 1 -ar 22050 -codec:a libmp3lame -b:a 64k "$AT/tm.mp3"
ffmpeg -hide_banner -loglevel error -y -i "$AT/t.wav" -codec:a libmp3lame -q:a 9 "$AT/tv9.mp3"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "anoisesrc=d=10:c=pink:r=44100:a=0.3" -ac 2 -c:a pcm_s16le "$AT/n.wav"
ffmpeg -hide_banner -loglevel error -y -i "$AT/n.wav" -c:a libmp3lame -b:a 256k "$AT/n.mp3"
javac --release 17 -d "$OUT/classes" $(find "$SRC/java/com/concertstage/lib/jlayer" "$SRC/java/com/concertstage/audio/core" -name "*.java") "$HERE/CoreTest.java" "$HERE/SeekTest.java" "$HERE/MixerTest.java" "$HERE/LimiterTest.java" "$HERE/RepeatModeTest.java" "$HERE/PlaylistPcmTest.java" "$HERE/SystemProcessorTest.java" "$HERE/DistanceCurveTest.java" "$HERE/DelayPlannerTest.java" "$HERE/GaplessTest.java" "$HERE/ReverbTest.java" "$HERE/EqTuningTest.java" "$HERE/MonitorMixerTest.java" "$HERE/ConcertAcousticsTest.java" "$HERE/ResamplerTest.java" "$HERE/CrossoverTest.java" "$HERE/HeadroomTest.java" "$HERE/LevelChainTest.java" "$HERE/MixerCostTest.java" "$SRC/java/com/concertstage/audio/SpeakerModels.java" "$SRC/java/com/concertstage/audio/AudioGroups.java"
cp -r "$SRC/resources/com" "$OUT/classes/"
java -cp "$OUT/classes" CoreTest "$AT"
for f in t tm tv9; do java -cp "$OUT/classes" SeekTest "$AT/$f.mp3"; done

java -cp "$OUT/classes" MixerTest
java -cp "$OUT/classes" LimiterTest

java -cp "$OUT/classes" RepeatModeTest

java -cp "$OUT/classes" PlaylistPcmTest "$AT"

java -cp "$OUT/classes" SystemProcessorTest

java -cp "$OUT/classes" DistanceCurveTest

java -cp "$OUT/classes" DelayPlannerTest

java -cp "$OUT/classes" GaplessTest "$AT"
java -cp "$OUT/classes" ReverbTest
java -cp "$OUT/classes" EqTuningTest

java -cp "$OUT/classes" MonitorMixerTest
java -cp "$OUT/classes" ConcertAcousticsTest

# 1.1.1
java -cp "$OUT/classes" ResamplerTest
java -cp "$OUT/classes" CrossoverTest
java -cp "$OUT/classes" HeadroomTest check
java -cp "$OUT/classes" MixerCostTest
java -cp "$OUT/classes" LevelChainTest systems 1.0 0.22
