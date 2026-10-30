# Changelog

All notable changes to KatMusic are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/).

## [2.0.0] - 2026-10-30

### Changed

- **New base: the native audio engine and interface now live in KatMusic.** Playback runs
  through the native audio engine with the parametric equaliser, crossfade/automix planner,
  USB exclusive output and loudness processing; lyrics, downloads, discovery, genres,
  artists, albums, playlists, podcasts, podcasts video, jam sessions and the YouTube
  account stack are all on the new code paths
- **Simpler chrome.** The full-bleed header that appeared to hang off the top of the screen
  is gone: screens now open on a plain top bar painted in the background colour, with the
  status bar padded out and no panel, rounding or glass. Search uses the same flat bar
- Settings reordered around what people open it for: **Playback & UI** first (Audio, then
  Appearance, then Library), then **Services** (YouTube, Spotify, Last.fm, and the module
  manager last), then **System & About**
- Typography moved to Figtree with weight-only axes, so the bold-font toggle keeps working
  and heading tracking no longer silently falls back to body weight

### Added

- Native audio engine, parametric EQ and automix (crossfade, transition planner, WSOLA
  time stretching) with unit tests under `app/src/main/cpp/tests`
- Genre explorer, artist/album/playlist detail stacks, podcast and video surfaces, Spotify
  Canvas and Jam sessions

## [Unreleased]

### Added

- **Download queue** with one transfer at a time: every tap joins a visible queue instead
  of opening another parallel socket, and each item shows queued / waiting for Wi-Fi /
  running with progress. Items can be cancelled mid-transfer or retried after a failure,
  and a failed item no longer disappears without a word
- **Wi-Fi-only downloads now actually apply.** The setting existed but nothing read it; a
  queued download pauses on mobile data, says so, resumes on its own when Wi-Fi returns,
  and can be released with one tap
- Downloads screen rebuilt around that queue: storage bar with used and free space, a
  Retry-all action, per-item cancel/retry, a confirmation before deleting everything, and
  live progress (the old screen read the progress map without observing it, so the bars
  never moved)
- **A shared header** on every screen: title, optional subtitle and tonal circular action
  buttons, padded for the status bar. Home and Library were rebuilt around it: the
  wordmark and the library title are fixed headers now, with the greeting, quick picks,
  stats card and library tabs scrolling underneath instead of drifting away with the
  content
- (2.0.0 supersedes the earlier full-bleed, bottom-rounded header with a plain flat bar)
- **Rubber-band overscroll** on the Home, Explore, Library, Search, Discover, Detail,
  Stats, Friends, Local music and Downloads lists, ported from KatMusic; the platform's
  own stretch is turned off so only one overscroll system runs at a time
- Offline Home: the last successful feed is saved and restored on the next launch, with
  an "Offline — showing your saved feed" row and a retry action

### Changed

- Library and settings writes are coalesced onto one background thread. Dragging a slider
  or a like toggle used to serialise and rewrite the whole JSON file on the main thread
  for every change; the UI now updates immediately and the file is written once after a
  short quiet period, and flushed when the app goes to the background
- Download records whose file has vanished (cleared data, deleted elsewhere) are pruned at
  startup, instead of leaving a row that plays nothing
- Downloads respect the streaming-quality tier, so Data saver downloads data-saver audio

### Fixed

- **Crash when opening a queue that contains the same song twice.** Queue rows were keyed
  by `id + indexOf(track)`, and `indexOf` returns the first match for every copy, so two
  rows shared one key and Compose threw "Key was already used" the moment the queue
  screen opened. Rows are now keyed by position, and tapping or removing a row acts on
  that row rather than on the first copy of the song
- The same duplicate-key crash in the Home shelves, shelf card rows, Explore genre chips
  and playlist track lists (two items with the same title / id produced one key)
- Crash on Share from a track row when the popup's context was not an Activity
  ("Calling startActivity() from outside of an Activity context requires the
  FLAG_ACTIVITY_NEW_TASK flag"): external intents now add that flag when needed and turn a
  missing handler into a toast instead of an exception
- The service locator could be built twice from different threads (activity, media
  service, widget action, sleep-timer receiver); a second caller slipping past the guard
  while the first was still assigning fields crashed on an uninitialised service. Init is
  now locked and completes before anything reads it
- Crash reports now include the last log lines, and release builds keep a small
  warnings-and-errors log, so a crash on a shipped build is diagnosable from
  Settings → Data → Debug log

## [1.8.0] – 2026-10-29

### Added

- **Reduce motion** (Settings → Appearance): snaps every Motion-driven transition and
  stops the ambient drift, the breathing album art, the spinning vinyl and the animated
  playing bars
- Search was rebuilt around the SimpMusic layout: an animated hint that cycles through
  songs/albums/artists/playlists, a tonal search bar that lifts on focus, All / Songs /
  Albums / Artists / Playlists filters, a large **Top result** card with like and play,
  recent searches and a gradient browse grid

### Changed

- Fixed the continuous-curvature shape: corners larger than half the shortest edge used
  to produce crossing control points, which is why chips and many buttons rendered almost
  square. The radius is clamped before the path is built, so pills, chips and cards all
  curve correctly at every size
- More colour separation: wider tonal steps between surface containers, a brighter
  primary, and cards are now opaque instead of a translucent tint over the backdrop
- More motion: hero artwork parallax on Home, count-up numbers and growing bars in Stats,
  album artwork and title transitions in the player, a pop on the like button, a bouncier
  dock icon, mini-player track fade and staggered settings cards

## [1.7.0] – 2026-10-29

### Added

- **Listen Together** (Settings → Listen together): host a room on the local Wi-Fi and
  friends follow your playback. The host's phone leads track changes, play/pause and
  seeks; guests get the queue, artwork and position over a small JSON line protocol on
  TCP, while rooms are advertised with a UDP broadcast and joined with a 4-character
  code. When discovery is blocked by the router, the host screen shows its IP so guests
  can type it directly. No account, server or API key is involved, and leaving the room
  closes every socket

## [1.6.0] – 2026-10-29

### Added

- Settings search and drill-down: the root is now a searchable list of sections, and each
  section opens its own page (Account, Appearance, Playback, Audio & safety, Lyrics,
  Downloads, AI, Discord, Last.fm, Data). Typing filters every row by name, description or
  keyword and jumps straight to the section that owns it
- Listening stats with periods: Today / This week / This month / All time, minutes compared
  with the previous window, a weekday strip with the busiest day, and Songs / Artists /
  Albums rankings
- Audio & safety options: mono downmix for single-earbud listening, a volume limit as a
  percentage of the device maximum, and an optional break reminder after 30–90 minutes of
  continuous playback
- Library multi-select: long-press any liked, downloaded or history row to select several
  tracks, then play, play next, queue, like, add to a playlist or download them in one go
- Streaming quality tiers in Settings → Playback: Max (best Opus/AAC rendition), High
  (≤256 kbps) and Data saver (≤128 kbps). The resolver filters renditions by the cap and
  caches each tier separately, so switching quality never serves a stale stream

### Changed

- Material 3 Expressive refresh: continuous-curvature (squircle) surfaces replace circular
  rounded corners, the bottom dock is a floating tonal pill whose active item grows and
  morphs, player transport uses the elastic push row, selection chips morph shape as well
  as colour, stats and quick-pick rows use segmented controls, and loading/progress now use
  shape-morph and wavy indicators
- The KatMusic logo is now the launcher icon (adaptive, black background) and appears in
  Settings → About
- Housekeeping: removed the committed crash log, the local chat transcript, the unused
  vector launcher art and the empty scratch folders

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
