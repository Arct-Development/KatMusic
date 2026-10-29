# Changelog

All notable changes to KatMusic are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/).

## [1.5.1] – 2026-10-29

### Fixed

- Crash on Samsung/Android 16 (`ForegroundServiceDidNotStartInTimeException`): the media
  service was requested from ExoPlayer's `onIsPlayingChanged`, i.e. after playback had
  already begun, so Media3 never observed the play transition that posts the foreground
  notification and the system killed the app after five seconds. The service is now
  requested once, before playback starts, and a service that never begins playback stops
  itself instead of idling past the deadline

## [1.5.0] – 2026-10-29

### Added

- Debug-build logger: events, warnings and errors are appended to a rotating log file,
  readable and copyable from Settings → Data → Debug log, so a bug report can include what
  the app was doing instead of only the stack trace. Release builds log to Logcat only
  and write nothing to disk

### Changed

- One player design. The Expressive / Classic / Apple style switch is gone; the artwork
  always uses the spinning vinyl behind a breathing cover, so there is a single visual
  language to keep consistent

## [1.4.2] – 2026-10-29

### Fixed

- Crashes are now diagnosable: an uncaught exception is saved to the app's storage and
  shown on the next launch with a one-tap "Copy report" button, instead of the app just
  closing
- The equalizer attaches defensively. Some devices throw from the platform audio-effect
  calls on an unusual audio session, which used to take the app down the first time a
  track started; effect reads are now guarded and the EQ simply reports unavailable

### Changed

- Type scale follows the reference app more closely (57/45/36 displays, 32/28/24
  headlines, 22 titles, no negative tracking), so titles read as Material 3 Expressive
  rather than a squeezed marketing font

## [1.4.1] – 2026-10-29

### Added

- Friends' listening feed: follow Last.fm profile names in Settings and see what they are
  playing, with a now-playing marker, artwork and one-tap playback that resolves the
  scrobble to a YouTube Music track. Uses the Last.fm key you already configured for
  scrobbling - public endpoints, nothing posted to their profile

## [1.4.0] – 2026-10-29

### Changed

- The interface is now Material 3 Expressive throughout. Every frosted "glass" sheet,
  rim highlight and translucent card is gone: cards, the mini player, the dock and the
  player controls are plain tonal surfaces with a single quiet shadow, and depth comes
  from tonal elevation instead of blur and borders
- The player transport no longer floats on a card; it sits on a scrim that fades the
  artwork out under the text, the way the reference player does
- Dragging the player down now peels the whole page back into the screen instead of
  sliding a static image away

### Added

- Word-by-word karaoke lyrics with a real glow: the lit copy of the line is masked to the
  sung extent with a feathered edge, the current word lifts and settles, and a halo rides
  the wipe so the light follows the voice. Neighbouring lines fade, soften and lag behind
  the scroll; tapping a line seeks to it
- Waveform seek bar: three flowing sine layers fill the played part of the track while the
  rest stays a flat capsule, flattening into a scrubber with a thumb while you drag
- Theme mode (System / Light / Dark) and Wallpaper colours (Material You on Android 12+)
- Lyric glow toggle in Settings, alongside the existing size and offset controls
- Lyrics options while listening, behind the tune button in the lyrics header: glow, word
  timing, romanised sub-lines, text size and timing offset, with a live preview line
- The player tab indicator now slides between tabs on a spatial spring instead of
  cross-fading, and the tabs sit on a flat tonal container rather than a translucent pill

## [1.3.0] – 2026-10-29

### Added

- Discord Rich Presence: the playing track, artist and progress appear on your Discord
  profile. Presence uses Discord's own Android RPC service over standard IPC - no partner
  SDK, no extra permissions, no token - and is configured with your own Discord
  application ID in Settings
- Android Auto and Android TV: the media session now also serves a browsable library
  (liked songs, downloads, recently played, playlists), and picking a track queues the
  whole list instead of a single song
- Multi-account switching: several YouTube Music sessions can be saved and switched from
  the account screen; the active one supplies library and recommendations
- AI picks: an OpenAI-compatible model curates songs from your listening history. The
  endpoint, model and API key are yours, entered in Settings and sent nowhere else;
  every suggestion is resolved against YouTube Music before it is shown
- Listening receipt: the stats screen renders a tall receipt card (minutes, plays, top
  artists, listening clock, barcode) and shares it as an image
- Rows across Explore, Search, Library and Discover cascade in with a short staggered
  spring instead of appearing all at once
- Player styles now change the player: Expressive keeps the vinyl and breathing cover,
  Classic and Apple use a still, tighter artwork

### Fixed

- Discord presence, Auto browsing and the player styles all reuse the existing player, so
  playback state stays in sync between the phone, the car and the status card

## [1.2.0] – 2026-10-29

### Added

- Discover screen: a radar of unheard tracks built from the artists you play, plus a
  Genre DNA chart resolved from Last.fm tags or the keyless iTunes catalogue, with one-tap
  genre radios
- Romanised lyrics: a Latin-script reading under every line, switched on in Settings
- Signed release builds via `keystore.properties` or CI environment variables, with a
  documented fallback to the debug key

- Word-by-word lyrics: Apple Music TTML from the Better Lyrics API, parsed into
  per-word timings (background vocals, translation tracks and the global
  `lyricOffset` handled) and drawn with a karaoke fill that sweeps across every
  word, interpolated at frame rate between player position updates
- Word-by-word switch in Settings; LRCLIB line lyrics stay the fallback

### Fixed

- Playback no longer dies mid-song: audio streaming and downloads now use a client
  without a whole-call timeout (the shared 45 s call timeout aborted every stream)
- Word lyrics lookup retries the provider's cache-key combinations, so cached
  songs are found with or without album and duration metadata

## [1.1.0] – 2026-10-28

### Added

- Backup and restore of the whole library and settings to a JSON file
- AutoEq headphone profile import (parametric, GraphicEQ and CSV formats)
- Device music browser that scans MediaStore and plays local files
- Now-playing home-screen widget built with Glance
- Import of pasted `Artist - Title` song lists with automatic matching
- Export of downloads into the phone's shared music library with metadata
- Offline playback prefers the downloaded file when one exists

### Changed

- New expressive design system: glass cards, floating dock, artwork-seeded tonal
  palette, ambient colour blobs and a single spring motion language
- Full player rebuilt with breathing artwork, morphing play/pause, animated lyric
  highlighting, drag-to-dismiss and an animated queue sheet
- Screen changes now animate with directional spring transitions
- Home feed reveals content with a staggered entrance and a collapsing header

### Fixed

- Downloads no longer stream from the network when an offline copy is present
- Audio now resolves through NewPipeExtractor with a probe before playback, so
  signed or throttled YouTube URLs no longer fail silently; InnerTube stays as a
  fallback
- Rejected streams invalidate their cached URL automatically

## [1.0.0] – 2026-10-28

First public build.

### Added

- YouTube Music streaming through a from-scratch InnerTube client: search with
  suggestions, home feed, charts, moods & genres, new releases, podcasts, albums,
  playlists and artist pages
- Media3 playback service with lock-screen notification, background playback,
  queue management, shuffle, repeat and autoplay radio
- Synced lyrics from LRCLIB with karaoke highlighting, offset and text-size controls
- Offline downloads with `.lrc` sidecars and a download manager
- Local library: likes, playlists, history, taste mixes, recent searches
- Playlist import from YouTube Music links
- YouTube Music account sign-in through a cookie-capturing WebView (SAPISIDHASH)
- Last.fm authorisation, now-playing updates and a retryable scrobble queue
- 10-band equalizer with presets, bass boost, virtualizer, crossfade and sleep timer
- Artwork-derived Material 3 theme, AMOLED mode and accent picker
- Listening statistics with top artists, top tracks and a listening clock
