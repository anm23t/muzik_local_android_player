# Tag test fixtures

Small synthetic files only: generated sine tones, no copyrighted music, covers or lyrics.

| File | Content | How it was made |
|---|---|---|
| `smoke.flac` | 1 s, 440 Hz sine, mono, 8 kHz; title `Smoke 🎵 тест` (emoji outside the BMP, Cyrillic) | see below |
| `tags/*.mp3, *.flac, *.m4a, *.ogg, *.opus` | 1 s sine tones with the same reference tags in every format: MP3 ID3v2.3 and v2.4, FLAC, M4A, Ogg Vorbis, Opus; `untagged.*` without tags | `src/test/fixtures/make_fixtures.sh` |
| `tags/extra*.mp3, extra.flac, extra.m4a, extra.ogg, extra.opus` | copies of the tagged files with fields the app does not know: TXXX and PRIV frames, an APEv2 and an ID3v1 tag at the end of `extra_v23.mp3`, custom Vorbis fields, a freeform MP4 atom; a tag write must keep them | `src/test/fixtures/extra_fixtures.py` (run by the same script) |
| `tags/cover_front.png`, `tags/cover_other.png` | 8×8 solid colour pictures embedded as the front cover and an "other" picture | same script |

The reference values and how each format stores them are described in `src/test/fixtures/tag_fixtures.py`;
`TagLibTagReaderTest` expects the same values. To regenerate (needs ffmpeg and Python with mutagen 1.47.0):

```bash
python3 -m venv /tmp/mutagen && /tmp/mutagen/bin/pip install mutagen==1.47.0
core/tags/src/test/fixtures/make_fixtures.sh /tmp/mutagen/bin/python
```

Broken files for fuzzing are not stored here: `BrokenFilesTest` derives them from these fixtures at run time.

### smoke.flac

```bash
ffmpeg -f lavfi -i "sine=frequency=440:duration=1:sample_rate=8000" -ac 1 -c:a flac \
  -metadata title="Smoke 🎵 тест" -fflags +bitexact -flags:a +bitexact smoke.flac
```
