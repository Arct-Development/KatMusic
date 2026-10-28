package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Local library: liked songs, playlists, history, downloads and recent searches. */
class LibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val file = File(context.filesDir, "katmusic-library.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryData> = _state.asStateFlow()

    private fun load(): LibraryData = runCatching {
        if (file.exists()) json.decodeFromString<LibraryData>(file.readText()) else LibraryData()
    }.getOrDefault(LibraryData())

    private fun persist(data: LibraryData) {
        _state.value = data
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), data))
            tmp.renameTo(file)
        }
    }

    private fun edit(block: (LibraryData) -> LibraryData) = persist(block(_state.value))

    /** Replaces the whole library, used by backup restore. */
    fun replaceAll(data: LibraryData) = persist(data)

    fun isLiked(trackId: String): Boolean = _state.value.liked.any { it.id == trackId }

    fun toggleLike(track: Track) = edit { data ->
        val liked = if (data.liked.any { it.id == track.id }) {
            data.liked.filterNot { it.id == track.id }
        } else listOf(track) + data.liked
        data.copy(liked = liked)
    }

    fun removeFromLiked(trackId: String) = edit { data ->
        data.copy(liked = data.liked.filterNot { it.id == trackId })
    }

    fun createPlaylist(name: String, description: String = ""): Playlist {
        val playlist = Playlist(
            id = "local-" + UUID.randomUUID(),
            name = name,
            description = description,
            createdAt = System.currentTimeMillis()
        )
        edit { it.copy(playlists = it.playlists + playlist) }
        return playlist
    }

    fun addToPlaylist(playlistId: String, tracks: List<Track>) = edit { data ->
        data.copy(playlists = data.playlists.map { playlist ->
            if (playlist.id != playlistId) playlist
            else {
                val existing = playlist.tracks.map { it.id }.toSet()
                val merged = playlist.tracks + tracks.filterNot { it.id in existing }
                playlist.copy(
                    tracks = merged,
                    artwork = playlist.artwork.ifBlank { tracks.firstOrNull()?.artwork.orEmpty() }
                )
            }
        })
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) = edit { data ->
        data.copy(playlists = data.playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
            else playlist
        })
    }

    fun moveInPlaylist(playlistId: String, from: Int, to: Int) = edit { data ->
        data.copy(playlists = data.playlists.map { playlist ->
            if (playlist.id != playlistId) return@map playlist
            val list = playlist.tracks.toMutableList()
            if (from !in list.indices || to !in list.indices) return@map playlist
            val item = list.removeAt(from)
            list.add(to, item)
            playlist.copy(tracks = list)
        })
    }

    fun renamePlaylist(playlistId: String, name: String, description: String? = null) = edit { data ->
        data.copy(playlists = data.playlists.map {
            if (it.id == playlistId) it.copy(name = name, description = description ?: it.description) else it
        })
    }

    fun deletePlaylist(playlistId: String) = edit { data ->
        data.copy(playlists = data.playlists.filterNot { it.id == playlistId })
    }

    fun recordPlay(track: Track) = edit { data ->
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(track, now, track.durationMs)
        val pruned = (listOf(entry) + data.history).take(800)
        data.copy(history = pruned)
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        edit { data ->
            data.copy(recentSearches = (listOf(trimmed) + data.recentSearches.filterNot {
                it.equals(trimmed, true)
            }).take(12))
        }
    }

    fun clearRecentSearches() = edit { it.copy(recentSearches = emptyList()) }

    fun clearHistory() = edit { it.copy(history = emptyList()) }

    fun addDownload(entry: DownloadEntry) = edit { data ->
        data.copy(downloads = listOf(entry) + data.downloads.filterNot { it.track.id == entry.track.id })
    }

    fun removeDownload(trackId: String) = edit { data ->
        data.copy(downloads = data.downloads.filterNot { it.track.id == trackId })
    }

    fun downloadFor(trackId: String): DownloadEntry? = _state.value.downloads.firstOrNull { it.track.id == trackId }

    fun toggleFollowArtist(card: MediaCard) = edit { data ->
        val exists = data.followedArtists.any { it.browseId == card.browseId }
        data.copy(
            followedArtists = if (exists) data.followedArtists.filterNot { it.browseId == card.browseId }
            else data.followedArtists + card
        )
    }

    fun addScrobble(scrobble: Scrobble) = edit { data ->
        data.copy(scrobbles = (data.scrobbles + scrobble).takeLast(2000))
    }

    fun markScrobblesSynced(count: Int) = edit { data ->
        data.copy(scrobbles = data.scrobbles.drop(count))
    }

    fun pendingScrobbles(): List<Scrobble> = _state.value.scrobbles.filterNot { it.synced }

    fun stats(): WrappedStats {
        val history = _state.value.history
        if (history.isEmpty()) return WrappedStats()
        val playsByTrack = history.groupingBy { it.track.title }.eachCount()
        val playsByArtist = history.groupingBy { it.track.artist.ifBlank { "Unknown" } }.eachCount()
        val hourly = MutableList(24) { 0 }
        history.forEach {
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = it.playedAt }
                .get(java.util.Calendar.HOUR_OF_DAY)
            hourly[hour] = hourly[hour] + 1
        }
        return WrappedStats(
            totalMinutes = history.sumOf { it.listenedMs } / 60_000,
            totalPlays = history.size,
            topTracks = playsByTrack.entries.sortedByDescending { it.value }.take(10)
                .map { it.key to it.value },
            topArtists = playsByArtist.entries.sortedByDescending { it.value }.take(10)
                .map { it.key to it.value },
            hourly = hourly
        )
    }

    /** Playlists built from listening data — "On repeat" style mixes. */
    fun generatedMixes(): List<Playlist> {
        val history = _state.value.history
        if (history.size < 3) return emptyList()
        val byArtist = history.groupBy { it.track.artist }
        val topArtist = byArtist.maxByOrNull { it.value.size }?.key.orEmpty()
        val mixes = ArrayList<Playlist>()
        val artistTracks = history.filter { it.track.artist == topArtist }.map { it.track }.distinctBy { it.id }
        if (artistTracks.size >= 3) {
            mixes.add(
                Playlist(
                    id = "mix-artist-$topArtist",
                    name = "$topArtist mix",
                    tracks = artistTracks.take(30),
                    description = "Built from your listening",
                    artwork = artistTracks.first().artwork
                )
            )
        }
        val onRepeat = history.groupingBy { it.track.id }.eachCount().entries
            .sortedByDescending { it.value }.take(20)
            .mapNotNull { entry -> history.firstOrNull { it.track.id == entry.key }?.track }
        if (onRepeat.size >= 3) {
            mixes.add(
                Playlist(
                    id = "mix-repeat",
                    name = "On repeat",
                    tracks = onRepeat,
                    description = "The songs you keep coming back to",
                    artwork = onRepeat.first().artwork
                )
            )
        }
        val fresh = history.map { it.track }.distinctBy { it.id }.shuffled().take(25)
        if (fresh.size >= 3) {
            mixes.add(
                Playlist(
                    id = "mix-rediscover",
                    name = "Rediscover",
                    tracks = fresh,
                    description = "A shuffle through your history",
                    artwork = fresh.first().artwork
                )
            )
        }
        return mixes
    }
}
