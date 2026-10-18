#!/usr/bin/env bash
# Estimates the DEX size JAudioTagger adds after R8 (release-like shrinking) for a minimal usage stub.
# Usage: measure_jaudiotagger_r8.sh <android-sdk> <jaudiotagger.jar> <out-dir>
set -euo pipefail
SDK="$1"; JAR="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
ANDROID_JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
mkdir -p "$OUT/classes" "$OUT/r8" "$OUT/d8"
javac --release 11 -cp "$JAR" -d "$OUT/classes" "$HERE/JatUsage.java"
(cd "$OUT/classes" && jar cf ../usage.jar .)
echo "-keep class JatUsage { public static void main(java.lang.String[]); }
-dontwarn **
-dontobfuscate" > "$OUT/rules.pro"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 --lib "$ANDROID_JAR" \
  --pg-conf "$OUT/rules.pro" --output "$OUT/r8" "$OUT/usage.jar" "$JAR"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
  --output "$OUT/d8" "$OUT/usage.jar" "$JAR"
echo "jar:            $(stat -f%z "$JAR") bytes"
echo "dex, no shrink: $(stat -f%z "$OUT/d8/classes.dex") bytes"
echo "dex, R8:        $(stat -f%z "$OUT/r8/classes.dex") bytes"
