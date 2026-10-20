<p align="center"><img src="docs/logo.png" alt="WUZZZIK logo" width="160"></p>

# WUZZZIK

**English** | [Русский](README.ru.md)

A local music player for Android that works with your own files: it reads and edits all tags (written straight into
the files), understands several artists per track and keeps listening statistics. Open source, GPLv3, no Google
services, offline by default. Developer: anm23t.

## Features

**Library**
- Scans the audio on the device (MediaStore): songs, albums, artists, with cached covers.
- Exclude folders from the library, set a minimum track length, rescan on demand.
- Files that crash the tag reader go to quarantine instead of breaking the scan.
- Formats: MP3, FLAC, Ogg/Opus/Vorbis, M4A (AAC and ALAC, with a bundled ALAC decoder), and others Android can play.

**Playback**
- Background playback with a media notification, lock screen and headset buttons.
- Queue: play next, shuffle, repeat, drag to reorder; the queue and position are restored on start.
- Swipe the cover to change tracks (in the player and the mini player), swipe up on the mini player to open the player.
- Gapless playback, crossfade (0-12 s, not between neighbours of one album), ReplayGain (track, album, auto,
  preamp, clip protection), skip silence, audio focus and headset behaviour settings.

**Tags**
- Reads title, artists, album, year, genres, cover and lyrics; edits them and writes into the file.
- Safe write: changes go to a working copy, are verified, the original is backed up and restored on any error.
- Several artists per track: split automatically (`;` first, otherwise `,`), edit by hand, reorder, merge and
  rename artist profiles, "remember as one artist" for names that contain a comma, check for similar spellings.
- Batch edit of an album: cover, album, album artists, year, genres.

**Artists and albums**
- Artist cards with albums, singles and "appears on", artist photos added by long press (never downloaded).
- Dynamic accent colour taken from the cover of the playing track (can be switched off).

**Statistics**
- Listening events with a snapshot of the title and artists, counted by a threshold you set; private mode pauses it.
- Top tracks, albums and artists (each artist counted separately) for today, week, month, year or all time.
- "Continue listening" on the home screen.

**Data**
- Backup and restore as JSON (statistics, settings, aliases, compound names), automatic backup into a folder you
  pick, export of listening history as CSV.

**Interface**
- Dark theme only. English and Russian.

### Not in 1.0 yet

Playlists, favourites and ratings in the interface, search, equalizer, sleep timer, synchronized lyrics (LRC/TTML),
smart playlists, widgets, Android Auto. The queue screen, the statistics screen details and a few other screens
are made without a mockup and may change. See [ROADMAP.md](ROADMAP.md).

## Install

1. Download the `.apk` file from the **Releases** page of this repository.
2. Open it on your phone. Android will ask to allow installing apps from this source (your browser or file
   manager); allow it for this one install.
3. Open WUZZZIK and grant access to audio files.

Requires Android 8.0 (API 26) or newer.

## Building

Requirements: JDK 17+, Android SDK (platform 35) with NDK `30.0.16248370` and CMake `3.31.6`,
a host C++ compiler for unit tests (Xcode Command Line Tools on macOS, gcc or clang on Linux).

```bash
git submodule update --init --recursive
sdkmanager "ndk;30.0.16248370" "cmake;3.31.6"
./gradlew test assembleDebug
```

A release build is signed with the key described in a git-ignored `keystore.properties` in the project root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without that file `assembleRelease` makes an unsigned APK.
See [CONTRIBUTING.md](CONTRIBUTING.md) for how to work on the code.

## Third-party code

[TagLib](https://taglib.org/) 2.3.2 (LGPL-2.1) with utfcpp (Boost Software License 1.0), as a git submodule in
`core/tags/src/main/cpp/taglib`.

The decoder of the [Apple Lossless Audio Codec](https://github.com/macosforge/alac) (Apache License 2.0), copied with
one safety patch into `core/playback/src/main/cpp/alac` (see the README there).

Fonts: Playfair Display and Literata (SIL Open Font License).

## License

GPLv3, see [LICENSE](LICENSE).
