"""Independent oracle: prints what is really stored in a file as JSON (via mutagen)."""
import base64
import hashlib
import json
import sys

import mutagen
from mutagen.flac import FLAC, Picture
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4


def sha(data):
    return hashlib.sha256(bytes(data)).hexdigest()[:16]


def dump_id3(f):
    t = f.tags
    out = {"id3_version": f"2.{t.version[1]}"}
    out["artists"] = list(t["TPE1"].text) if "TPE1" in t else []
    out["title"] = str(t["TIT2"]) if "TIT2" in t else None
    out["album"] = str(t["TALB"]) if "TALB" in t else None
    out["albumartist"] = str(t["TPE2"]) if "TPE2" in t else None
    out["track"] = str(t["TRCK"]) if "TRCK" in t else None
    out["genre"] = str(t["TCON"]) if "TCON" in t else None
    out["covers"] = [sha(p.data) for p in t.getall("APIC")]
    out["lyrics"] = [u.text for u in t.getall("USLT")]
    out["unknown"] = {
        "TXXX:MUZZZLO_TEST": [str(x) for x in t.getall("TXXX:MUZZZLO_TEST")],
        "PRIV": [sha(p.data) for p in t.getall("PRIV")],
        "GEOB": [sha(g.data) for g in t.getall("GEOB")],
    }
    out["frames"] = sorted(t.keys())
    return out


def dump_vorbis(f, is_flac):
    t = f.tags
    g = lambda k: list(t.get(k, []))
    out = {
        "artists": g("ARTIST"), "title": g("TITLE"), "album": g("ALBUM"), "albumartist": g("ALBUMARTIST"),
        "track": g("TRACKNUMBER"), "genre": g("GENRE"), "lyrics": g("LYRICS") + g("UNSYNCEDLYRICS"),
        "unknown": {"MUZZZLO_TEST": g("MUZZZLO_TEST"), "REPLAYGAIN_TRACK_GAIN": g("REPLAYGAIN_TRACK_GAIN")},
        "fields": sorted(set(k.upper() for k in t.keys())),
    }
    if is_flac:
        out["covers"] = [sha(p.data) for p in f.pictures]
        out["unknown"]["APPLICATION"] = [sha(b.data) for b in f.metadata_blocks if b.code == 2]
    else:
        out["covers"] = [sha(Picture(base64.b64decode(v)).data) for v in g("METADATA_BLOCK_PICTURE")]
    return out


def dump_mp4(f):
    t = f.tags
    g = lambda k: list(t.get(k, []))
    return {
        "artists": g("\xa9ART"), "title": g("\xa9nam"), "album": g("\xa9alb"), "albumartist": g("aART"),
        "track": [list(x) for x in g("trkn")], "genre": g("\xa9gen"), "lyrics": g("\xa9lyr"),
        "covers": [sha(c) for c in g("covr")],
        "unknown": {"MUZZZLO_TEST": [bytes(x).decode() for x in g("----:com.apple.iTunes:MUZZZLO_TEST")]},
        "atoms": sorted(t.keys()),
    }


f = mutagen.File(sys.argv[1])
if isinstance(f, MP3):
    print(json.dumps(dump_id3(f)))
elif isinstance(f, MP4):
    print(json.dumps(dump_mp4(f)))
else:
    print(json.dumps(dump_vorbis(f, isinstance(f, FLAC))))
