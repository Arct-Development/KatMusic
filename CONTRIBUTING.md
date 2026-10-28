# Contributing to KatMusic

Thanks for wanting to help. This document keeps contributions consistent and easy to review.

## Getting set up

1. Install JDK 17 or newer and the Android SDK (API 35). Android Studio works out of the box.
2. Clone the repository and open it in Android Studio, or build from the terminal:

   ```bash
   ./gradlew :app:assembleDebug
   ```

3. Run the unit tests before opening a pull request (PR):

   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

| Path | Purpose |
| --- | --- |
| `data/` | InnerTube client, JSON parsing, lyrics, accounts, library and settings storage |
| `playback/` | ExoPlayer controller, media session service, downloads, equalizer |
| `ui/theme/` | Colour engine and artwork-derived theming |
| `ui/screens/` | One file per screen |
| `ui/Components.kt` | Shared rows, cards, shelves, dialogs |

## How to contribute

- **Bugs:** open an issue with steps to reproduce, expected vs actual behaviour and your device/Android version. Logcat snippets help.
- **Features:** open an issue first so the approach can be agreed before code is written. Small, focused PRs are merged fastest.
- **Translations:** strings live in `app/src/main/res/values/strings.xml`. Add a new `values-<lang>` folder; don't rename existing keys.
- **Formatting:** keep the existing Kotlin style (4 spaces, trailing commas, no wildcard imports). Run `./gradlew :app:lintDebug` if you changed resources.

## Pull request checklist

- [ ] The project builds (`./gradlew :app:assembleDebug`)
- [ ] Unit tests pass (`./gradlew :app:testDebugUnitTest`)
- [ ] New behaviour has been exercised on a device or emulator
- [ ] No credentials, API keys or personal tokens are committed
- [ ] The PR description explains *why*, not just *what*

## Code of conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Licensing

KatMusic is licensed under the GNU General Public License v3.0. By contributing you
agree that your work is distributed under the same licence.
