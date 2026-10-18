#!/usr/bin/env bash
# Generates small synthetic audio fixtures (a 2 s sine tone, no copyrighted content)
# and tags them with mutagen. Usage: make_fixtures.sh <python-with-mutagen> <out-dir>
set -euo pipefail
PY="$1"
mkdir -p "$2"; OUT="$(cd "$2" && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
cd "$OUT"

tone=(-f lavfi -i "sine=frequency=440:duration=2" -ac 2 -ar 44100)
ffmpeg -loglevel error -y "${tone[@]}" -c:a libmp3lame -b:a 128k -id3v2_version 0 -write_xing 0 raw.mp3
ffmpeg -loglevel error -y "${tone[@]}" -c:a flac raw.flac
ffmpeg -loglevel error -y "${tone[@]}" -c:a aac -b:a 128k raw.m4a
ffmpeg -loglevel error -y "${tone[@]}" -c:a vorbis -strict -2 raw.ogg
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=440:duration=2" -ac 2 -ar 48000 -c:a libopus raw.opus
ffmpeg -loglevel error -y -f lavfi -i "color=c=red:s=64x64" -frames:v 1 cover.png
ffmpeg -loglevel error -y -f lavfi -i "color=c=blue:s=64x64" -frames:v 1 cover2.png

cp raw.mp3 mp3_v23.mp3
cp raw.mp3 mp3_v24.mp3
cp raw.flac flac.flac
cp raw.m4a m4a.m4a
cp raw.ogg ogg.ogg
cp raw.opus opus.opus
rm raw.*

"$PY" -I "$HERE/tag_fixtures.py" "$OUT"

# Longer files for the read-speed run (4 minutes, realistic sizes, still synthetic).
long=(-f lavfi -i "sine=frequency=440:duration=240" -ac 2 -ar 44100)
ffmpeg -loglevel error -y "${long[@]}" -c:a libmp3lame -b:a 320k perf_long.mp3
ffmpeg -loglevel error -y "${long[@]}" -c:a flac perf_long.flac
