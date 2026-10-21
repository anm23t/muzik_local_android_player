"""Makes big_cover.ogg: Ogg Vorbis at 48 kHz with a 1080x1080 front cover in METADATA_BLOCK_PICTURE.

The cover is a synthetic noise JPEG of about 320 KB, so the comment packet (about 440 KB in base64) spans many
Ogg pages, as in the file that went to quarantine in task 1.1.2. TagLibTagReaderTest reads it by path and by
descriptor. Run by make_fixtures.sh, which makes big_cover.ogg and big_cover.jpg first; the JPEG is removed.
"""
import base64
import sys
from pathlib import Path

from mutagen.flac import Picture
from mutagen.oggvorbis import OggVorbis

OUT = Path(sys.argv[1])
COVER = OUT / "big_cover.jpg"
PAGE_LIMIT = 255 * 255  # the most an Ogg page can carry

picture = Picture()
picture.type = 3  # front cover
picture.mime = "image/jpeg"
picture.width = 1080
picture.height = 1080
picture.depth = 24
picture.data = COVER.read_bytes()

f = OggVorbis(OUT / "big_cover.ogg")
f.clear()
f["TITLE"] = "Big cover"
f["METADATA_BLOCK_PICTURE"] = base64.b64encode(picture.write()).decode("ascii")
f.save()
COVER.unlink()

comment_size = len(f.tags.write())
assert comment_size > 4 * PAGE_LIMIT, f"comment packet of {comment_size} bytes fits in too few pages"
print(f"big_cover.ogg: comment packet {comment_size} bytes")
