package com.itzfallenme.katmusic.data

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val description: String = "",
    val artwork: String = "",
    val createdAt: Long = 0L,
    val remoteId: String? = null
)

@Serializable
data class HistoryEntry(
    val track: Track,
    val playedAt: Long,
    val listenedMs: Long = 0L
)

@Serializable
data class DownloadEntry(
    val track: Track,
    val path: String,
    val lyricsPath: String? = null,
    val downloadedAt: Long = 0L,
    val quality: String = "auto",
    val bytes: Long = 0L
)

@Serializable
data class Scrobble(
    val title: String,
    val artist: String,
    val album: String = "",
    val timeMs: Long,
    val synced: Boolean = false
)

@Serializable
data class LibraryData(
    val liked: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val downloads: List<DownloadEntry> = emptyList(),
    val scrobbles: List<Scrobble> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val followedArtists: List<MediaCard> = emptyList()
)

@Serializable
data class AppSettings(
    val highQualityStream: Boolean = true,
    /** Max / High / Data saver, see [StreamQuality]. */
    val streamQuality: String = StreamQuality.HIGH.id,
    val wifiOnlyDownloads: Boolean = false,
    val dynamicArtworkTheme: Boolean = true,
    val amoled: Boolean = false,
    val seedColor: String = "#8AB4F8",
    val useSystemDynamicColor: Boolean = false,
    /** iOS-style translucent materials across the app. */
    val liquidGlass: Boolean = true,
    /** Use the bundled Google Sans Flex rather than the platform typeface. */
    val useCustomFont: Boolean = true,
    /** Bolder cut of the bundled font; ignored when [useCustomFont] is off. */
    val useBoldFont: Boolean = true,
    val lyricsEnabled: Boolean = true,
    val lyricsOffsetMs: Long = 0L,
    val lyricsTextScale: Float = 1f,
    /** Prefer the Apple-Music TTML catalogue so every word lights up as it is sung. */
    val wordByWordLyrics: Boolean = true,
    val autoplayRadio: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val skipSilence: Boolean = false,
    val normalizeVolume: Boolean = false,
    val playbackSpeed: Float = 1f,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: List<Float> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmSessionKey: String = "",
    val lastFmUsername: String = "",
    val scrobblingEnabled: Boolean = false,
    /** Last.fm profiles whose public listening shows up in the Friends feed. */
    val lastFmFriends: List<String> = emptyList(),
    val showExplicit: Boolean = true,
    val reducedMotion: Boolean = false,
    val combinedLibrarySections: Boolean = true,
    val statsEnabled: Boolean = true,
    val sponsorBlockEnabled: Boolean = false,
    /** system | light | dark */
    val themeMode: String = "system",
    /** Bloomy halo behind the line/word being sung. */
    val lyricsGlow: Boolean = true,
    val romanizedLyrics: Boolean = false,
    /** Publish the playing track to Discord as a Rich Presence card. */
    val discordPresenceEnabled: Boolean = false,
    /** Discord application ID the card is attributed to (from the Developer Portal). */
    val discordAppId: String = "1563081601254953002",
    /** OpenAI-compatible endpoint used for AI picks; the key stays on this device. */
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "gpt-4o-mini",
    /** Downmix stereo to a single channel for one-earpiece listening. */
    val monoAudio: Boolean = false,
    /** Cap the music stream so long sessions cannot creep up to full volume. */
    val volumeLimitEnabled: Boolean = false,
    val volumeLimitPercent: Int = 80,
    /** Ask for a break after this many minutes of playback; 0 turns it off. */
    val listeningReminderMinutes: Int = 0
)

@Serializable
data class WrappedStats(
    val totalMinutes: Long = 0L,
    val totalPlays: Int = 0,
    val topTracks: List<Pair<String, Int>> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topAlbums: List<Pair<String, Int>> = emptyList(),
    val hourly: List<Int> = List(24) { 0 },
    /** Plays per weekday, Monday first. */
    val byWeekday: List<Int> = List(7) { 0 },
    /** Minutes in the window before this one, for the "more than last week" line. */
    val previousMinutes: Long = 0L
)
