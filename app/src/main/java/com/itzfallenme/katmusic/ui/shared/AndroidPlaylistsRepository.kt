package com.itzfallenme.katmusic.ui.shared

import com.itzfallenme.katmusic.data.generate.toGenerated
import com.itzfallenme.katmusic.data.local.db.SavedPlaylistDao
import com.itzfallenme.katmusic.data.local.db.SavedPlaylistEntity
import com.itzfallenme.katmusic.data.playlist.PlaylistRepository
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.data.Playlist
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [PlaylistsRepository] backed by the app's real Room `saved_playlists`
 * table ([SavedPlaylistDao], mode = "custom"), the same storage the full
 * Android app reads: a playlist created in the shared shell shows up in the
 * main app's playlist list and vice versa.
 */
@Singleton
class AndroidPlaylistsRepository @Inject constructor(
    private val dao: SavedPlaylistDao,
) : PlaylistsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        scope.launch {
            runCatching { dao.getAll() }.getOrDefault(emptyList())
            reload()
        }
    }

    override suspend fun create(title: String): Result<Playlist> = runCatching {
        val entity = SavedPlaylistEntity(
            id = System.currentTimeMillis(),
            title = title.trim(),
            subtitle = "Custom playlist",
            mode = "custom",
            tracksJson = "[]",
            createdAtMillis = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        reload()
        entity.toPlaylist()
    }

    override suspend fun delete(playlistId: String): Result<Unit> = runCatching {
        val rowId = playlistId.removePrefix("pl:").toLongOrNull()
            ?: error("Not a Room playlist id: $playlistId")
        dao.deleteById(rowId)
        reload()
        Unit
    }

    override suspend fun addToPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
        val rowId = playlistId.removePrefix("pl:").toLongOrNull()
            ?: error("Not a Room playlist id: $playlistId")
        val entity = dao.getById(rowId) ?: error("Playlist not found")
        val tracks = decode(entity.tracksJson).toMutableList()
        tracks.add(track)
        dao.upsert(entity.copy(tracksJson = encode(tracks)))
        reload()
        Unit
    }

    override suspend fun removeFromPlaylist(playlistId: String, trackId: String): Result<Unit> = runCatching {
        val rowId = playlistId.removePrefix("pl:").toLongOrNull()
            ?: error("Not a Room playlist id: $playlistId")
        val entity = dao.getById(rowId) ?: error("Playlist not found")
        val tracks = decode(entity.tracksJson).filterNot { it.id == trackId }
        dao.upsert(entity.copy(tracksJson = encode(tracks)))
        reload()
        Unit
    }

    /** Re-reads the Room table; the DAO exposes suspend reads, not a Flow. */
    private fun reload() {
        scope.launch {
            val entities = runCatching { dao.getAll() }.getOrDefault(emptyList())
            _playlists.value = entities
                .filter { it.mode == "custom" || it.mode == "liked" || it.mode == "local" }
                .map { it.toPlaylist() }
                .sortedBy { it.title.lowercase() }
        }
    }

    private fun encode(tracks: List<Track>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.itzfallenme.katmusic.data.generate.StoredTrack.serializer()),
            tracks.map { it.toStoredTrack() },
        )

    private fun decode(tracksJson: String): List<Track> = runCatching {
        val stored = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(com.itzfallenme.katmusic.data.generate.StoredTrack.serializer()),
            tracksJson,
        )
        stored.map { it.toGenerated() }.map { generated ->
            Track(
                id = generated.url.ifBlank { generated.name },
                title = generated.name,
                artist = generated.artist,
                album = generated.album.orEmpty(),
                source = generated.url,
                artwork = generated.artworkUrl,
                isLocal = generated.url.startsWith("/"),
            )
        }
    }.getOrDefault(emptyList())

    private fun Track.toStoredTrack() = com.itzfallenme.katmusic.data.generate.StoredTrack(
        name = title,
        artist = artist,
        url = source,
        image = artwork,
        album = album.ifBlank { null },
    )

    private fun SavedPlaylistEntity.toPlaylist(): Playlist = Playlist(
        id = "pl:$id",
        title = title,
        tracks = decode(tracksJson),
        createdAtMillis = createdAtMillis,
    )
}
