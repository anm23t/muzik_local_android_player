# 013. Waveform of the big player: own decoding pass, peaks cached in a file

Status: accepted (task 1.2.7)

## Problem

The owner asked for a waveform of bars (as on SoundCloud) instead of the progress bar of the big player; the mini
player keeps its thin bar. The shape of a track is not in its tags or in MediaStore, so the audio has to be decoded
once. Playback must not suffer, and ALAC must work although vendor ALAC codecs crash on some phones (decision 012).

## Options

| | Own pass: `MediaExtractor` + `MediaCodec`, ALAC through the bundled decoder | Tap the PCM while the track plays | Media3 `Transformer` to PCM |
|---|---|---|---|
| Ready | Seconds after the player opens, then from the cache | Only the played part, the rest appears while listening | Seconds, like the own pass |
| Dependencies | None (platform and the existing JNI decoder) | None | `media3-transformer`, about 1 MB |
| Seek and skip | Whole shape at once | Holes after a seek | Whole shape at once |
| Complexity | One decoder loop, two input paths | Hooks in the audio sink of both decks | Pipeline meant for video export |

Chosen: **the own decoding pass**. It needs no new dependency, gives the whole shape at once and reuses the ALAC
decoder that playback already has.

## How it works

- `WaveformViewModel` (`feature:player`) asks `WaveformRepository` (`core:model`) for the peaks of the current track
  while the big player is open and the setting "Waveform" (Interface, on by default) is on. A new file starts from
  null, so the plain bar shows until its peaks are ready; then `WaveformBar` fades over in 300 ms.
- `DefaultWaveformRepository` (`core:playback`) looks in `cacheDir/waveforms` first. The key is the SHA-256 of the
  URI, size and `DATE_MODIFIED` from MediaStore, so an edited file is decoded again. One file is decoded at a time
  (a mutex); a skipped track cancels its decoding at the next packet.
- `WaveformDecoder` takes the audio track from `MediaExtractor`. ALAC packets go to `AlacStreamDecoder`, the rest to
  `MediaCodec`. `PcmPeaks` reads 16, 24, 32 bit and float PCM. `PeakCollector` (`core:model`) keeps the loudest
  sample of each of 2048 equal parts, placed by frame number against the length from the header.
- The cache file is `MZWF`, a version byte, the number of peaks and one byte per peak: about 2 KB per track, 8 MB at
  most (the files used the longest ago go first). A damaged file is decoded again.
- `Waveform.bars` folds the peaks into as many bars as fit the width (bar 3 dp, gap 2 dp): each bar takes the
  loudest peak of its share, then the bars are scaled to the loudest, so quiet recordings fill the height too. The
  smallest bar is 6% of the height, so silence still shows a line.
- Seeking works as before: a tap or a horizontal drag on the waveform (`seekInput`, shared with `ProgressBar`).

## Consequences

- The first opening of the player for a track costs one decoding pass, about as long as a few seconds of CPU for a
  lossless file. It runs on the IO dispatcher alongside playback.
- Files that the platform cannot decode (and ALAC with an unusual cookie) keep the plain bar.
- The cache can always be deleted: everything in it is made again from the audio files.
