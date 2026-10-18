"""Writes the reference tags into the fixtures with mutagen (independent of the libraries under test)."""
import sys
from pathlib import Path

from mutagen.flac import FLAC, Picture
from mutagen.id3 import APIC, GEOB, ID3, PRIV, TALB, TCON, TDRC, TIT2, TPE1, TPE2, TPOS, TRCK, TXXX, TYER, USLT
from mutagen.mp4 import MP4, MP4Cover, MP4FreeForm
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis

OUT = Path(sys.argv[1])
COVER = (OUT / "cover.png").read_bytes()
LYRICS = "[00:01.00]Line one\n[00:02.00]Line two"
ARTISTS = ["Alpha", "Beta"]


def picture():
    pic = Picture()
    pic.type = 3
    pic.mime = "image/png"
    pic.width = pic.height = 64
    pic.depth = 24
    pic.data = COVER
    return pic


def tag_id3(path, version):
    tags = ID3()
    tags.add(TIT2(encoding=3, text="Spike Title"))
    # v2.3 has no multi-value text frames: one string with "; " (ARCHITECTURE.md, section 5).
    tags.add(TPE1(encoding=3, text=ARTISTS if version == 4 else ["; ".join(ARTISTS)]))
    tags.add(TALB(encoding=3, text="Spike Album"))
    tags.add(TPE2(encoding=3, text="Alpha"))
    tags.add(TRCK(encoding=3, text="3/10"))
    tags.add(TPOS(encoding=3, text="1/2"))
    tags.add(TCON(encoding=3, text="Test"))
    tags.add(TDRC(encoding=3, text="2024") if version == 4 else TYER(encoding=1, text="2024"))
    tags.add(APIC(encoding=3, mime="image/png", type=3, desc="", data=COVER))
    tags.add(USLT(encoding=3 if version == 4 else 1, lang="eng", desc="", text=LYRICS))
    # Unknown to the app: must survive any write.
    tags.add(TXXX(encoding=3 if version == 4 else 1, desc="MUZZZLO_TEST", text="keep-me"))
    tags.add(PRIV(owner="org.muzzzlo.spike", data=b"\x00\x01\x02private"))
    tags.add(GEOB(encoding=0, mime="application/octet-stream", filename="blob.bin", desc="blob", data=b"geob-data"))
    tags.save(path, v2_version=version, v23_sep=None)


def tag_vorbis_like(f, is_flac):
    f["TITLE"] = "Spike Title"
    f["ARTIST"] = ARTISTS
    f["ALBUM"] = "Spike Album"
    f["ALBUMARTIST"] = "Alpha"
    f["TRACKNUMBER"] = "3"
    f["TRACKTOTAL"] = "10"
    f["DISCNUMBER"] = "1"
    f["DATE"] = "2024"
    f["GENRE"] = "Test"
    f["LYRICS"] = LYRICS
    f["MUZZZLO_TEST"] = "keep-me"
    f["REPLAYGAIN_TRACK_GAIN"] = "-6.50 dB"
    if is_flac:
        f.add_picture(picture())
    else:
        import base64
        f["METADATA_BLOCK_PICTURE"] = base64.b64encode(picture().write()).decode("ascii")
    f.save()


def tag_flac(path):
    f = FLAC(path)
    tag_vorbis_like(f, True)
    # Raw APPLICATION metadata block (type 2) appended through mutagen's block list.
    from mutagen.flac import MetadataBlock
    block = MetadataBlock(b"MZZL" + b"application-block-payload")
    block.code = 2
    f.metadata_blocks.append(block)
    f.save()


def tag_mp4(path):
    f = MP4(path)
    f["\xa9nam"] = "Spike Title"
    f["\xa9ART"] = ARTISTS
    f["\xa9alb"] = "Spike Album"
    f["aART"] = "Alpha"
    f["trkn"] = [(3, 10)]
    f["disk"] = [(1, 2)]
    f["\xa9day"] = "2024"
    f["\xa9gen"] = "Test"
    f["\xa9lyr"] = LYRICS
    f["covr"] = [MP4Cover(COVER, imageformat=MP4Cover.FORMAT_PNG)]
    f["----:com.apple.iTunes:MUZZZLO_TEST"] = [MP4FreeForm(b"keep-me")]
    f.save()


tag_id3(OUT / "mp3_v23.mp3", 3)
tag_id3(OUT / "mp3_v24.mp3", 4)
tag_flac(OUT / "flac.flac")
tag_mp4(OUT / "m4a.m4a")
tag_vorbis_like(OggVorbis(OUT / "ogg.ogg"), False)
tag_vorbis_like(OggOpus(OUT / "opus.opus"), False)
print("fixtures tagged in", OUT)
