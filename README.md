<p align="center"><img src="docs/logo.png" alt="Muzzzlo logo" width="160"></p>

# muzik_local_android_player

Local music player for Android. GPLv3.

## Building

Requirements: JDK 17+, Android SDK (platform 35) with NDK `30.0.16248370` and CMake `3.31.6`,
a host C++ compiler for unit tests (Xcode Command Line Tools on macOS, gcc or clang on Linux).

```bash
git submodule update --init --recursive
sdkmanager "ndk;30.0.16248370" "cmake;3.31.6"
./gradlew test assembleDebug
```

## Third-party code

[TagLib](https://taglib.org/) 2.3.2 (LGPL-2.1) with utfcpp (Boost Software License 1.0), as a git submodule in
`core/tags/src/main/cpp/taglib`.
