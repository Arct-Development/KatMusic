<div align="center">

# KatMusic

**A native Android client for YouTube Music.** Ad-free streaming, offline downloads,
synced lyrics, Last.fm scrobbling and an interface that recolours itself from the
album art you're listening to.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![CI](https://github.com/ItzFallenMe/KatMusic/actions/workflows/android.yml/badge.svg)](https://github.com/ItzFallenMe/KatMusic/actions/workflows/android.yml)

Created by [ItzFallenMe](https://github.com/ItzFallenMe)

</div>

---

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Install](#install)
- [Build from source](#build-from-source)
- [How it works](#how-it-works)
- [Privacy](#privacy)
- [FAQ](#faq)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

## Features

### Listen

- Stream songs, albums, playlists, artist pages and mixes from the YouTube Music catalogue
- Background playback with a media notification and lock-screen controls
- Queue control: play next, add to queue, reorder, remove, clear
- Shuffle, repeat one/all and autoplay radio that keeps recommending while you listen
- Sleep timer with quick presets
- Playback speed from 0.75× to 2×
- **Listen Together** – host a room on the local Wi-Fi and let friends' phones follow
  your track, play/pause and seeks. Rooms are advertised over UDP and joined with a
  4-character code (or the host's IP when discovery is blocked); there is no account or
  server involved
- **Discord Rich Presence** – the playing track, artist and progress bar on your Discord
  profile, published through Discord's own RPC service with no SDK or extra permissions

### Discover

- **Genre DNA** – your listening history resolved into a genre breakdown, weighted by
  play count (Last.fm tags when configured, the keyless iTunes catalogue otherwise)
- **Discovery radar** – unheard tracks pulled from the artists you play, interleaved and
  topped up from the catalogue when your history is thin
- One-tap genre radios and a Smart Mix generator that alternates between your seed artists
- **AI picks** – describe a mood and an OpenAI-compatible model of your choosing curates
  it from your history; suggestions are matched against the catalogue before they are
  shown, and your key never leaves the endpoint you configure

### Sound

- 10-band equalizer with the device's built-in presets
- **AutoEq headphone profiles** – paste or open any ParametricEQ, GraphicEQ or CSV
  export and the correction curve is mapped onto your bands
- **Streaming quality tiers** – Max, High (≤256 kbps) or Data saver (≤128 kbps); the
  resolver keeps the best Opus/AAC rendition under the cap and caches per tier
- Bass boost and virtualizer
- **Mono audio** for single-earbud listening, a **volume limit** as a percentage of the
  device maximum, and an optional **listening-break reminder** after 30–90 minutes
- Crossfade between tracks
- Skip-silence

### Lyrics

- **Word-by-word lyrics** from the [Better Lyrics](https://better-lyrics.boidu.dev/)
  API: Apple Music TTML carries a start and end time for every word, so the fill
  sweeps across each word exactly as it is sung, background vocals included
- Millisecond-synced line lyrics from [LRCLIB](https://lrclib.net) as the fallback
- **Word glow** – the lit copy of the line is masked to the sung extent with a feathered
  edge, the current word lifts and settles, and a halo rides the wipe; neighbouring lines
  fade and soften, and tapping a line seeks to it
- Frame-rate karaoke clock and self-following scroll that yields while you browse
- Romanised readings under every line, for Japanese, Korean, Hindi and other scripts
- **Share lyrics as an image** – the lines around the current moment are rendered with
  the album art into a share-ready card
- Timing offset, text-size controls and a switch to turn word sync off
- Plain-text fallback when no synchronised version exists

### Library

- Liked songs, playlists and listening history
- **Multi-select** – long-press liked, downloaded or history rows, then play, queue, like,
  add to a playlist or download the whole selection at once
- Automatic taste mixes built from your listening: *On repeat*, artist mix and rediscover
- Import public YouTube Music playlists from a link or id, or paste a plain
  `Artist - Title` list exported from Spotify or Apple Music and let KatMusic match it
- Offline downloads with matching `.lrc` lyric files, plus one-tap export into the
  phone's shared music library
- Music already on your device – scan, browse and play local files
- Backup and restore the whole library and settings as a single JSON file
- **Several accounts** – save more than one YouTube Music session and switch between
  them; the active one supplies your library and recommendations
- **Listening receipt** – your stats rendered as a tall, barcode-bottomed receipt image
- Recent searches

### Explore

- Home feed straight from YouTube Music
- Charts, moods & genres, new releases and podcasts
- Search with typeahead suggestions, albums, artists and playlists
- Song radio built from up-next recommendations

### Personalise

- Material 3 Expressive theme seeded from the current artwork: flat tonal surfaces,
  a pill dock, breathing artwork, morphing play/pause and animated lyrics - no
  glassmorphism, blur panes or rim highlights
- System / Light / Dark mode, Material You wallpaper colours, AMOLED black and a
  hand-picked accent palette
- Waveform seek bar with three flowing layers over the played part of the track
- Motion with a **Reduce motion** switch that snaps transitions and stops ambient drift
- **Search** with an animated hint, type filters (All / Songs / Albums / Artists /
  Playlists), a top-result card, recent searches and a browse grid
- Home-screen **now playing widget** with play/pause and skip
- **Settings search** – type a setting's name and land on the section that owns it, or
  browse Account, Appearance, Playback, Audio & safety, Lyrics, Downloads, AI, Discord,
  Last.fm and Data as their own pages
- Listening stats by period: Today, This week, This month or All time, with minutes
  compared against the previous window, a weekday strip and Songs / Artists / Albums
  rankings
- Shareable stats summary

### Integrations

- **YouTube Music account** – sign in to personalise the feed and sync your library
- **Last.fm** – authorise in the browser, send now-playing updates and scrobbles; failed
  scrobbles are queued and retried

## Screenshots

<!--
Drop PNGs into docs/screenshots/ and reference them here, for example:

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" />
  <img src="docs/screenshots/player.png" width="30%" />
  <img src="docs/screenshots/lyrics.png" width="30%" />
</p>
-->

Screenshots are welcome from contributors – add them to `docs/screenshots/` and open a PR.

## Install

KatMusic is built from source at the moment. Release APKs will be attached to GitHub
releases once the first signed build ships.

1. Download `app-debug.apk` from the CI artifacts of the latest `main` build (or build it yourself).
2. Allow installing apps from unknown sources for your file manager or browser.
3. Open the APK and install. Android 8.0 (API 26) or newer is required.

> KatMusic streams from YouTube Music. It is an unofficial client and is not affiliated
> with YouTube or Google.

## Build from source

Requirements:

- JDK 17 or newer
- Android SDK platform 35 and build tools 35.0.0
- `ANDROID_HOME` pointing at your SDK, or a `local.properties` with `sdk.dir=...`

```bash
git clone https://github.com/ItzFallenMe/KatMusic.git
cd KatMusic
./gradlew :app:assembleDebug          # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # parsing unit tests
./gradlew :app:assembleRelease        # signed APK → app/build/outputs/apk/release/
```

Release builds are signed from `keystore.properties` at the repository root:

```properties
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

The same four keys can be supplied as environment variables (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`) so CI can sign without committing secrets. When neither is
present the release build falls back to the debug key, which installs fine on a phone but
cannot be published. Keep the keystore backed up: Android refuses updates signed with a
different key.

Open the folder in Android Studio for the full IDE experience; no extra setup is needed.

## How it works

```
app/src/main/java/com/itzfallenme/katmusic
├── data/        InnerTube client, stream resolution, JSON parsing, lyrics, storage
├── playback/    Media3 controller, media session service, downloads, equalizer
└── ui/          Theme engine, shared components, screens
```

| Layer | Implementation |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 / ExoPlayer with a custom `ResolvingDataSource` that fetches a fresh audio URL as each track loads |
| Stream resolution | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) handles YouTube's signed/throttled URLs, every candidate is probed before playback, and the InnerTube player endpoint stays as a fallback |
| Networking | OkHttp + kotlinx.serialization |
| Lyrics | LRCLIB public API |
| Storage | JSON files for library and settings, SharedPreferences for the account session |
| Images | Coil + AndroidX Palette for artwork colours |

Notable implementation details:

- **Stream resolution** happens inside the player's data source, so queue items hold
  only video ids and never expire while sitting in the queue.
- **Playlists, likes, history and downloads** are plain serialisable models. There is no
  database, which keeps backups trivial and the APK small.
- **Theming** converts the dominant artwork colour into a full dark palette in HSL space.
- **Scrobbling** queues failed calls locally and retries them from Settings.

## Privacy

KatMusic has no analytics, no crash reporting and no KatMusic server. Your library,
settings and downloads stay on the device. Network calls go only to:

- `music.youtube.com` / `www.youtube.com` (catalogue, playback)
- `lrclib.net` (lyrics)
- `ws.audioscrobbler.com` (only if you connect Last.fm)

A signed-in YouTube Music session is stored in the app's private storage and is cleared
when you sign out. See [SECURITY.md](SECURITY.md) for details.

## FAQ

**Why does playback stop after a while?**
YouTube rotates stream URLs and occasionally rate-limits clients. Skip to the next track
or restart playback; the resolver fetches a fresh URL each time.

**Lyrics are missing or out of sync.**
LRCLIB is community-maintained. Adjust the timing offset in Settings → Lyrics, or open
the track on LRCLIB and contribute a sync.

**Can I import Spotify playlists?**
Not directly: Spotify's API needs its own developer keys. Convert the playlist to YouTube
Music with any converter, then paste that link into Settings → Data → Import a playlist.

**Does it work without an account?**
Yes. Signing in only personalises recommendations and unlocks your YouTube Music library.

**Is my data backed up?**
The library and settings are plain JSON inside the app sandbox, so `adb backup` or a
rooted copy works. Android's auto-backup is enabled by default.

## Contributing

Issues and pull requests are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md), and
please read the [Code of Conduct](CODE_OF_CONDUCT.md). Security reports go through
[SECURITY.md](SECURITY.md).

## Roadmap

Planned work, roughly in priority order. Feature requests are welcome – open an issue.

- [x] Now-playing home-screen widget (insight and playlist widgets still planned)
- [x] Genre DNA and a discovery feed built from listening stats
- [x] Word-by-word lyrics and romanisation
- [x] Share lyrics as an image
- [x] Discord Rich Presence
- [x] Multi-account switching for YouTube Music
- [x] Android Auto and Android TV entry points
- [x] AI song suggestions with a user-supplied API key
- [ ] Google Cast support
- [ ] Listen Together (synced rooms)
- [ ] Video playback with captions and audio-track selection
- [x] MediaStore export of downloads with title, artist and album tags
- [ ] Embedded cover art and ID3 tagging inside downloaded files
- [x] Local file playback and a device-music browser
- [x] Backup / restore of the whole library
- [x] AutoEq headphone profiles for the equalizer
- [x] Spotify / Apple Music song lists (pasted, matched automatically)
- [ ] Spotify / Apple Music playlists read directly through their APIs

## Credits

KatMusic is an independent implementation inspired by the feature sets and interface
ideas of two excellent open-source projects:

- [LastWave](https://github.com/Clash-Projects/LastWave-native) by Clash Projects – the
  tonal palette builder and motion rhythm are adapted from LastWave (GPL-3.0), as are the
  TTML lyrics pipeline, the karaoke surface (`KaraokeLyrics.kt`, ported from its
  `ModernLyricsView`), the Discord RPC transport (including the three
  `com.discord.socialsdk.rpc` AIDL stubs) and the genre resolver. A read-only clone of the
  upstream project is kept at `lastwave/` for reference and is git-ignored.
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev

Lyrics by [LRCLIB](https://lrclib.net). Audio and metadata by YouTube Music. Stream
signing by [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). Icons from
Material Symbols. Built with [Jetpack Compose](https://developer.android.com/jetpack/compose).

## License

KatMusic is released under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 ItzFallenMe

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version.
```
