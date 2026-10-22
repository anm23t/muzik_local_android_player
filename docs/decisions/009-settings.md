# 009. Settings: contents, playback settings in 0.5, dynamic accent switch

Status: accepted

## Settings screen

`feature:settings` is built in task 0.5.1: the screen, a settings repository on DataStore (interface in `core:model`)
and the items that exist by then (library folders, minimum track length, rescan, quarantined files, ID3 version for
untagged files, confirmation before writing a file, compound names, language, dynamic accent). Every later feature
adds its own items in its own task. Settings go into the JSON backup. The full list with defaults is in
ARCHITECTURE.md, section 9.

## Playback moves from 1.2 to a new version 0.5

Gapless, ReplayGain, crossfade (adjustable 0–12 s), playback behaviour and the sleep timer form version 0.5,
between multiple artists (0.4) and statistics (now 0.6).

- Not earlier: ReplayGain needs tag reading (0.3.2), the settings screen needs the design system (0.3.4).
- Before statistics: crossfade makes two tracks overlap, so the rule for counting a listen (0.6.1) must know about it,
  and the counting threshold itself is a setting.
- Multiple artists, the main feature of the app, are not delayed.

Future versions after 1.0 move up by one (equalizer 1.2, smart playlists 1.3, statistics cards 1.4, batch editing and
synced lyrics 1.5).

## Dynamic accent can be switched off

Decision 9 in CLAUDE.md stays: the accent follows the cover by default. A switch in the interface settings turns it
off, and then the accent is always the default red `#CD253E` and covers are not analysed. The theme stays dark only.

## Sleep timer moves to 1.2

The owner postponed the sleep timer (by time, or "finish the current track") from task 0.5.5 to version 1.2, after the
1.0 release: there is no need for it yet, and it needs a dialog in the player that has no mockup. Task 0.5.5 keeps the
audio focus, headset and silence settings.

## Deliberately not included

Light theme (decision 8), playback speed and pitch, A-B repeat, network features before 2.0.

## Update check is the one network request (task 1.1.9)

The app can ask GitHub (`releases/latest` of the project, nothing else) once a day at start whether a newer release
exists, and tell the user with a link to the release page. It never downloads or installs an APK. CLAUDE.md allows no
network request without an explicit "on" setting, so the first start of 1.1 asks once "Check for updates?" (closing the
window counts as "no"), and the setting stays in the settings screen. The answer, the time of the last check and the
last announced tag live in DataStore next to, but not inside, `AppSettings`, and they are not in the backup: consent to
use the network belongs to one installation. The `INTERNET` permission exists only for this request, which sends no
user data.
