# Module layout and the shared Kotlin/Compose slice

This document records how the repository is split between the Android build, the
shared Kotlin Multiplatform module, and the Flutter desktop app, and which parts
intentionally stay platform-specific.

## Module layout

```
:shared     Kotlin Multiplatform (androidTarget + jvm("desktop") + iOS targets)
  commonMain   UI, design system, models, queue logic, search, settings contracts, platform expect declarations
  androidMain  Android actuals (system bars, dynamic colour) + shared AAR for :app
  desktopMain  JVM actuals (no system bars / no wallpaper palette); see "Leftover JVM target" below
:app        Android application (consumes :shared)
audio/…     Android USB audio driver (platform-specific)
desktop/    Flutter desktop app (Windows, Linux, macOS) — not a Gradle module
```

`desktop/` is built with Flutter (`flutter build windows|linux|macos`), has no
Gradle wiring, and shares no source with `:shared` or `:app`. `settings.gradle.kts`
includes only `:app`, `:shared` and `:audio:decent-usb-audio-driver`.

## How the desktop got here

The desktop app was first a Flutter project, was briefly replaced by a Kotlin
Compose Desktop module (`:desktop`) that rendered the shared UI, and is now the
Flutter app from release 2.6.4 again. The Compose desktop module, its
`javax.sound`/JNA/NewPipe-fork stack and its Gradle packaging tasks
(`packageMsi`, `packageDmg`, `packageDeb`, `createDistributable`) no longer
exist. `:shared` stays because the Android app depends on it.

Desktop-specific documentation now lives in [desktop/README.md](../desktop/README.md)
and [desktop-feature-parity.md](desktop-feature-parity.md).

## What is shared Kotlin/Compose (`:shared`, `commonMain`)

- **Design system** — `ui/theme`: `KatMusicTheme`, `ThemeUiState`, Figtree type
  scale (bundled in `composeResources/font`), shape scale, `PaletteBuilder`
  (seeded, monochrome and KatMusic signature palettes).
- **Motion** — `ui/components/MotionSpec` (springs, transitions, press-scale)
  and `expressiveBounceClickable`.
- **Adaptive layout** — `WindowSizeClass` / `rememberWindowSizeClass` read the
  composition window size instead of Android's `LocalConfiguration`.
- **Theme tokens** — `ThemeMode`, `AccentMode`, `ThemeUiState`, consumed by
  `:app`.
- **Models** — `shared/models/Track` (id, title, artist, album, duration,
  source URI, artwork URI, track/year, local flag).
- **Queue logic** — `PlayerController` + `PlayerState` + `QueueNavigator`:
  shuffle, repeat off/all/one, explicit-next vs auto-advance, previous-track
  restart threshold and end-of-queue stop.
- **Search** — `LibrarySearch` (token based, diacritic-insensitive).
- **Grouping** — `LibraryGrouping` (albums and artists from a track list:
  album-artist keys, disc/track ordering, duration sums, unknown buckets).
- **Lyrics** — `lyrics/Lyrics.kt`: `Lyrics`/`LyricsLine` model, `LrcParser`
  (multiple timestamps per line, `[mm:ss.xxx]`, metadata tags, `[offset:]`,
  plain-text fallback, current-line lookup) and the `LyricsProvider` contract.
  Android implements it over the app's existing provider chain
  (`AndroidLyricsProvider` → `LyricsRepository`).
- **Settings** — `AppSettings`, `SettingsStore` contract and
  `AppSettings.toThemeUiState()`.
- **UI shell and screens** — `shared/ui`: responsive `KatMusicApp` (bottom bar
  on compact, rail on wide), Navigation-Compose graph with shared transitions,
  `PlayerDock`, `LibraryScreen`, `SearchScreen`, `NowPlayingScreen`,
  `SettingsScreen`, `Artwork` (Coil 3, multiplatform) and `TrackRow`.

## What stays platform-specific (and why)

| Area | Android (`:app`) | Desktop (`desktop/`, Flutter) |
| --- | --- | --- |
| Playback | Media3/ExoPlayer, MediaSession, audio focus, FFmpeg decoder, USB exclusive output, native DSP/AutoMix engine (`app/src/main/cpp`) | `media_kit` / libmpv, crossfade and AutoMix in Dart |
| Library source | MediaStore, download database (Room), MediaStore permission flow | Local folders, tag reading, downloads under `features/downloads` |
| Settings storage | DataStore | `shared_preferences`, `flutter_secure_storage`, local SQLite |
| System integration | home-screen widgets (Glance), Android Auto, notifications, share intents, splash video, dynamic colour/wallpaper, app locale | window management, system tray, global hotkeys, MPRIS (Linux), SMTC (Windows), Discord presence |
| UI | Jetpack Compose with the shared design system | Flutter (Fluent shell, KatMusic design tokens) |

Audio playback, Media3, native processing, file access and system integrations
were deliberately **not** moved into common code: none of them exist on the
other platform, and shimming them would mean removing working behaviour.

## Liquid glass

`ui/theme/LiquidGlass.kt` in `:shared` provides the portable glass material used
by the shared shell. Android keeps Kyant Backdrop for true background
refraction. The Flutter desktop app does not use this file: it draws its own
glass with the `liquid_glass_widgets` package on the title bar, navigation rail
and player dock, and falls back to an opaque tonal surface when the
**Liquid Glass chrome** setting is off.

## Migration status of the Android UI

- The **design system, motion, adaptive layout, components and navigation
  primitives Android uses are the shared ones** — `:app` no longer contains its
  own `Theme`, `Type`, `Shape`, `PaletteBuilder`, `MotionSpec` or
  `AdaptiveLayout`; `:app` compiles against `:shared`.
- **Not yet ported:** Android's individual feature screens (Home feed, artist,
  album, playlist, jam, lyrics, downloads, podcast, settings sub-pages) still
  live in `:app` because they are bound to Hilt view models, Media3 controller
  state, Room/DataStore repositories and Android resources. Moving them means
  converting each view model to a constructor-injected shared state holder and
  replacing resource lookups with shared strings; that is an incremental port.
- **Android runs the shared UI too.** `AndroidMediaLibrary`
  (`shared/src/androidMain`) is a real MediaStore library, `AndroidSettingsStore`
  persists `AppSettings` in SharedPreferences, `Media3PlayerController` (`:app`)
  implements the shared `PlayerController` on top of the process-wide
  `MusicPlayer`, and `SharedShellActivity` hosts `KatMusicApp`, reachable at
  `katmusic://shared`. The main Android UI stays the launcher.

### Shared slice: downloads, playlists, home feed

The shared shell has three feature slices, each a contract in
`shared/.../shared/data`. Android bridges each to the app's real data:

- **Downloads** (`DownloadsRepository`) — the app's Room download table
  (`DownloadedTrackDao`); deletes go through `TrackDownloadManager` (MediaStore
  entries and sidecar `.lrc` included).
- **Playlists** (`PlaylistsRepository`) — the app's `saved_playlists` Room table
  (mode `custom`), so a playlist made in the shell appears in the main Android
  app and vice versa.
- **Home feed** (`HomeFeedRepository` + `HomeCard`/`HomeSection` models) —
  `StreamApi.fetchHomeShelves`/`searchSongs`, so the shell's Home shows the real
  InnerTube feed and plays streamed cards through the app's BotGuard/Media3
  pipeline.

## Leftover JVM target in `:shared`

`:shared` still declares `jvm("desktop")` with a single `desktopMain` file
(`PlatformExpectations.desktop.kt`) and the `compose.desktop.currentOs`
dependency. Nothing consumes that target any more: the desktop app is Flutter.
It is kept for now because `:shared` is an Android dependency and `commonTest`
runs through `:shared:desktopTest`. Removing it is a safe follow-up once those
tests run on another target, but it touches the module the APK depends on, so it
was not done as part of the desktop swap.

## Compatibility decisions

- Compose Multiplatform `1.12.2` with Kotlin `2.4.10` (the compose compiler
  ships with the Kotlin plugin).
- Navigation Compose multiplatform `2.10.0` and Lifecycle multiplatform
  `2.11.0` for the shared navigation graph.
- Coil `3.6.3` (multiplatform) in shared code; `:app` keeps Coil 2 for the
  screens that have not moved yet.
- Figtree is bundled in the shared module's Compose resources, so the shared
  theme does not depend on Android `R.font`.
- `:shared` uses `com.android.library` + `androidTarget`. AGP 9 prints a
  deprecation warning recommending `com.android.kotlin.multiplatform.library`;
  the current setup keeps `compose.resources` and the existing Android DSL (the
  project pins `android.newDsl=false`) working. Switching plugins is a
  recommended follow-up.

## iOS (deferred)

An iOS host (`iosApp` Xcode project plus `iosMain` actuals) was prototyped on the
shared UI, but it is not part of this delivery. The `iosApp/` directory, the
`iosMain` sources and the iOS workflow are ignored by git (see `.gitignore`).

## Verification

Commands for each part of the build. They were **not** run as part of the
desktop swap: the environment used for it had no Android SDK or Flutter
toolchain and could not reach the Maven or Flutter hosts. Run them locally or in
CI before releasing.

```bash
# Android + shared (JDK 17, Android SDK)
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:desktopTest
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug

# Desktop (Flutter 3.47+)
cd desktop
flutter pub get
dart tool/obfuscate_secrets.dart
flutter analyze --no-fatal-infos
flutter test --exclude-tags golden,live
flutter build windows --release   # or: linux, macos
```

`.github/workflows/desktop.yml` runs the Flutter steps on Windows, Linux and
macOS. `.github/workflows/release.yml` publishes desktop, Android or both from
one dispatch; desktop and Android version independently, so desktop packages go
to a `desktop-v<version>` release and Android packages to `<version>`.
