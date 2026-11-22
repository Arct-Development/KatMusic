# Desktop feature and UI parity

KatMusic Desktop is the Flutter app in `desktop/` (Windows, Linux, macOS). It is
a separate codebase from the Android app, so "parity" here means matching the
experience and the wire protocols, not sharing code.

## Goal

Desktop should provide the full KatMusic experience with desktop-appropriate
layouts and controls. This does **not** mean sharing personal data between
devices: Desktop settings, library, playlists, downloads, and listening history
remain local to that computer.

Two things are intentionally shared with Android:

- **Listen Together (Jam)** is a live room feature, so Android and Desktop must
  connect to the same Jam server and be able to join the same room
  (`desktop/lib/features/jam`).
- **Phone → desktop YouTube sign-in** uses one protocol on both sides: the
  Android app scans the QR code and the desktop receives the session
  (`desktop/lib/features/innertube/yt_phone_pairing.dart`).

## Current parity snapshot

"Present" means the feature module exists in `desktop/lib`. This table was
written from the source tree and has not been re-verified by running the app.

| Area | Desktop status |
| --- | --- |
| Playback, queue, crossfade, AutoMix | Present (`media_kit` / libmpv; `features/player`, `features/automix`) |
| Equalizer and audio output | Present (`features/equalizer`, `features/audio_output`; the WASAPI output engine is Windows-only) |
| YouTube Music browsing, library sync and playback | Present (`features/innertube`) |
| Lossless and addon sources | Present (`features/lossless`, `features/addons`); keyed addon calls fail closed without credentials |
| Home feed, Discover, genres | Present (`features/feed`, `features/home`, `ui/discover`, `ui/genres`) |
| Local files, playlists, M3U, playlist import | Present, stored locally (`features/library`) |
| Downloads | Present (`features/downloads`) |
| Lyrics | Synced and word-by-word lyrics with provider fallback and translation (`features/lyrics`) |
| Podcasts | Present (`features/podcasts`) |
| Listening stats | Present, based on desktop-local history (`features/stats`) |
| Jam rooms | Present (`features/jam`) |
| Group Blend | Present (`features/blend`) |
| Last.fm | Bring-your-own API key; authentication and scrobbling (`features/lastfm`) |
| Spotify and Spotify Canvas | Present (`features/spotify`, `ui/canvas`) |
| Backup | Present (`features/backup`) |
| Sharing | Present (`features/sharing`, `ui/sharing`) |
| Phone → desktop YouTube sign-in | Present (`yt_phone_pairing.dart`, QR code via `qr_flutter`) |
| OS integration | System tray, global hotkeys, MPRIS (Linux), SMTC (Windows), Discord presence (`features/mpris`, `features/smtc`, `features/presence`) |
| In-app updates | Present (`features/updates`) |
| Android widgets, Android Auto, notification controls, USB exclusive output, native DSP engine | Android-only; desktop uses tray, media-key and OS media-session equivalents where one exists |

## Acceptance criteria

- Desktop users can reach every supported Android feature without hidden or
  dead-end routes; mobile-only system integrations get clear desktop
  equivalents rather than fake controls.
- Android and Desktop users can create or join the same Jam room and use the
  room's playback, queue, member, suggestion, invite and chat features.
- A phone running the Android app can sign the desktop in to YouTube Music by
  scanning its QR code.
- Desktop-owned data remains local; no account or general-purpose device sync is
  introduced as part of feature parity.
- `flutter analyze` and `flutter test --exclude-tags golden,live` pass in
  `desktop/`, the Android build passes, and the packaged desktop app is
  smoke-tested before preparing an update.

## Approach

Desktop features are implemented natively in Dart under `desktop/lib`.
Where Android and Desktop must interoperate (Jam, YouTube pairing), the wire
protocol is the contract and each side implements it independently. Android's
platform-specific code is not touched by desktop work, and hardware-dependent
Android features get a desktop-appropriate alternative instead of an emulation.

## Known differences

- Android's alternate player decks and widgets, Android Auto, USB bit-perfect
  output and the native DSP engine have no desktop counterpart.
- Streaming depends on providers accepting the requests; when one refuses, the
  player surfaces the provider's own reason instead of failing silently.
- Account-owned catalog features depend on credentials the user supplies on that
  machine (Last.fm key, Spotify sign-in, addon key).
