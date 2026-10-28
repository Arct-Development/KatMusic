# Changelog

All notable changes to KatMusic are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/).

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
