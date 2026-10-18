# 005. Tag library: TagLib 2.x through JNI

Status: accepted (task 0.3.0)

## Context

`core:tags` must read and write title, artists, album, cover and lyrics directly in the files (CLAUDE.md, decisions 1, 2, 8, 12)
for MP3 (ID3v2.3 and v2.4), FLAC, M4A, OGG Vorbis and Opus. Several artists are stored as separate values
in Vorbis comments and ID3v2.4 and as one `A; B` string in ID3v2.3 (ARCHITECTURE.md, section 5). A write must
never lose data the app does not understand, and it runs on a temporary copy before the original is replaced.

Candidates: **JAudioTagger** (pure Java, `net.jthink:jaudiotagger:3.0.1`) and **TagLib** (C++, 2.3.2, through our own JNI bridge).

## How it was checked

A throwaway prototype lives in `spikes/tag-library/` (standalone Gradle build, not part of the app; see its README).
Synthetic 2-second sine-tone fixtures are tagged with mutagen, including fields the app does not know
(`TXXX`, `PRIV`, `GEOB`; custom Vorbis fields and `REPLAYGAIN_*`; a FLAC `APPLICATION` block; an MP4 freeform atom).
Both libraries run the same checks through one interface. After every write, mutagen (an independent third library)
reports what is really in the file, and ffmpeg compares an MD5 of the audio packets before and after.

Legend: **P** = verified by the prototype, **D** = from documentation or source code.

## Comparison

### Formats and fields (P, 16 checks × 6 files per library)

| Check | JAudioTagger 3.0.1 | TagLib 2.3.2 |
|---|---|---|
| MP3 ID3v2.3 / v2.4: read, write | ✔ | ✔ |
| FLAC: read, write | ✔ (cover needs a FLAC-specific call, see below) | ✔ |
| M4A: read, write | ✔ except multiple artists (below) | ✔ |
| OGG Vorbis: read, write | read ✔; written cover was **lost** | ✔ |
| Opus | ✘ not supported at all ("No Reader associated with this extension") | ✔ |
| Several artists: Vorbis/FLAC, ID3v2.4 | ✔ separate values | ✔ separate values |
| Several artists: ID3v2.3 as `A; B` | ✔ (we join ourselves) | ✔ (we join ourselves) |
| Several artists: M4A | reads only the first value of a standard multi-value `©ART`; writes several `©ART` atoms (non-standard) | ✔ both ways |
| ID3 version kept on write | ✔ | ✔, but only when the version is passed explicitly: `FileRef::save()` silently upgrades v2.3 to v2.4 |
| Cover: read, replace, remove | ✔ for MP3/M4A; FLAC and Vorbis in Android mode throw `UnsupportedOperationException` through the generic API | ✔ all formats (`PICTURE` complex property) |
| Lyrics (USLT, `LYRICS`, `©lyr`), multi-line LRC text intact | ✔ | ✔ |
| Unknown frames and fields kept | ✔ | ✔ when written as read-modify-write of the full `PropertyMap` (see risks) |
| Audio data unchanged (MD5 of packets) | ✔ | ✔ |
| Unicode title written and read back | ✔ | ✔ |

### Android

| Topic | JAudioTagger | TagLib |
|---|---|---|
| File access | `java.io.File` / `RandomAccessFile` only: every read needs a real path, so on scoped storage every file would first have to be copied (D, P on host) | Opens by path **or by file descriptor** (`FileStream(int fd)`): scanning can read a `content://` URI through `ContentResolver.openFileDescriptor(uri, "r")` without copying (P on host) |
| Safe write (ARCHITECTURE.md, section 5) | write to the temporary copy by path: works | write to the temporary copy by path: works; same flow |
| Java desktop APIs | references `java.awt` / `javax.imageio` (8 classes) for artwork; avoided with `TagOptionSingleton.setAndroid(true)`, which in turn breaks the generic FLAC/Vorbis artwork API (P) | none |
| Scoped storage permission to replace the original | same for both: `MediaStore.createWriteRequest` (API 30+), `RecoverableSecurityException` (API 29), `WRITE_EXTERNAL_STORAGE` (API 26–28); then copy the verified temp file into `openFileDescriptor(uri, "rwt")` | same |

### Size, speed, project, license

| Topic | JAudioTagger | TagLib |
|---|---|---|
| APK size | jar 1.17 MB; DEX **≈ 520 KB** after R8 for a usage stub (P, `size/measure_jaudiotagger_r8.sh`); one copy for all ABIs | `libtaglib_jni.so`, MinSizeRel, only MP3/MP4/Ogg/FLAC/Opus, stripped, `c++_static`, NDK r30 (P): arm64-v8a **564 KB**, armeabi-v7a **326 KB**, x86_64 **523 KB** (gzip ≈ 207 / 168 / 208 KB). AGP stores `.so` uncompressed in the APK for minSdk 26, so the install size is the raw figure; the download is closer to the gzip one |
| Read speed on host, warm cache (P, `PerfTest`, 2000 reads) | 898 µs per small tagged file; 86 µs for a 4-minute untagged MP3/FLAC | 65 µs per small tagged file; 24–26 µs for 4-minute files |
| Activity | last release on Maven Central 3.0.1, October 2021; commits continue in the Bitbucket repository (2026) but are not released | 2.3 (May 2026), 2.3.1 (July 2026), 2.3.2 (September 2026); active GitHub, input hardening in 2.3.2 |
| Used by Android players | historically several | Auxio (GPL-3.0) bundles TagLib through JNI in its `musikr` module |
| License | LGPL-2.1-or-later | LGPL-2.1 or MPL-1.1 (dual); bundled utfcpp: Boost 1.0 |
| GPLv3 compatible | yes | yes: LGPL-2.1 permits use under GPL, including static linking into a GPLv3 app whose source is published |

## Decision

Use **TagLib 2.x through a small JNI bridge** in `core:tags`.

- It passed every check on every target format, including Opus, which JAudioTagger cannot open at all.
  Opus support is required, and adding it to an unreleased Java library would mean maintaining a fork.
- Multiple artists in M4A and covers in FLAC/Vorbis work through one API in TagLib; in JAudioTagger they need
  format-specific workarounds, and the Vorbis cover was still lost.
- Reading by file descriptor fits scoped storage: the scanner can read tags from `content://` URIs without copying files.
- It is several times faster on the host and actively maintained.

The cost is a native build: the NDK and CMake become build requirements, the APK gains a `.so` per ABI,
and a crash in native code takes the process down instead of throwing an exception.

## Consequences for the next tasks

- `core:tags` gets `src/main/cpp` with TagLib as a pinned git submodule or downloaded source at a fixed tag
  (not vendored copies of binaries), built by the Android Gradle plugin's `externalNativeBuild` with CMake,
  using the formats switched off in `spikes/tag-library/native/CMakeLists.txt`.
- Kotlin side: a `TagReader` / `TagWriter` interface in `core:tags`; JNI types stay internal to the module.
- Write = read the full `PropertyMap`, change only our keys, `setProperties`, then save. For MPEG files pass
  the ID3v2 version the file had (`MPEG::File::save(AllTags, StripNone, v3|v4)`).
- `FileStream(int fd)` closes the descriptor itself: hand it `ParcelFileDescriptor.detachFd()` or a `dup()`.
- New dependencies for the app (CLAUDE.md, code rules): TagLib (source at a pinned version), the Android NDK and CMake.
  Approved; they are wired up in task 0.3.1 (connecting TagLib), reading follows in 0.3.2, safe writing in 0.3.3.

## License

- TagLib is dual-licensed: **LGPL-2.1** (version 2.1 only, not "or later") or **MPL-1.1**, per file headers and
  `COPYING.LGPL` / `COPYING.MPL`. MUZZZLO uses it under **LGPL-2.1**; MPL-1.1 alone is not compatible with the GPL.
- **Compatible with GPLv3:** LGPL-2.1, section 3, allows any copy of the library to be used under the GNU GPL
  version 2 or any later version, so linking TagLib (statically, into `libtaglib_jni.so`) into a GPLv3 app is allowed.
  The FSF lists LGPL-2.1 as GPLv3-compatible.
- utfcpp, bundled with TagLib, is under the Boost Software License 1.0, which is GPL-compatible.
- Obligations: ship the license texts and copyright notices of TagLib and utfcpp with the app (licenses screen and
  repository), state the TagLib version used, and keep the corresponding source available. The app being GPLv3 with
  public source covers the LGPL requirement to allow relinking.

## Open risks

1. **Native crashes.** A malformed file can crash the process. Mitigation: pin a recent TagLib (2.3.2 hardened parsers),
   mark a file as "in progress" before native reading so a file that crashed the process is skipped and flagged after restart (0.3.1),
   fuzz the bridge with broken fixtures in 0.3.2 (done: `BrokenFilesTest`), and consider reading tags in a separate `:scanner` process if crashes show up.
2. **Strings across JNI.** The spike uses `NewStringUTF`, which expects modified UTF-8: characters outside the BMP (emoji) would break.
   Production code passes strings as UTF-8 byte arrays in both directions, with a mandatory emoji test (ARCHITECTURE.md, section 5).
3. **Build complexity.** CI and contributors need the NDK and CMake; F-Droid builds need the same NDK version pinned. ABI splits are planned in 1.0.3.
4. **APK size per ABI** as measured above; a universal APK carries all ABIs.
5. **Speed on a phone** — measured in task 0.3.2 with `TagReadBenchmark` (`core:tags` instrumented tests) on a phone reporting
   Android 16 (API 36), 1586 music files (1117 MP3, 427 FLAC, 23 M4A, 9 Ogg, 10 WAV), reading as the scanner does:
   open the `content://` URI, read tags and audio properties by descriptor behind `NativeCallGuard`, one file at a time.
   - First run (page cache cold for most files): 27.8 s in total, 17.5 ms per file on average.
   - Warm runs: 10.2–10.8 s, mean 6.4–6.8 ms, median 4.5–4.7 ms, p95 15.5–15.8 ms, max ≈ 0.1 s per file.
   - Split of a warm read: opening the URI 1.7 ms, the guard's marker 0.2 ms, TagLib 4.0 ms (audio properties add
     under 0.1 ms). Host numbers (65 µs) are two orders lower: on the phone the time goes to I/O through the FUSE layer
     of shared storage, not to parsing.
   - Only changed files are read on later scans, so this cost is paid in full once. Reading several files in parallel
     is the obvious next step if the first scan feels slow; it widens what a native crash quarantines (section on guard).
   - The run also found that TagLib's content sniffing rejects some valid MP3 files (a LAME "Info" frame followed by a
     second ID3v2 tag; 32 files on that phone) when it reads a descriptor without a name. The bridge now passes the
     MediaStore display name so the format is picked by extension, as for a path.
6. **M4A multiple artists**: TagLib writes several values inside one `©ART` atom; some players show only the first. Accepted, since it is the standard form.
7. **`PropertyMap` semantics**: `setProperties` drops mapped fields missing from the map (custom `TXXX`, Vorbis fields). Always write the full map read from the file; covered by the "unknown fields kept" test, which must move to `core:tags`.
8. **Replacing the original** through `openFileDescriptor(uri, "rwt")` and the permission dialogs were not exercised in the spike; this is task 0.3.3.
