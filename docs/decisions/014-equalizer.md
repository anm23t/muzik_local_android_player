# 014. Equalizer: own audio processor in the Media3 chain

Status: accepted (task 1.2.8)

## Problem

The owner asked for an equalizer: 10 bands from 31 Hz to 16 kHz, ±12 dB, a preamp, ready-made curves and the
user's own one, without clipping together with ReplayGain. Playback runs on two ExoPlayer decks for crossfade
(decision 011), and both must sound the same.

## Options

| | Own processor in the audio sink | Platform `android.media.audiofx.Equalizer` |
|---|---|---|
| Decks | One processor per deck, the same settings | Bound to an audio session: two decks, two sessions, two effects to keep in step |
| Bands | Any: 10 bands at fixed octaves | What the device offers, usually 5 bands at device-chosen frequencies |
| Behaviour | The same on every phone | Differs by vendor; some phones apply it late or not at all |
| Clipping | Knows its own curve, lowers the output by its peak | No control over the headroom |
| Tests | Frequency response and processing in JVM unit tests | Needs a device |
| Cost | CPU of 10 biquads per sample and channel (a few percent of one core at 48 kHz stereo) | Runs in the platform audio path |

Chosen: **the own processor**. The platform effect cannot give 10 fixed bands, is hard to keep in step on two decks
and cannot be tested.

## How it works

- `Equalizer` (`core:model`, pure Kotlin): one peaking biquad per band after the Audio EQ Cookbook (R. Bristow-
  Johnson), Q = √2 (one octave), gains in steps of 0.5 dB from −12 to +12. A band without gain, or at or above 45%
  of the sample rate, is left out. `responseDb` gives the response of the whole curve; `headroomDb` is its highest
  point between 20 Hz and 20 kHz and at the band centres.
- `EqualizerSettings` in `AppSettings` (DataStore, backup): on/off (off by default), the curve in use
  (`EqualizerPreset`: Flat, Bass, Treble, Vocal, Rock, Pop, Jazz, Classical, Electronic or Custom), the custom curve
  and the preamp. Changing a band of a ready-made curve starts the custom curve from it; choosing a ready-made curve
  keeps the custom one for later.
- `EqualizerAudioProcessor` (`core:playback`) runs after ReplayGain in `TrimChain` of each deck, on 16-bit and float
  PCM, in transposed direct form II with the state per channel. A neutral equalizer (off, or flat without preamp)
  copies the samples. New settings apply from the next buffer; running filters keep their state, so a dragged slider
  does not click. A flush clears the state.
- Clipping: with the ReplayGain clip protection on, the output is lowered by `headroomDb` (preamp plus the highest
  point of the curve, at least 0), so a full-scale sine at any frequency stays at full scale at most; ReplayGain has
  already kept the peak of the track under full scale. Samples over full scale are clamped in any case.
- The screen (`feature:settings`, row "Equalizer" in "Playback", no mockup): the switch, curve chips, 10
  `BandSlider`s, the preamp with −/+, a note on the clip protection and "Reset to flat".

## Consequences

- Turning the clip protection on makes the music quieter by the boost of the curve, which is the price of no
  distortion; the note on the screen says so.
- The processor adds no latency and no dependency.
