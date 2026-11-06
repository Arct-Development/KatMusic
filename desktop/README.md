# KatMusic Desktop

The Windows, Linux and macOS build of KatMusic. It is a Flutter desktop player
with YouTube Music and lossless streaming, Last.fm authentication/scrobbling,
synced and word-by-word lyrics, downloads, local files, group Jam rooms,
Spotify Canvas, tray controls and MPRIS/SMTC integration.

## UI direction

The desktop app uses a KatMusic-specific visual system rather than the neutral
chrome of its foundation:

- midnight-navy surfaces (`#060910` / `#0B111C` / `#111B2A`);
- KatMusic blue (`#3D91FF`) for selection, progress and focus;
- Figtree for the complete Wave type ramp;
- shader-backed [Liquid Glass](https://github.com/sdegenaar/liquid_glass_widgets)
  on the title bar, navigation rail, player dock and floating controls;
- KatMusic logo, welcome lockup, navigation labels and home wordmark;
- an opaque tonal fallback under Solid material, Reduce Transparency, or when
  **Settings → Surface & motion → Liquid Glass chrome** is turned off.

Content cards and lists stay opaque by design; glass is reserved for the
navigation and control layer above them.

## Requirements

- Flutter **3.47+** (Dart 3.13+) and the desktop toolchain for your OS.
- Windows: Visual Studio 2022 with Desktop development with C++.
- macOS: Xcode with command-line tools.
- Linux: GTK 3, WebKitGTK 4.1, libmpv, libsecret, appindicator and keybinder
  development packages. On Ubuntu 22.04:

```bash
sudo apt-get install -y clang cmake ninja-build pkg-config libgtk-3-dev \
  liblzma-dev libstdc++-12-dev libayatana-appindicator3-dev libasound2-dev \
  libmpv-dev libkeybinder-3.0-dev libwebkit2gtk-4.1-dev libsecret-1-dev \
  libjsoncpp-dev
```

## Run

```bash
flutter pub get
dart tool/obfuscate_secrets.dart
flutter run -d windows   # linux | macos
```

`dart tool/obfuscate_secrets.dart` reads an optional local `.env` (copy
`.env.example`) and writes the gitignored `lib/core/env/secrets.g.dart`. The app
starts without addon credentials; keyed addon calls fail closed.

Last.fm is bring-your-own-key: sign in from the welcome screen and enter your
own API key and shared secret. They stay on this device.

## Build

```bash
flutter build windows --release   # build/windows/x64/runner/Release
flutter build linux --release     # build/linux/x64/release/bundle
flutter build macos --release     # build/macos/Build/Products/Release
```

Linux packages use `linux/packaging/nfpm.yaml`; the Windows installer source is
`windows/installer.iss`. `.github/workflows/desktop.yml` builds all three on
version tags.

## Architecture

- `lib/features/player`: media_kit/libmpv playback, queue, crossfade and AutoMix.
- `lib/features/innertube`: YouTube Music browsing, library sync and playback.
- `lib/features/lossless`: lossless/addon sources.
- `lib/features/lyrics`: timed lyrics plus provider fallback.
- `lib/features/jam`: synchronized listening rooms.
- `lib/ui`: Fluent shell, Wave design tokens, Liquid Glass chrome.
- `lib/core`: storage, networking, diagnostics and artwork cache.

Desktop foundation adapted from LastWave Desktop; the KatMusic identity, theme,
navigation wording, artwork, packaging metadata and Liquid Glass surfaces are
KatMusic-specific. The third-party LastWave lyrics provider keeps its own name.
