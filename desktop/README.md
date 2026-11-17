# KatMusic Desktop (Compose Multiplatform)

The Windows, Linux and macOS build of KatMusic. It is a Compose Desktop
(`:desktop`) application that renders the **shared Compose UI** from
`shared/src/commonMain` — the same theme, typography, shapes, motion, components,
navigation and screens the Android app uses.

Only the platform-specific parts are implemented here:

| Concern | Desktop (`:desktop`) | Android (`:app`) |
| --- | --- | --- |
| UI, design system, navigation | shared (`:shared`, `commonMain`) | shared (`:shared`, `commonMain`) |
| Playback engine | `JavaSoundPlayer` on `javax.sound.sampled` (MP3/Ogg/WAV/AIFF) | Media3/ExoPlayer, USB bit-perfect, DSP/native processors |
| Library | `DesktopMediaLibrary` — folders picked from disk, tags via jaudiotagger | MediaStore + the app's download database |
| Settings storage | `java.util.prefs` | DataStore |
| System integration | window, file chooser | widgets, media session, Auto, notifications, tray-free |

Queue order, shuffle/repeat, search filtering, models, settings semantics and
the theme mapping are shared Kotlin (`shared/src/commonMain`), so both players
advance the queue identically.

## Requirements

- JDK 17 or newer (Gradle 9 toolchain).
- The Android SDK is required for configuration because `:shared` also targets
  Android: set `ANDROID_HOME` (or `sdk.dir` in `local.properties`).

## Run

```bash
./gradlew :desktop:run
```

## Test the shared logic

```bash
./gradlew :shared:desktopTest
```

## Package

```bash
./gradlew :desktop:createDistributable        # app image
./gradlew :desktop:packageDmg                 # macOS
./gradlew :desktop:packageMsi                 # Windows
./gradlew :desktop:packageDeb                 # Linux
```

Tagged builds are produced by `.github/workflows/desktop.yml`.
