# 011. Crossfade: two players behind one session player

Status: accepted (task 0.5.4)

Media3 1.5.1 has no crossfade. An ExoPlayer renders one stream at a time into one `AudioSink`; the next track starts
only after the last sample of the previous one. Crossfade needs both tracks to sound at once for up to 12 s.

## Options

| | Own `AudioProcessor` in one ExoPlayer | Tail player (main queue + helper for the outgoing track) | Two decks behind one `SimpleBasePlayer` |
|---|---|---|---|
| How | The processor holds back the last N seconds of track A and mixes them into the start of track B | At the overlap the main player jumps to B, a second player plays the rest of A | Both ExoPlayers hold the same queue; each track plays whole on one deck, a crossfade starts the next track on the other deck |
| Overlap | Fake: A's tail is decoded fast, the output is starved while it is collected (underrun risk) | Real | Real |
| Position and `onMediaItemTransition` | Wrong by N seconds: the player thinks A ended N seconds earlier | Correct | Correct (reported by the wrapper) |
| Different sample rates of A and B | Breaks: the sink reconfigures and flushes the processor, nothing left to mix | Works | Works |
| Gapless within an album | Works | Works | Works: the active deck moves on by itself |
| Weak point | All of the above | A is handed from one player to the other mid-track; the two never meet sample-exactly, an audible jump or doubling | More code: the wrapper keeps both queues in sync and handles audio focus itself |

Chosen: **two decks behind one `SimpleBasePlayer`**. It is the only option where every sample of every track comes
from one decoder from start to end, so nothing is cut, doubled or shifted.

## How it works

- `CrossfadePlayer` (a `SimpleBasePlayer`, `core:playback`) is the player of the `MediaSession`. `MediaController`,
  `MediaControllerPlayer` and the saved queue see one ordinary player and do not change.
- `ForwardingSimpleBasePlayer` is not used: it is bound to one player for its whole life, while the active deck
  changes after every crossfade.
- Two ExoPlayers ("decks"), each with its own `AudioRenderersFactory`, so gapless and ReplayGain work on both. Every
  queue change (set, add, move, remove, shuffle, repeat) goes to both decks, so their timelines are equal; the
  ReplayGain "auto" mode reads the neighbours from the timeline and stays the same.
- The active deck plays; the other one is idle (stopped, no codec held).
- When the active deck starts a track that will be crossfaded into the next one, the wrapper sets a `PlayerMessage`
  at `duration − fade − 3 s` to prepare the next item on the idle deck (paused at 0, volume 0), and one at
  `duration − fade` to start the overlap. `PlayerMessage` fires at the exact position on the playback thread, no
  polling.
- Overlap: the incoming deck plays, both volumes follow an equal-power curve (`sin` / `cos`) over the fade length,
  stepped every 20 ms with `ExoPlayer.setVolume`. The platform mixer ramps volume changes, so steps do not click.
  The volume only attenuates; ReplayGain stays in each deck's processor.
- From the start of the overlap the incoming track is the current item of the wrapper (`onMediaItemTransition`,
  position, notification). At the end the outgoing deck stops and becomes idle.
- Pause during the overlap pauses both decks, play resumes both. A seek, a skip or a queue jump during the overlap
  stops the outgoing deck at once and acts on the incoming one.
- Audio focus: if both decks requested focus they would take it from each other (two requests are two clients for the
  system). Both decks run with `handleAudioFocus = false`; the wrapper requests focus itself with the platform
  `AudioFocusRequest` (API 26, the project's min SDK), pauses on loss, ducks both decks on a transient "may duck" loss
  and resumes on gain. No new dependency. Task 0.5.5 adds the "duck or pause" choice on top of it.
- "Becoming noisy" (headphones unplugged) stays on the decks (`setHandleAudioBecomingNoisy`), the pause goes through
  the wrapper's state like any other.

## Rules (pure function in `core:model`, table tests)

No crossfade, the next track starts as now (gapless if the files allow):
- fade length 0 (the default);
- no next track in play order (end of the queue, repeat off);
- repeat one;
- the change is manual: next, previous, a jump in the queue, a seek;
- "Crossfade within album" is off (the default) and the next track continues the album: same album id and the next
  number in disc and track order (disc 1 track 3 → disc 1 track 4, or the last track of a disc → track 1 of the next
  disc). With shuffle, two tracks of one album that are not consecutive are crossfaded.

The fade is at most half of the shorter of the two tracks.

## Statistics (task 0.6.1)

During the overlap the incoming track is the current one; the outgoing track counts as ended at the start of the
overlap. The listen rule of 0.6.1 must count the fade part of the outgoing track as listened.

## Cost

No new dependencies. A second ExoPlayer instance is a few hundred KB of heap; a second codec and `AudioTrack` exist only
during the overlap and a few seconds before it.
