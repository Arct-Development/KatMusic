<div align="center">

<img src="katmusic_logo.png" width="132" alt="KatMusic logo">

# KatMusic

**A native Android client for YouTube Music.** Ad-free streaming, offline downloads,
word-by-word lyrics, a parametric equaliser and an automix engine that blends one
track into the next.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4284F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

Designed and built by [ItzFallenMe](https://github.com/ItzFallenMe) · **v2.0.0**

</div>

---

## Contents

- [Features](#features)
- [Install](#install)
- [Build the APK](#build-the-apk)
- [How it works](#how-it-works)
- [Privacy](#privacy)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

## Features

### Playback

- Streams songs, albums, playlists, artist pages, mixes and podcasts from the YouTube
  Music catalogue
- Background playback with a media notification, lock-screen controls and Android Auto
- Queue control: play next, add to queue, reorder, remove, clear, plus shuffle, repeat
  one/all and an autoplay radio that keeps recommending while you listen
- **Automix** – crossfades between tracks with BPM and phase detection, a transition
  planner and WSOLA time stretching, all in the native audio engine
- **Parametric equaliser** with AutoEQ device profiles, plus a simpler band EQ for
  quick adjustments
- **USB exclusive output** for bit-perfect playback to an external DAC, and a direct
  internal output path that bypasses system mixing where the device allows it
- Loudness processing (normalisation and studio-clarity curves), playback speed from
  0.75× to 2×, and a sleep timer with quick presets
- **Listen Together** – host a room on the local Wi-Fi and let friends' phones follow
  your track, play/pause and seeks. Rooms are advertised over UDP and joined with a
  4-character code; there is no account or server involved

### Library and discovery

- Home feed, stats, playlists, liked songs, downloads and local files in one shell
- **Genre explorer** with genre radios and a weighted breakdown of what you actually
  listen to
- Discover, new releases, artist and album detail pages, search with live suggestions
- Smart Mix and AI picks: describe a mood and an OpenAI-compatible model of your choice
  curates it from your history; suggestions are matched against the catalogue before
  they are shown
- Podcasts, including video podcasts and per-show progress
- **Spotify Canvas**, **Last.fm scrobbling** and **Discord Rich Presence** as opt-in
  integrations

### Lyrics

- Word-by-word and line-synced lyrics, with several providers tried in order and a
  local fallback
- Translation, adjustable size/offset, and a share card renderer that turns a verse
  into an image

### Downloads

- One-transfer-at-a-time download queue with per-item cancel/retry, a storage bar and
  live progress that also shows outside the Downloads screen
- Wi-Fi-only downloads that actually pause and resume, and a chosen folder structure
  under `Music/KatMusic`
- FLAC/Opus/AAC tagging on save, lyrics and artwork written into the file

### Personalisation

- Material 3 theme built from your wallpaper or the current artwork, with light, dark
  and pure-black modes
- Liquid-glass surfaces, expressive motion, a floating navigation dock and a
  home-screen widget
- Bundled Figtree typeface with a weight-only scale, and a bold-font toggle

## Install

Download the APK from the [releases page](https://github.com/ItzFallenMe/KatMusic/releases)
and install it. Android 10 (API 29) or newer is required; Android 8 users should stay on
the 1.x builds.

## Build the APK

You need JDK 17, the Android SDK with **platform 37** and **build-tools 35+**, and the
NDK with CMake for the native audio engine.

```bash
git clone https://github.com/ItzFallenMe/KatMusic.git
cd KatMusic
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # signed release, needs keystore.properties
```

Release signing reads `keystore.properties` in the project root (or the matching
environment variables) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
A release build without a key is refused on purpose, so an update can never be signed
with a different key than the one already installed.

## How it works

- **UI** – Jetpack Compose with Material 3, a single `MainShell` (feed/stats/playlists
  pager) plus a Compose Navigation graph for detail screens. Screens share one plain
  top bar, a floating dock and a mini player.
- **State** – Hilt for dependency injection, `StateFlow` view models, DataStore for
  preferences and Room for the downloaded-track and history tables.
- **Playback** – Media3/ExoPlayer for YouTube and DASH streams, with the native C++
  engine (`app/src/main/cpp`) handling DSP, the parametric EQ, automix and USB audio
  output through Oboe.
- **Catalogue** – InnerTube for browse/search/streams, with signed-in account support
  for personalised shelves and library sync.
- **Lyrics** – provider chain with per-provider parsers (LRC, TTML, word timing) and a
  repository that caches results per track.

Source layout: `data/` (repositories and clients), `playback/` (player, DSP, downloads),
`ui/` (`screens`, `components`, `theme`), `cpp/` (native engine), `di/` (Hilt modules).

## Privacy

- No analytics and no tracking SDKs. Network traffic goes to the endpoints you use:
  the YouTube Music catalogue, the lyrics providers, and any integration you enable.
- API keys, cookies and session tokens are stored on the device only and are excluded
  from exported backups.
- The live-listener map is opt-in and off unless you enable it.

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[code of conduct](CODE_OF_CONDUCT.md). Please include logs and the build stamp shown in
Settings → About.

## Credits

- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) for stream
  extraction
- [Coil](https://coil-kt.github.io/coil/), [Oboe](https://github.com/google/oboe) and the
  AndroidX libraries this app is built on
- Lyrics data from LRCLIB, Apple Music, YouTube Music and the other providers listed in
  Settings → Appearance → Lyrics

## License

KatMusic is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

This program is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version.
