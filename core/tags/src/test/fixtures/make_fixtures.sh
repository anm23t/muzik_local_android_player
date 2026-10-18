#!/usr/bin/env bash
# Generates the tag test fixtures in core/tags/src/test/resources/tags/: 1-second sine tones (no copyrighted
# content) tagged by mutagen, a library independent of TagLib. The output is committed; rerun only to change it.
#
# Usage: make_fixtures.sh <python-with-mutagen>
# Needs ffmpeg with libmp3lame, libopus and the native aac and vorbis encoders. Tested with mutagen 1.47.0.
set -euo pipefail
PY="$1"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../resources/tags"
mkdir -p "$OUT"
cd "$OUT"

ff() { ffmpeg -loglevel error -y "$@"; }
tone=(-f lavfi -i "sine=frequency=440:duration=1" -ac 1 -fflags +bitexact -flags:a +bitexact -map_metadata -1)

ff "${tone[@]}" -ar 22050 -c:a libmp3lame -b:a 32k -id3v2_version 0 -write_id3v1 0 -write_xing 0 mp3_v23.mp3
cp mp3_v23.mp3 mp3_v24.mp3
cp mp3_v23.mp3 untagged.mp3
ff "${tone[@]}" -ar 8000 -c:a flac flac.flac
cp flac.flac untagged.flac
ff "${tone[@]}" -ar 22050 -c:a aac -b:a 32k m4a.m4a
# The native vorbis encoder only takes stereo.
ff "${tone[@]}" -ar 22050 -ac 2 -c:a vorbis -strict -2 ogg.ogg
ff -f lavfi -i "sine=frequency=440:duration=1" -ac 1 -ar 48000 -fflags +bitexact -flags:a +bitexact \
  -map_metadata -1 -c:a libopus -b:a 16k opus.opus

ff -f lavfi -i "color=c=red:s=8x8" -frames:v 1 -fflags +bitexact cover_front.png
ff -f lavfi -i "color=c=blue:s=8x8" -frames:v 1 -fflags +bitexact cover_other.png

"$PY" -I "$HERE/tag_fixtures.py" "$OUT"
"$PY" -I "$HERE/extra_fixtures.py" "$OUT"
ls -l "$OUT"
