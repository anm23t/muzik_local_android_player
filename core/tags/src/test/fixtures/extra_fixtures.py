"""Makes the extra_* fixtures: copies of the tagged fixtures with fields the app does not know, written by mutagen.

A tag write must keep all of them (ARCHITECTURE.md, section 5, step 4); SafeTagWriterTest checks that.
- MP3 (ID3v2.3 and v2.4): TXXX:MUZZZLO_TEST, a PRIV frame, TXXX:REPLAYGAIN_TRACK_GAIN; the v2.3 file also ends
  with an APEv2 tag and an ID3v1 tag, which the audio fingerprint must skip;
- FLAC, Ogg Vorbis, Opus: MUZZZLO_TEST with two values and REPLAYGAIN_TRACK_GAIN;
- M4A: a freeform ----:com.apple.iTunes:MUZZZLO_TEST atom.
Run by make_fixtures.sh after tag_fixtures.py, or alone: python -I extra_fixtures.py <resources/tags>.
"""
import shutil
import sys
from pathlib import Path

from mutagen.apev2 import APEv2
from mutagen.flac import FLAC
from mutagen.id3 import ID3, PRIV, TXXX
from mutagen.mp4 import MP4, MP4FreeForm
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis

OUT = Path(sys.argv[1])
PRIV_OWNER = "org.muzzzlo.test"
PRIV_DATA = b"\x00\x01private\xff"
VALUES = ["kept one", "kept two"]
GAIN = "-6.50 dB"


def copy(source, target):
    shutil.copyfile(OUT / source, OUT / target)
    return OUT / target


def extra_id3(source, target, version, trailing_tags):
    path = copy(source, target)
    tags = ID3(path)
    tags.add(TXXX(encoding=3, desc="MUZZZLO_TEST", text=VALUES[0]))
    tags.add(TXXX(encoding=3, desc="REPLAYGAIN_TRACK_GAIN", text=GAIN))
    tags.add(PRIV(owner=PRIV_OWNER, data=PRIV_DATA))
    tags.save(path, v2_version=version, v23_sep=None, v1=0)
    if trailing_tags:
        # APEv2 first, then ID3v1 after it, as in files written by older taggers.
        ape = APEv2()
        ape["MUZZZLO_APE"] = "kept"
        ape.save(path)
        ID3(path).save(path, v2_version=version, v23_sep=None, v1=2)


def extra_vorbis(f):
    f["MUZZZLO_TEST"] = VALUES
    f["REPLAYGAIN_TRACK_GAIN"] = GAIN
    f.save()


def extra_mp4(source, target):
    path = copy(source, target)
    f = MP4(path)
    f["----:com.apple.iTunes:MUZZZLO_TEST"] = [MP4FreeForm(VALUES[0].encode("utf-8"))]
    f.save()


extra_id3("mp3_v23.mp3", "extra_v23.mp3", 3, trailing_tags=True)
extra_id3("mp3_v24.mp3", "extra_v24.mp3", 4, trailing_tags=False)
extra_vorbis(FLAC(copy("flac.flac", "extra.flac")))
extra_vorbis(OggVorbis(copy("ogg.ogg", "extra.ogg")))
extra_vorbis(OggOpus(copy("opus.opus", "extra.opus")))
extra_mp4("m4a.m4a", "extra.m4a")
print("extra fixtures written in", OUT)
