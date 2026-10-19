"""Makes the replaygain* fixtures: copies of the untagged tones with ReplayGain tags, written by mutagen.

TagLibTagReaderTest expects the same values from every file: track -6.50 dB, peak 0.912345; album -7.25 dB,
peak 0.987654. Each format stores them the way common taggers do:
- MP3 (ID3v2.4): TXXX frames with upper-case descriptions;
- FLAC: Vorbis comments;
- M4A: freeform ----:com.apple.iTunes: atoms with lower-case names, as foobar2000 writes them;
- Opus: R128_TRACK_GAIN and R128_ALBUM_GAIN (Q7.8 at -23 LUFS: -2944 and -3136 are -6.50 and -7.25 dB at the
  ReplayGain level) and REPLAYGAIN_*_PEAK, which Opus has no R128 field for.
Run by make_fixtures.sh after extra_fixtures.py, or alone: python -I replaygain_fixtures.py <resources/tags>.
"""
import shutil
import sys
from pathlib import Path

from mutagen.flac import FLAC
from mutagen.id3 import ID3, TXXX
from mutagen.mp4 import MP4, MP4FreeForm
from mutagen.oggopus import OggOpus

OUT = Path(sys.argv[1])
VALUES = {
    "REPLAYGAIN_TRACK_GAIN": "-6.50 dB",
    "REPLAYGAIN_TRACK_PEAK": "0.912345",
    "REPLAYGAIN_ALBUM_GAIN": "-7.25 dB",
    "REPLAYGAIN_ALBUM_PEAK": "0.987654",
}
PEAKS = {key: value for key, value in VALUES.items() if key.endswith("_PEAK")}


def copy(source, target):
    shutil.copyfile(OUT / source, OUT / target)
    return OUT / target


def replaygain_mp3():
    path = copy("untagged.mp3", "replaygain_v24.mp3")
    tags = ID3()
    for key, value in VALUES.items():
        tags.add(TXXX(encoding=3, desc=key, text=value))
    tags.save(path, v2_version=4, v1=0)


def replaygain_flac():
    f = FLAC(copy("untagged.flac", "replaygain.flac"))
    for key, value in VALUES.items():
        f[key] = value
    f.save()


def replaygain_m4a():
    path = copy("m4a.m4a", "replaygain.m4a")
    f = MP4(path)
    f.clear()
    for key, value in VALUES.items():
        f["----:com.apple.iTunes:" + key.lower()] = [MP4FreeForm(value.encode("utf-8"))]
    f.save()


def replaygain_opus():
    f = OggOpus(copy("opus.opus", "replaygain.opus"))
    f.clear()
    f["R128_TRACK_GAIN"] = "-2944"
    f["R128_ALBUM_GAIN"] = "-3136"
    for key, value in PEAKS.items():
        f[key] = value
    f.save()


replaygain_mp3()
replaygain_flac()
replaygain_m4a()
replaygain_opus()
