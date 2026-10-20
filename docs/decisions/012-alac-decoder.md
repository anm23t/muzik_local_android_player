# 012. ALAC: bundled Apple decoder instead of MediaCodec

Status: accepted (task 1.0.6a)

## Problem

M4A files with ALAC (Apple Lossless) did not play: the player showed "This track can't be played" and skipped every
ALAC track. AAC in M4A played. On the test phone (Android 16) 259 of 262 M4A files are ALAC.

Android (AOSP) has no ALAC decoder of its own. Media3 hands ALAC to `MediaCodec`, and the only decoders there are the
vendor ones, on the test phone `c2.qti.alac.hw.decoder` and `c2.qti.alac.sw.decoder` (Qualcomm). Both fail on the
first input buffer (`Failed to process input buffer`, component error `0xe`) with every variant tried:

- the 24-byte `ALACSpecificConfig` as `csd-0` (what Media3 passes, the same as the platform `MPEG4Extractor`);
- the whole 36-byte `alac` atom as `csd-0`;
- `bits-per-sample` added to the `MediaFormat`, as the platform extractor does;
- the hardware decoder first, with decoder fallback on.

Phones of other vendors may have no ALAC decoder at all.

## Options

| | Bundled Apple ALAC decoder (NDK) | Media3 FFmpeg extension | Decoder in pure Kotlin |
|---|---|---|---|
| Source | Apple's open-source decoder, Apache 2.0, about 3000 lines of C/C++ | FFmpeg built with only the ALAC decoder, LGPL | Own port of the algorithm |
| Build | Same NDK and CMake as TagLib (`core:tags`) | Not on Maven; FFmpeg has to be built for every ABI | Nothing native |
| APK size | 43–80 KB per ABI (measured: armeabi-v7a 43 KB, x86_64 75 KB, arm64-v8a 80 KB) | About 1 MB and more | About 30 KB of dex |
| Risk | Small: the reference implementation | Larger build, more to update | Own decoding bugs |

Chosen: **the bundled Apple decoder**. It is the reference implementation, small, and fits the native build the
project already has. FFmpeg makes sense only if more formats (APE, WMA) are ever needed.

## How it works

- Sources: the decoding half of `codec/` from https://github.com/macosforge/alac, copied into
  `core/playback/src/main/cpp/alac` (not a submodule, because one safety check is patched in; the folder README lists
  the commit and the change). Built into `libalac_jni.so` with the JNI bridge `alac_jni.cpp`.
- `AlacAudioRenderer` (a Media3 `DecoderAudioRenderer`) stands before the `MediaCodec` audio renderer in
  `AudioRenderersFactory`, so ExoPlayer gives it ALAC and leaves everything else to `MediaCodec`. It takes
  `audio/alac` only when the library is loaded and the cookie is supported (`AlacConfig`: 16, 20, 24 or 32 bits,
  1–8 channels, frame length up to 65536); otherwise ALAC falls back to `MediaCodec` as before.
- `AlacDecoder` (`SimpleDecoder`) decodes one MP4 sample (an ALAC packet) per buffer on its own thread. Output: PCM
  16 bit, 24 bit (also for 20-bit sources) or 32 bit, little endian; the sink converts it as for any other source.
- Gapless and ReplayGain: `DecoderAudioRenderer` creates a new decoder for every stream and configures the sink at the
  start of each track. `TrackFormatSink` (wrapping the shared sink) applies the gapless setting to the trim data and
  adds the ReplayGain values of the queue item found in `onStreamChanged`, the same values the `MediaCodec` renderer
  passes in `Format.customData`. Silence trimming and the ReplayGain processor stay in the shared sink. Crossfade needs
  nothing extra: each deck has its own renderers factory.

## Safety

The files are the user's, so a broken or crafted file must not corrupt memory:

- the cookie is checked before the decoder sees it (frame length, channels), on the Kotlin and on the native side;
  a huge frame length would overflow the buffer sizes the decoder computes on 32-bit ABIs;
- patched in the sources: a partial frame may not declare more samples than the frame length;
- the decoder reads the bit stream without checking the end of the packet; the bridge copies each packet into a
  buffer with enough zero bytes after it (`frameLength x 12 + 1024`) for the furthest such read.

## Not covered

- Channel order of ALAC with more than two channels follows ALAC (centre first), not Android; such files are rare.
- ALAC in CAF files: the app does not scan CAF.
