# Contributing to KatMusic

Thanks for wanting to help. This document keeps contributions consistent and easy to review.

## Getting set up

APKs are built by the GitHub Actions workflow, so you do not need an Android SDK on your
machine to ship one.

1. Fork the repository and push a branch to your fork.
2. Open **Actions → Build APK → Run workflow** and wait for the run to finish.
3. Download the `katmusic-debug-apk` artifact from that run and unzip it to get
   `app-debug.apk`.

Pull requests run the same workflow (unit tests plus a debug build), and its log is the
first thing reviewers look at. If you do use Android Studio locally, `./gradlew
:app:assembleDebug` and `./gradlew :app:testDebugUnitTest` behave as usual – but CI is the
supported way to produce an APK.

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

- [ ] The **Build APK** workflow is green on the pull request
- [ ] The debug artifact installs and the changed screen actually works on a device
- [ ] New behaviour has been exercised on a device or emulator
- [ ] No credentials, API keys or personal tokens are committed
- [ ] The PR description explains *why*, not just *what*

## Code of conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Licensing

KatMusic is licensed under the GNU General Public License v3.0. By contributing you
agree that your work is distributed under the same licence.
