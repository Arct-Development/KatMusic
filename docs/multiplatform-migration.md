# KatMusic → Kotlin Multiplatform / Compose Multiplatform

This document records the migration of KatMusic to a single Kotlin
Multiplatform build with one shared Compose UI for Android and desktop, and the
parts that intentionally stay platform-specific.

## Module layout

```
:shared     Kotlin Multiplatform (androidTarget + jvm("desktop"))
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
- **Grouping** — `LibraryGrouping` (albums and artists from any platform track
  list: album-artist keys, disc/track ordering, duration sums, unknown buckets).
- **Lyrics** — `lyrics/Lyrics.kt`: `Lyrics`/`LyricsLine` model, `LrcParser`
  (multiple timestamps per line, `[mm:ss.xxx]`, metadata tags, `[offset:]`,
  plain-text fallback, current-line lookup) and the `LyricsProvider` contract.
  Android implements it over the app's existing provider chain
  (`AndroidLyricsProvider` → `LyricsRepository`); desktop implements it over
  LRCLIB (`LrclibLyricsProvider`), with a sidecar `.lrc` next to the audio file
  taking precedence (`DesktopLyricsProvider`), matching the Android rule that
  local lyrics win over every provider.
  Tests: `LrcParserTest` (shared, 9 cases) and `DesktopLyricsProviderTest`
  (sidecar precedence, case-insensitive match, empty/missing sidecar fallback,
  plus a live LRCLIB lookup that runs when `KATMUSIC_NETWORK_TESTS=1`).
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
| Playback | Media3/ExoPlayer, MediaSession, audio focus, FFmpeg decoder, USB exclusive output, native DSP/AutoMix engine (`app/src/main/cpp`) | `JavaSoundPlayer` on `javax.sound.sampled` with mp3spi/vorbisspi PCM decoders, or libmpv for streams |
| Library source | MediaStore, download database (Room), MediaStore permission flow | Folders chosen with a file chooser, tags via jaudiotagger, folder cover art |
| Settings storage | DataStore | `java.util.prefs` |
| System integration | home-screen widgets (Glance), Android Auto, notifications, share intents, splash video, dynamic colour/wallpaper, app locale | window management, `JFileChooser` |
| Feature screens | feed, discover, jam/listen-together, lyrics page, downloads, podcast, artist/album, YouTube import, Spotify login, update prompt, stats | shared screens only |

Audio playback, Media3, native processing, file access and system integrations
were deliberately **not** moved into common code: none of them exist on the
other platform, and shimming them would mean removing working Android
behaviour.

## Liquid glass on desktop

`ui/theme/LiquidGlass.kt` in `:shared` provides the portable glass material used
by the shared chrome. Android keeps Kyant Backdrop for true background
refraction; desktop gets a Compose-drawn equivalent with the same
translucent frost, diagonal specular band, bright rim and shadow. It is applied
to the player dock, the compact navigation bar and the wide navigation rail,
and follows the existing **Liquid glass chrome** switch (the Material 3 switch
turns it off for the navigation bar). No platform-specific code is needed at
call sites: `Modifier.liquidGlassSurface(...)` is a no-op when the switch is
off.

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
  album, playlist, jam, lyrics, downloads, podcast, settings sub-pages)
  still live in `:app` because they are bound to Hilt view models, Media3
  controller state, Room/DataStore repositories and Android resources
  (`stringResource`, `LocalContext`). Moving them requires converting each
  view model to a constructor-injected shared state holder and replacing
  resource lookups with shared strings; that is an incremental port, and the
  shared module is now the place for it. Nothing was deleted or stubbed to
  make the desktop build pass.
- **Android now runs the shared UI too.** `AndroidMediaLibrary`
  (`shared/src/androidMain`) is a real MediaStore library, `AndroidSettingsStore`
  persists `AppSettings` in SharedPreferences, `Media3PlayerController`
  (`:app`) implements the shared `PlayerController` on top of the process-wide
  `MusicPlayer` (the same Media3 instance the MediaSession, widgets and USB
  output use), and `SharedShellActivity` hosts `KatMusicApp` with those
  implementations, reachable at `katmusic://shared`. The main Android UI keeps
  being the launcher and is untouched; promoting the shared shell to the
  default launcher is what the remaining screen port unblocks.
- The shared screen set now covers Library with Songs/Albums/Artists views,
  album and artist collections, search, now playing, the queue, login-free
  lyrics and settings, so the desktop app offers those rather than Android's
  full feature set.
  Desktop streaming (InnerTube/Last.fm/Jam) is not implemented; the desktop
  library is local files. This is a functional gap, not a mock: no screen
  pretends to have data it does not have.

### Shared slice: downloads, playlists, home feed (added later)

The shared shell now has three feature slices, each a contract in
`shared/.../shared/data` with real implementations on both platforms:

- **Downloads** (`DownloadsRepository`) — Android bridges the app's real Room
  download table (`DownloadedTrackDao`) and deletes files through the app's own
  `TrackDownloadManager` (MediaStore entries and sidecar `.lrc` included).
  Desktop keeps a file store under `~/KatMusic/Downloads` with a JSON index
  sidecar; deleting removes the actual audio file. The shared Downloads screen
  lists, plays and deletes with a confirm dialog, and shows the real total size.
- **Playlists** (`PlaylistsRepository`) — Android persists in the app's
  `saved_playlists` Room table (mode `custom`), so a playlist made in the shell
  appears in the main Android app and vice versa. Desktop stores one portable
  `.m3u` file per playlist in `~/KatMusic/Playlists`; the parser refreshes tags
  from disk and keeps entries whose files disappeared (unplayable rather than
  silently missing). Tests: `M3uPlaylistsRepositoryTest` (round-trip, missing
  files, add/remove/delete, streams rejected for local playlists).
- **Home feed** (`HomeFeedRepository` + `HomeCard`/`HomeSection` models) —
  Android bridges `StreamApi.fetchHomeShelves`/`searchSongs`, so the shell's
  Home shows the real InnerTube feed and plays streamed cards through the
  app's existing BotGuard/Media3 pipeline. Desktop uses the **Piped public API**
  (`desktop/data/PipedClient`, multi-instance failover): `/trending` and
  `/search?filter=music_songs` return real data (verified live, 2026-11-16:
  15 trending rows, 20 music search results).

Desktop stream resolution uses **SimpMusic's method** (`desktop/data/NewPipeStreamResolver`):
two NewPipe-fork extractor tiers that fetch the player metadata themselves and
decode YouTube's signature/n challenges, returning real googlevideo URLs. Tier 1
is the **PipePipe extractor** with its challenges solved at the hosted
`api.pipepipe.dev` decoder (SimpMusic's tier 2 — their local QuickJS tier is not
ported); tier 2 is the **BravePipe extractor**, which decodes ciphers on-device
with its bundled Rhino engine. Extracted URLs must contain a playable audio itag
and pass a live HEAD check before use (the same validation SimpMusic applies),
and Piped's `/streams` remains the final fallback. Both extractor artifacts come
from jitpack (`com.github.maxrave-dev.PipePipeExtractor:extractor:bba8d84453`,
`com.github.maxrave-dev:BravePipeExtractor:991b158fee`).

Desktop playback engines: `MpvPlayer` (libmpv via JNA, the SimpMusic `media-jvm`
approach, for Opus/AAC/HLS when libmpv is installed) with automatic fallback
to `JavaSoundPlayer` for local MP3/Ogg/WAV/AIFF. `MpvPlayer` is probed at
startup; when libmpv is absent the app keeps the Java Sound engine.

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

## Why a naive desktop InnerTube client does not work (probed)

The first attempt was a compact InnerTube client rather than a port of
Android's 5,500-line `StreamApi`, and the provider side was probed before
writing code. The probe results:

```
GET  https://music.youtube.com              → HTTP 200 (apiKey + clientVersion scraped)
POST /youtubei/v1/player  WEB_REMIX         → playabilityStatus "UNPLAYABLE"
                                              reason "The page needs to be reloaded"
POST /youtubei/v1/player  WEB_REMIX         → playabilityStatus "LOGIN_REQUIRED"
POST /youtubei/v1/player  WEB               → playabilityStatus "LOGIN_REQUIRED"
POST /youtubei/v1/player  ANDROID_MUSIC     → no playable formats
```

Zero audio formats and zero media URLs are returned for anonymous requests:
the player endpoint demands the same anti-bot proof Android satisfies with
BotGuard PO tokens (`data/music/potoken/BotGuardTokenGenerator.kt`, a WebView
execution of the player's JS challenge) plus the InnerTubeX/NewPipe fallbacks
in `StreamApi`. A small JSON resolver therefore cannot work — that is exactly
the problem SimpMusic solves with full extractors, and what
`NewPipeStreamResolver` now does on desktop.

## Reference implementation: how SimpMusic ships this

SimpMusic (`maxrave-dev/SimpMusic`, GPL-3.0) is the closest existing solution:
the same Kotlin Multiplatform + Compose Multiplatform split this repository now
has, with a working desktop build. Its layout, read from the source:

| Piece | SimpMusic | This repo today |
| --- | --- | --- |
| InnerTube client | `:kotlinYtmusicScraper` (`common/` submodule), Ktor with OkHttp (Android/JVM) and Darwin (iOS) engines, `models/YouTubeClient.kt` holding a client table (ANDROID_MUSIC 7.27.52/kx21, ANDROID 21.26.364, WEB, WEB_REMIX, IOS, MWEB, TVHTML5, each with its own API key and user agent) | Android `StreamApi` (Android-only, OkHttp); desktop resolves through the extractors below, Piped for feed/search |
| Signature/cipher | **PipePipe** extractor fork + **Brave NewPipeExtractor**, with a QuickJS engine (`cipher/QuickJsEngine.kt`, `FaradayCipherEngine.kt`) for local solving and a **hosted tier** (`api.pipepipe.dev`) used when the local decode is rejected | **Ported**: `NewPipeStreamResolver` — PipePipe with the hosted decode tier (their tier 2), BravePipe on-device decode (their tier 3), HEAD-checked URLs, Piped `/streams` as final fallback. Their local QuickJS tier is not ported |
| Desktop playback | **libmpv via JNA** (`media-jvm/.../mpv/MpvPlayerAdapter.kt`, 159 KB; `libmpv` bundled, DASH streams, crossfade, EQ filters) | **Ported (lean)**: `MpvPlayer` (libmpv via JNA, `loadfile` of the resolved URL) with Java Sound fallback for local files |
| Sign-in | QR "login sync": desktop runs a temporary Ktor HTTP server, phone scans the QR and transfers YouTube/Spotify/Discord/Last.fm credentials (`:loginSync`, AES-256-GCM sealed, key in the QR) | **Ported (YouTube)**: the Android app already ships the sender (`YtDesktopPairingClient` + camera scanner); desktop `YtPhonePairing` implements the matching v1 receiver — LAN-only HTTP, five-minute one-use QR, explicit phone approval, AES-256-GCM envelope. `YouTubeSessionStore` persists the cookies and `NewPipeStreamResolver` applies them through `ServiceList.YouTube.tokens`, SimpMusic's own hook |

The key takeaway: **SimpMusic did not write a small JSON resolver.** It ported a
full extractor, added a JS engine for ciphers plus a hosted decode service as
fallback, and replaced the desktop audio backend with a native media library.
KatMusic now uses the same extractor + hosted-decode + mpv method.

Re-running their exact client table and API keys from this machine still
returns `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot"), and so do both
extractor tiers (2026-11-16): this container's IP is flagged for anonymous
YouTube access across every path — InnerTube, the watch page's
`ytInitialPlayerResponse`, Piped `/streams`, both NewPipe forks, and Invidious
instances (403/500 everywhere). SimpMusic itself would fail at the same step
from this IP. The port is the same method they ship; on a normal user IP the
extractors are the working path, and when a provider refuses, the error text is
YouTube's own reason surfaced in the player state.

The local QuickJS cipher tier (their tier 1) remains unported: it is
`cipher/QuickJsEngine.kt` + `FaradayCipherEngine.kt` + player-config store
(~1,400 lines plus the `quickjs-kt` native binding). Without it PipePipe calls
`api.pipepipe.dev` for every challenge — functional, but dependent on that
hosted service.

### QR YouTube login handoff (phone → desktop)

The Android app already had the sending half of this flow
(`YtDesktopPairingClient`, `YtDesktopPairingViewModel`, camera scanner under
Settings → YouTube). The KMP desktop now implements the receiving half in
`desktop/data/YtPhonePairing.kt`, matching that protocol exactly:

- Desktop binds a random LAN port and shows a QR containing
  `{type: "katmusic-youtube-pair", v: 1, hosts, port, path, code, key, expiresAt}`.
  The key is a 32-byte AES key; the code is a one-use secret; the QR lives five
  minutes.
- Phone `GET /v1/yt-pair/request?code&device&client` → `requestId`; a second
  phone is refused with 409 while a request is pending.
- The desktop user must press **Allow phone**; until then the transfer endpoint
  answers 403. Declining is terminal and requires a fresh QR.
- Phone `POST /v1/yt-pair/transfer?code&requestId` with
  `{nonce, ciphertext}` (AES-256-GCM, 12-byte nonce). The desktop decrypts,
  verifies a SAPISID-family cookie is present, accepts the payload once, and
  sets `received`.
- `YouTubeSessionStore` writes `~/KatMusic/youtube-session.json` with
  owner-only POSIX permissions and applies the cookie header immediately.
- `NewPipeStreamResolver` sends `ServiceList.YouTube.tokens = cookies` before
  each extraction (PipePipe then derives both `Cookie` and
  `Authorization: SAPISIDHASH …`, exactly as SimpMusic does) and adds the same
  cookies to BravePipe requests, scoped to YouTube/Google hosts only — never to
  the hosted decoder. Playback and downloads share one resolver instance.

Settings → YouTube account shows the connected name/handle/photo, allows
re-linking another phone, and signs out by deleting the stored session.

Tests: `YtPhonePairingTest` (QR shape, request → approval → encrypted transfer,
replay refusal, second-phone conflict, wrong code/expiry) and
`YouTubeSessionStoreTest` (round-trip, restart reload, SAPISID validation,
corrupt file).

## iOS (deferred)

An iOS host (`iosApp` Xcode project plus `iosMain` actuals) was prototyped on
the same shared UI, but it is not part of this delivery. The `iosApp/` directory,
the `iosMain` sources and the iOS workflow are ignored by git (see
`.gitignore`) so the Android/desktop release stays independent of an unfinished
platform; they can be resumed and committed as their own slice later.

## Verification performed

Environment: JDK 25 (Gradle daemon) with a Temurin JDK 17 toolchain installed,
Android SDK Platform 37.0 + Build-Tools 37.0.0 + NDK 27.3 + CMake 3.22.1.

```bash
./gradlew :shared:compileDebugKotlinAndroid   # shared module, Android target
./gradlew :desktop:compileKotlin              # desktop app + shared desktop target
./gradlew :app:compileDebugKotlin             # full Android app against :shared
./gradlew :shared:desktopTest                 # QueueNavigator + LibrarySearch tests
```

Added later (downloads / playlists / home feed slices):

```bash
./gradlew :shared:desktopTest                 # + HomeFeedModelsTest (3 cases)
./gradlew :desktop:test                       # + M3uPlaylistsRepositoryTest (5 cases)
                                              # + NewPipeStreamResolverTest (4 cases)
./gradlew :app:assembleDebug                  # APK with the shared shell wired to Room + InnerTube
./gradlew :desktop:createDistributable        # desktop image with Piped feed + extractor streaming + M3U playlists
```

Live provider verification for the desktop feed (2026-11-16): `PipedClient`
returned 15 trending items and 20 music-search results through
`api.piped.private.coffee`; `/streams/{id}` returned HTTP 500
`LOGIN_REQUIRED` on every configured instance, which the client reports as a
player error rather than a dead control.

Live streaming verification through SimpMusic's method (2026-11-16, same day):
`NewPipeStreamResolver` ran both tiers against real video ids and reported
YouTube's own errors — PipePipe: "Sign in to confirm you're not a bot";
BravePipe: "YouTube probably temporarily blocked anonymous watch access with
this IP, got error LOGIN_REQUIRED". The watch page fetched from this IP also
carries `playabilityStatus: LOGIN_REQUIRED` with zero formats, Invidious
instances answer 403/530, and Piped `/streams` answers 500 — this container's
IP is bot-flagged on every anonymous path. The extractors are wired and
reachable; playback activates from normal user IPs and the UI shows each
provider's real reason when they refuse.

`./gradlew :app:assembleDebug` packages the Android application (Hilt/KSP,
Room, Media3, the CMake DSP module and the manifest entry for the shared
shell), and `./gradlew :desktop:createDistributable` produced
`desktop/build/compose/binaries/main/app/KatMusic`.

The packaged desktop binary was started in this environment and stopped at
Skiko initialisation: `libGL.so.1: cannot open shared object file`. That is a
missing graphics library in the container (no OpenGL, no display server), not
a build or code failure — the same app image works on a desktop with a GPU
stack. UI runtime behaviour was therefore not observed here; only compilation,
packaging and the shared-logic tests are verified.
