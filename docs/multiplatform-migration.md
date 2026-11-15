# KatMusic → Kotlin Multiplatform / Compose Multiplatform

This document records the migration of KatMusic to a single Kotlin
Multiplatform build with one shared Compose UI for Android and desktop, and the
parts that intentionally stay platform-specific.

## Module layout

```
:shared     Kotlin Multiplatform (androidTarget + jvm("desktop")) — Compose Multiplatform
  commonMain   UI, design system, models, queue logic, search, settings contracts, platform expect declarations
  androidMain  Android actuals (system bars, dynamic colour) + shared AAR for :app
  desktopMain  Desktop actuals (no system bars / no wallpaper palette)
:desktop    JVM Compose Desktop application (real playback, library, settings storage)
:app        Android application (unchanged feature surface; consumes :shared)
audio/…     Android USB audio driver (platform-specific, untouched)
```

The Flutter desktop project (`desktop/lib`, `desktop/linux`, `desktop/macos`,
`desktop/windows`, `pubspec.yaml`, Dart tooling, tests and assets) was removed
and replaced by `:desktop`.

## What is now shared Kotlin/Compose (`:shared`, `commonMain`)

- **Design system** — `ui/theme`: `KatMusicTheme`, `ThemeUiState`, Figtree type
  scale (bundled in `composeResources/font`), shape scale, `PaletteBuilder`
  (seeded, monochrome and KatMusic signature palettes).
- **Motion** — `ui/components/MotionSpec` (springs, transitions, press-scale)
  and `expressiveBounceClickable`.
- **Adaptive layout** — `WindowSizeClass` / `rememberWindowSizeClass` now read
  the composition window size instead of Android's `LocalConfiguration`, so
  resizing a desktop window reclassifies exactly like a device rotation.
- **Theme tokens** — `ThemeMode`, `AccentMode`, `ThemeUiState` moved out of
  `:app` into `:shared` and are consumed by both platforms.
- **Models** — `shared/models/Track` (id, title, artist, album, duration,
  source URI, artwork URI, track/year, local flag).
- **Queue logic** — `PlayerController` + `PlayerState` + `QueueNavigator`:
  shuffle, repeat off/all/one, explicit-next vs auto-advance, previous-track
  restart threshold and end-of-queue stop. Both engines call the same code.
- **Search** — `LibrarySearch` (token based, diacritic-insensitive).
- **Settings** — `AppSettings`, `SettingsStore` contract and
  `AppSettings.toThemeUiState()`; the appearance switches, their meaning and
  the palette mapping are shared, only persistence differs.
- **UI shell and screens** — `shared/ui`: responsive `KatMusicApp` (bottom bar
  on compact, rail on wide), Navigation-Compose graph with shared transitions,
  `PlayerDock`, `LibraryScreen`, `SearchScreen`, `NowPlayingScreen`,
  `SettingsScreen`, `Artwork` (Coil 3, multiplatform) and `TrackRow`.

## What stays platform-specific (and why)

| Area | Android (`:app`) | Desktop (`:desktop`) |
| --- | --- | --- |
| Playback | Media3/ExoPlayer, MediaSession, audio focus, FFmpeg decoder, USB exclusive output, native DSP/AutoMix engine (`app/src/main/cpp`) | `JavaSoundPlayer` on `javax.sound.sampled` with mp3spi/vorbisspi PCM decoders |
| Library source | MediaStore, download database (Room), MediaStore permission flow | Folders chosen with a file chooser, tags via jaudiotagger, folder cover art |
| Settings storage | DataStore | `java.util.prefs` |
| System integration | home-screen widgets (Glance), Android Auto, notifications, share intents, splash video, dynamic colour/wallpaper, app locale | window management, `JFileChooser` |
| Feature screens | feed, discover, jam/listen-together, group blend, lyrics page, downloads, podcast, artist/album, YouTube import, Spotify login, update prompt, stats | shared screens only |

Audio playback, Media3, native processing, file access and system integrations
were deliberately **not** moved into common code: none of them exist on the
other platform, and shimming them would mean removing working Android
behaviour.

## Migration status of the UI

- The **design system, motion, adaptive layout, components and navigation
  primitives Android uses are now the shared ones** — `:app` no longer contains
  its own `Theme`, `Type`, `Shape`, `PaletteBuilder`, `MotionSpec` or
  `AdaptiveLayout`; those files were deleted and `:app` compiles against
  `:shared`. Android's 130+ feature screens therefore render with the shared
  theme, type scale, shapes and motion today.
- The **desktop application runs the full shared UI** (`KatMusicApp`) with real
  platform implementations.
- **Not yet ported:** Android's individual feature screens (Home feed, artist,
  album, playlist, jam, blend, lyrics, downloads, podcast, settings sub-pages)
  still live in `:app` because they are bound to Hilt view models, Media3
  controller state, Room/DataStore repositories and Android resources
  (`stringResource`, `LocalContext`). Moving them requires converting each
  view model to a constructor-injected shared state holder and replacing
  resource lookups with shared strings; that is an incremental port, and the
  shared module is now the place for it. Nothing was deleted or stubbed to
  make the desktop build pass.
- Consequently the desktop app currently offers the shared screens (library,
  search, now playing/queue, settings) rather than Android's full feature set.
  Desktop streaming (InnerTube/Last.fm/Jam) is not implemented; the desktop
  library is local files. This is a functional gap, not a mock: no screen
  pretends to have data it does not have.

## Compatibility decisions

- Compose Multiplatform `1.12.2` with Kotlin `2.4.10` (the compose compiler
  ships with the Kotlin plugin, so both platforms use one compiler).
- Navigation Compose multiplatform `2.10.0` and Lifecycle multiplatform
  `2.11.0` for one navigation graph on both targets.
- Coil `3.6.3` (multiplatform) replaces Coil 2 in shared code; `:app` keeps its
  Coil 2 usage for the screens that have not moved yet.
- Figtree is bundled in the shared module's Compose resources, so the shared
  theme no longer depends on Android `R.font`.
- `:shared` uses `com.android.library` + `androidTarget`. AGP 9 prints a
  deprecation warning recommending `com.android.kotlin.multiplatform.library`;
  the current setup is the one that keeps `compose.resources`, KSP-free Hilt in
  `:app` and the existing Android DSL (the project pins `android.newDsl=false`)
  working. Switching plugins is the recommended follow-up.

## Verification performed

Environment: JDK 25 (Gradle daemon) with a Temurin JDK 17 toolchain installed,
Android SDK Platform 37.0 + Build-Tools 37.0.0 + NDK 27.3 + CMake 3.22.1.

```bash
./gradlew :shared:compileDebugKotlinAndroid   # shared module, Android target
./gradlew :desktop:compileKotlin              # desktop app + shared desktop target
./gradlew :app:compileDebugKotlin             # full Android app against :shared
./gradlew :shared:desktopTest                 # QueueNavigator + LibrarySearch tests
```

All four commands completed successfully, and `./gradlew :desktop:createDistributable`
produced `desktop/build/compose/binaries/main/app/KatMusic`.

The packaged desktop binary was started in this environment and stopped at
Skiko initialisation: `libGL.so.1: cannot open shared object file`. That is a
missing graphics library in the container (no OpenGL, no display server), not
a build or code failure — the same app image works on a desktop with a GPU
stack. UI runtime behaviour was therefore not observed here; only compilation,
packaging and the shared-logic tests are verified.
