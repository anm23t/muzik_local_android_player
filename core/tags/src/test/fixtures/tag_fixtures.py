"""Writes the reference tags into the fixtures with mutagen, independently of TagLib.

Expected values live in ReferenceFixtures.kt; keep both in sync.
Every tagged file carries the same logical tags, stored the way each format stores them:
- artists: separate values in Vorbis comments, ID3v2.4 and MP4; one "Alpha; Beta" string in ID3v2.3;
- track and disc: "3/10" and "1/2" in ID3v2 and MP4; TRACKNUMBER plus TRACKTOTAL (FLAC) or TOTALTRACKS (Ogg);
- lyrics: USLT without a description (v2.4) and with one (v2.3), LYRICS (FLAC, Vorbis), UNSYNCEDLYRICS (Opus), ©lyr;
- pictures: an "other" picture first, then the front cover, so readers must pick the cover by type
  (MP4 has no picture types and holds the front cover only).
"""
import base64
import sys
from pathlib import Path

from mutagen.flac import FLAC, Picture
from mutagen.id3 import APIC, ID3, TALB, TCON, TDRC, TIT2, TPE1, TPE2, TPOS, TRCK, TYER, USLT
from mutagen.mp4 import MP4, MP4Cover
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis

OUT = Path(sys.argv[1])
FRONT = (OUT / "cover_front.png").read_bytes()
OTHER = (OUT / "cover_other.png").read_bytes()

TITLE = "Reference 🎵 тест"
ARTISTS = ["Alpha", "Beta"]
ALBUM = "Test Album"
ALBUM_ARTIST = "Alpha"
GENRE = "Test"
LYRICS = "[00:00.10]Line one\n[00:00.50]Вторая строка 🎶"


def flac_picture(data, picture_type):
    pic = Picture()
    pic.type = picture_type
    pic.mime = "image/png"
    pic.width = pic.height = 8
    pic.depth = 24
    pic.data = data
    return pic


def tag_id3(path, version):
    tags = ID3()
    tags.add(TIT2(encoding=3, text=TITLE))
    # v2.3 has no multi-value text frames: one string with "; " (ARCHITECTURE.md, section 5).
    tags.add(TPE1(encoding=3, text=ARTISTS if version == 4 else ["; ".join(ARTISTS)]))
    tags.add(TALB(encoding=3, text=ALBUM))
    tags.add(TPE2(encoding=3, text=ALBUM_ARTIST))
    tags.add(TRCK(encoding=3, text="3/10"))
    tags.add(TPOS(encoding=3, text="1/2"))
    tags.add(TCON(encoding=3, text=GENRE))
    tags.add(TDRC(encoding=3, text="2024") if version == 4 else TYER(encoding=1, text="2024"))
    tags.add(APIC(encoding=3, mime="image/png", type=0, desc="other", data=OTHER))
    tags.add(APIC(encoding=3, mime="image/png", type=3, desc="", data=FRONT))
    description = "" if version == 4 else "lrc"
    tags.add(USLT(encoding=3 if version == 4 else 1, lang="eng", desc=description, text=LYRICS))
    tags.save(path, v2_version=version, v23_sep=None)


def tag_vorbis_comments(f, total_suffix, lyrics_key, embed_pictures):
    f["TITLE"] = TITLE
    f["ARTIST"] = ARTISTS
    f["ALBUM"] = ALBUM
    f["ALBUMARTIST"] = ALBUM_ARTIST
    f["TRACKNUMBER"] = "3"
    f["DISCNUMBER"] = "1"
    if total_suffix:
        f["TRACKTOTAL"] = "10"
        f["DISCTOTAL"] = "2"
    else:
        f["TOTALTRACKS"] = "10"
        f["TOTALDISCS"] = "2"
    f["GENRE"] = GENRE
    f[lyrics_key] = LYRICS
    embed_pictures(f)
    f.save()


def flac_pictures(f):
    f.add_picture(flac_picture(OTHER, 0))
    f.add_picture(flac_picture(FRONT, 3))


def ogg_pictures(f):
    f["METADATA_BLOCK_PICTURE"] = [
        base64.b64encode(flac_picture(OTHER, 0).write()).decode("ascii"),
        base64.b64encode(flac_picture(FRONT, 3).write()).decode("ascii"),
    ]


def tag_flac(path):
    f = FLAC(path)
    # A full date checks that only the year is taken.
    f["DATE"] = "2024-05-01"
    tag_vorbis_comments(f, True, "LYRICS", flac_pictures)


def tag_ogg(f, lyrics_key):
    f["DATE"] = "2024"
    tag_vorbis_comments(f, False, lyrics_key, ogg_pictures)


def tag_mp4(path):
    f = MP4(path)
    f["\xa9nam"] = TITLE
    f["\xa9ART"] = ARTISTS
    f["\xa9alb"] = ALBUM
    f["aART"] = ALBUM_ARTIST
    f["trkn"] = [(3, 10)]
    f["disk"] = [(1, 2)]
    f["\xa9day"] = "2024"
    f["\xa9gen"] = GENRE
    f["\xa9lyr"] = LYRICS
    f["covr"] = [MP4Cover(FRONT, imageformat=MP4Cover.FORMAT_PNG)]
    f.save()


tag_id3(OUT / "mp3_v23.mp3", 3)
tag_id3(OUT / "mp3_v24.mp3", 4)
tag_flac(OUT / "flac.flac")
tag_mp4(OUT / "m4a.m4a")
tag_ogg(OggVorbis(OUT / "ogg.ogg"), "LYRICS")
tag_ogg(OggOpus(OUT / "opus.opus"), "UNSYNCEDLYRICS")
print("fixtures tagged in", OUT)
