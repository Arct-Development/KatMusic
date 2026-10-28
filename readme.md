# KatMusic

<p align="center">
  <b>A native Android music client for YouTube Music, built with Kotlin and Jetpack Compose.</b><br/>
  Created by <a href="https://github.com/ItzFallenMe">ItzFallenMe</a>
</p>

KatMusic streams the YouTube Music catalogue without ads, keeps playing in the
background, downloads tracks for offline listening, follows along with synced
lyrics and scrobbles everything to Last.fm. The interface is a dark,
glass-and-gradient Material 3 Expressive design that recolours itself from the
album art you are listening to.

## Features

**Listening**
- Stream songs, albums, playlists, artists and mixes from YouTube Music
- Background playback with a media notification and lock-screen controls
- Queue management: play next, add to queue, reorder, remove
- Shuffle, repeat one/all, autoplay radio that keeps the queue going
- Sleep timer with quick presets
- Playback speed control
- Skip-silence and audio focus handling

**Sound**
- 10-band equalizer with device presets
- Bass boost and virtualizer
- High-quality Opus stream preference

**Lyrics**
- Millisecond-synced lyrics from LRCLIB
- Karaoke-style highlighting with automatic scrolling
- Playback-timing offset and text-size controls
- Plain-lyrics fallback when no sync is available

**Library**
- Liked songs, local playlists, listening history
- Automatic taste mixes ("On repeat", artist mix, rediscover) built from history
- Follow artists and jump straight to their page
- Offline downloads with `.lrc` sidecar files
- Recent searches

**Discovery**
- Home feed from the YouTube Music home page
- Charts, moods & genres, new releases
- Search with typeahead suggestions
- Album, playlist and artist pages
- Song radio / up-next recommendations

**Personalisation & stats**
- Artwork-derived dynamic colour theme, AMOLED mode, accent picker
- Listening stats: minutes, plays, top artists, top tracks, listening clock
- Shareable stats summary

**Last.fm**
- API-key login flow with browser authorisation
- Now-playing updates and scrobbling
- Offline scrobble queue that syncs later

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Requires JDK 17+ and Android SDK 35.

## Architecture

```
app/src/main/java/com/itzfallenme/katmusic
├── data/        InnerTube client, lyrics, models, library & settings storage
├── playback/    Media3 player controller, media session service, downloads, EQ
└── ui/          Compose theme, shared components, screens
```

- Kotlin + Jetpack Compose (Material 3)
- AndroidX Media3 / ExoPlayer with a custom stream-resolving data source, so
  fresh audio URLs are fetched lazily as each track loads
- OkHttp + kotlinx.serialization for the InnerTube and LRCLIB APIs
- JSON-backed local storage for the library and settings (no database needed)

## Credits

Design and feature inspiration:
[LastWave](https://github.com/Clash-Projects/LastWave-native) and
[SimpMusic](https://github.com/maxrave-dev/SimpMusic). KatMusic is an
independent implementation.

Lyrics by [LRCLIB](https://lrclib.net). Metadata and audio from YouTube Music.

## Disclaimer

KatMusic is an unofficial client and is not affiliated with YouTube or Google.
Use it with an account and in a region where streaming the catalogue is allowed.
