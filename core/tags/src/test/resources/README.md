# Tag test fixtures

Small synthetic files only: generated sine tones, no copyrighted music, covers or lyrics.

| File | Content | How it was made |
|---|---|---|
| `smoke.flac` | 1 s, 440 Hz sine, mono, 8 kHz; title `Smoke 🎵 тест` (emoji outside the BMP, Cyrillic) | see below |

```bash
ffmpeg -f lavfi -i "sine=frequency=440:duration=1:sample_rate=8000" -ac 1 -c:a flac \
  -metadata title="Smoke 🎵 тест" -fflags +bitexact -flags:a +bitexact smoke.flac
```
