# 010. Accent extraction: MaterialKolor, contrast rule, cache marker

Status: accepted (task 0.3.8)

## Library: MaterialKolor 2.0.0

The accent is picked from the cover with `com.materialkolor:material-color-utilities:2.0.0`, a Kotlin port of
Google's Material Color Utilities (MIT licence, compatible with GPLv3). Google does not publish the Java version on
Maven, so the port is the only artifact of MCU that does not pull in the whole Material Components library.

| | androidx.palette 1.0.0 | MaterialKolor 2.0.0 |
|---|---|---|
| Artifact | AAR 23 KB, 45 KB of classes | jar 134 KB, about 114 KB of classes in the used part (quantize, score, hct, contrast, utils) |
| APK after R8 (estimate) | about 15–20 KB | about 40–60 KB, measured in 0.3.8 by comparing `assembleRelease` before and after |
| HCT and contrast | no, HSL swatches only | yes: `QuantizerCelebi`, `Score` (drops grey colors itself), `Hct`, `Contrast` |
| Tests without Android | no, needs `Bitmap` (Robolectric) | yes, works on an `IntArray` of pixels |
| Licence | Apache 2.0 | MIT |

Why MaterialKolor: DESIGN.md (section 4) adjusts the tone in HCT, and the whole picking logic can be covered by plain
JUnit tests. Palette would need our own HCT code and Robolectric for every edge case.

Limitation: 2.0.0 is built with kotlin-stdlib 2.0.20 and fits the project's Kotlin 2.0.21. Version 2.0.1 and later
need Kotlin 2.1+, so the library is updated only together with Kotlin.

## Contrast rule

The accent is used both as a text color (the current track title in `TrackRow`) and as a fill under a white icon (the
big play button). The default red `#CD253E` gives about 3.9:1 on `#000000` and 3.5:1 on `#121212`, so a 4.5:1 rule for
text would reject the mockup's own color. The rule for an adjusted accent is therefore:

- at least 3:1 against `#121212` (and so against `#000000`);
- white on the accent at least 3:1, which caps the tone from above.

The default red is not adjusted.

## Cache marker in `album.accent_color`

The column exists since schema version 1, no migration is needed. Values:

- `NULL`: not computed yet (or reset because the cover may have changed);
- `0` (transparent ARGB): computed, no suitable color, the default red is used;
- any other value: the adjusted accent (ARGB, opaque).

## Settings flag

The "dynamic accent" flag is postponed to task 0.5.1, together with the settings repository. Until then the accent is
always dynamic.
