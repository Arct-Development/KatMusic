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
    val wifiOnlyDownloads: Boolean = false,
    val dynamicArtworkTheme: Boolean = true,
    val amoled: Boolean = false,
    val seedColor: String = "#8AB4F8",
    val useSystemDynamicColor: Boolean = false,
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
    val showExplicit: Boolean = true,
    val reducedMotion: Boolean = false,
    val combinedLibrarySections: Boolean = true,
    val statsEnabled: Boolean = true,
    val sponsorBlockEnabled: Boolean = false,
    val playerStyle: String = "expressive",
    val romanizedLyrics: Boolean = false,
    /** Publish the playing track to Discord as a Rich Presence card. */
    val discordPresenceEnabled: Boolean = false,
    /** Discord application ID the card is attributed to (from the Developer Portal). */
    val discordAppId: String = ""
)

@Serializable
data class WrappedStats(
    val totalMinutes: Long = 0L,
    val totalPlays: Int = 0,
    val topTracks: List<Pair<String, Int>> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val hourly: List<Int> = List(24) { 0 }
)
