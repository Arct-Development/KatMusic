# Security policy

## Reporting a vulnerability

Please don't open a public issue for security problems. Report them privately through
GitHub's [security advisories](https://github.com/ItzFallenMe/KatMusic/security/advisories/new)
or contact [ItzFallenMe](https://github.com/ItzFallenMe) directly.

Include what is affected, how to reproduce it and the impact you expect. You should get
an acknowledgement within a few days.

## Scope

KatMusic stores data locally:

- `katmusic-library.json` – likes, playlists, history, downloads
- `katmusic-settings.json` – preferences (including a Last.fm session key if you connect one)
- `katmusic-account` SharedPreferences – YouTube Music session cookies, if you sign in
- downloaded audio and `.lrc` files under the app's external files directory

These files live inside the app sandbox. Signing out clears the stored YouTube session.
Uninstalling the app removes everything. Never commit real cookies, Last.fm session keys
or API secrets to the repository.

## Supported versions

Only the latest `main` branch is supported with fixes.
