# Apple ALAC decoder (vendored)

The decoding half of the Apple Lossless Audio Codec, used by `AlacAudioRenderer` because the vendor ALAC
decoders of some phones fail in `MediaCodec` (`docs/decisions/012-alac-decoder.md`).

- Source: https://github.com/macosforge/alac, commit `c38887c5c5e64a4b31108733bd79ca9b2496d987` (2016-05-11),
  folder `codec/`.
- License: Apache License 2.0 (`LICENSE` in this folder), compatible with the GPLv3 of the app.
- Only the decoder files are copied; the encoder, the `convert-utility` and the documentation are left out.

## Changes against the source

Marked with `MUZZZLO:` comments in the code.

1. `ALACDecoder.cpp`: a partial frame may not declare more samples than `frameLength`. Without the check a broken
   or crafted file writes past the decoding buffers.

The endianness is set by the build (`TARGET_RT_LITTLE_ENDIAN=1` in `../CMakeLists.txt`), not in the sources.
Reads past the end of a packet are kept inside a zero-padded copy made by the JNI bridge (`../alac_jni.cpp`).
