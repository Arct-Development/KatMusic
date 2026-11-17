# Desktop feature and UI parity

## Goal

Desktop should provide the full KatMusic experience with desktop-appropriate
layouts and controls. This does **not** mean sharing personal data between
devices: Desktop settings, library, playlists, downloads, and listening history
remain local to that computer.

Listen Together is the intentional exception: it is a live room feature, so
Android and Desktop must connect to the same Jam server and be able to join the
same room. A KatMusic Code displayed on Desktop must be a Code 128 tag that the
Android in-app scanner can read.

## Current parity snapshot

| Area | Desktop status |
| --- | --- |
| Shared responsive Compose shell, theme, playback dock | Present |
| Local library, album/artist grouping, playlists, downloads | Present, stored locally |
| Online music feed/search/playback | Present through Desktop providers; regional trending selector plus the local-history mix |
| Discover | Desktop has regional online trending (persisted country choice) plus a local-history-weighted mix generated from its library |
| New releases | Desktop can search/open YouTube Music album results through Piped; not yet the same regional/personalized Android release feed |
| First-class listening stats | Present, based on Desktop-local playback history |
| Streamed music in playlists | Desktop M3U entries preserve portable YouTube links and KatMusic metadata; playback resolves a fresh URL |
| Playlist import | Desktop supports local M3U plus public YouTube, Spotify and Apple Music imports; external tracks are matched to YouTube Music |
| Podcasts, lyrics, queue, settings | Present in the shared shell; narrower feature surface than Android |
| Sleep timer | Shared player control with Desktop-local countdown and pause |
| Listen Together | Uses the same WebSocket service/protocol and default endpoint as Android; room controls cover members, co-hosting, suggestions, queue, invites and chat |
| KatMusic Code | Desktop can display/export Code 128 and enter codes from Search; its full-card Code 128 regression test passes, but camera scanning still needs device validation |
| Generated mixes | Local-history-weighted mix from the Desktop library; no online taste-profile mix |
| Online artist, album, genre and playlist detail flows | Public artist search with recent tracks, albums and provider-recommended radio; album detail, genre keyword picks and playlist import are present. Android's dedicated genre pages and full release timeline are not matched |
| Last.fm scrobbling, social listening and friend profiles | Optional bring-your-own-key browser authorization, scrobbling, recent listening, top artists and public friend profiles |
| Account-owned catalog integrations | Spotify login, private/liked playlists and Spotify playback are not ported; public Spotify/Apple playlist import works without account cookies. Spotify Canvas is partner-only, so Desktop uses artwork motion |
| Settings sub-pages and Android-specific playback configuration | Shared appearance plus Desktop library-folder management, YouTube pairing, equalizer, playback integrations, Last.fm and stats; Android-only player decks and camera flows are documented platform differences |
| Desktop OS controls | System-wide media keys through a native hook when the desktop allows it (focused-window fallback otherwise), optional system-tray play/pause/next/previous and track-change notifications with the supplied KatMusic logo |
| Lyrics presentation and sharing | Shared synced lyrics with copy-to-clipboard and a Desktop-rendered PNG lyrics card; Android's alternate player decks and richer on-device share-card editor are Android-specific |
| Android widgets, Auto, notifications, camera and USB-specific integrations | Require suitable desktop-native equivalents where meaningful; tray notifications and manual code entry replace the camera/widget flows |

## Acceptance criteria

- Desktop users can reach every supported Android feature without hidden or
  dead-end routes; mobile-only system integrations get clear desktop
  equivalents rather than fake controls.
- Desktop Search can find local music and online tracks, play a KatMusic Code,
  and open the same share-code card Android can scan.
- Android and Desktop users can create or join the same Jam room and use the
  room's playback, queue, member, suggestion, invite and chat features.
- Desktop-owned data remains local; no account or general-purpose device sync is
  introduced as part of feature parity.
- Desktop and Android builds/tests pass, and the packaged Desktop app is
  smoke-tested before preparing an update.

## Porting approach

Keep shared Compose screens and shared data contracts where behavior is common.
Implement desktop-native storage, file pickers, keyboard/mouse interactions,
audio, export, and provider integrations in `:desktop`. Preserve Android's
platform-specific implementations and give hardware-dependent features an
appropriate desktop alternative instead of coupling Desktop to Android APIs.

## Remaining parity waves

1. **Music discovery:** authenticated/personalized Discover sourced from an
   online taste profile and Android's regional release feed. Desktop already has
   a persisted regional trending chart, a private local-history mix, public
   artist albums and radio; the remaining difference is the account-backed
   recommendation algorithm, which Desktop intentionally does not copy.
2. **Social and catalog integrations:** Spotify sign-in/token import,
   account-owned playlists and private playlist access. Public Spotify/Apple
   playlist import works without account cookies. Spotify Canvas is a
   partner-only API, so Desktop uses artwork motion instead.
3. **Player and settings depth:** Android's alternate player decks and the full
   on-device share-card editor remain Android-specific presentation features.
   Desktop has the shared Classic player, synchronized lyrics, copy and PNG
   lyric-card export; shared appearance, library, YouTube, equalizer, playback
   and Last.fm settings are present.
4. **Desktop-native integrations:** system-wide media keys with a focused-window
   fallback, tray controls and track-change notifications are present. Webcam
   scanning has a manual/paste code equivalent; widgets and Android Auto have no
   meaningful desktop counterpart.

The parity target is complete when every remaining item is either implemented or
has a documented platform-specific equivalent and the acceptance checks above
pass.
