# Tag library spike (task 0.3.0)

Throwaway prototype behind `docs/decisions/005-tag-library.md`. It is a standalone Gradle build,
not part of the app: the root `settings.gradle.kts` does not include it and no `core:*` / `feature:*`
module depends on it. Nothing here is production code.

## What it does

- `fixtures/make_fixtures.sh` generates 2-second sine-tone files (MP3 with ID3v2.3 and v2.4, FLAC, M4A, OGG Vorbis, Opus)
  and tags them with mutagen (`fixtures/tag_fixtures.py`), including fields the app does not know about.
- `fixtures/dump_tags.py` is the independent oracle: after a library writes a file, mutagen reports what is really stored.
- `src/main/kotlin/spike/` wraps JAudioTagger 3.0.1 and TagLib 2.3.2 (JNI, `native/`) behind one `TagSpike` interface.
- `MatrixTest` runs every check against every library and format and writes `build/matrix/matrix.md`.
  Failures are expected for unsupported cases (`ignoreFailures`, use `-Pspike.strict=true` to fail the build).
- `PerfTest` measures read speed on the host (`build/matrix/perf.md`).
- `size/measure_jaudiotagger_r8.sh` estimates the DEX size of JAudioTagger after R8.

## Requirements

JDK 17+, `cmake`, `ninja`, `ffmpeg`, Python 3 with `mutagen`, and for Android `.so` sizes the Android NDK.
The macOS host build may need `-DCMAKE_OSX_SYSROOT=<older SDK>` if the Command Line Tools linker is older than the newest SDK.

## Run (from the repository root)

```bash
cd spikes/tag-library
python3 -m venv build/venv && build/venv/bin/pip install mutagen
./fixtures/make_fixtures.sh build/venv/bin/python build/fixtures
git clone --depth 1 --branch v2.3.2 --recurse-submodules https://github.com/taglib/taglib.git build/src/taglib
cmake -S native -B build/native-host -G Ninja -DCMAKE_BUILD_TYPE=Release -DTAGLIB_SRC=$PWD/build/src/taglib
cmake --build build/native-host
cd ../.. && ./gradlew -p spikes/tag-library test -Pspike.python=$PWD/spikes/tag-library/build/venv/bin/python
./gradlew -p spikes/tag-library test --tests spike.PerfTest -Pspike.perf.files=2000 -Pspike.python=$PWD/spikes/tag-library/build/venv/bin/python
```

Android `.so` sizes, per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`):

```bash
cmake -S native -B build/native-android/arm64-v8a -G Ninja -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=26 -DANDROID_STL=c++_static -DTAGLIB_SRC=$PWD/build/src/taglib
cmake --build build/native-android/arm64-v8a
```
