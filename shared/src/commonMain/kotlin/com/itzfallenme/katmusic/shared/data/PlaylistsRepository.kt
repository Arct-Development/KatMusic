package com.itzfallenme.katmusic.shared.data

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * One playlist entry, in the shape the shared UI understands.
 * [tracks] holds the playlist contents in order.
 */
data class Playlist(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    val createdAtMillis: Long = 0L,
) {
    val trackCount: Int get() = tracks.size
}

/**
 * Playlists, shared by Android and desktop.
 *
 * Android persists playlists in Room via the app's own `SavedPlaylistDao`
 * (mode = "custom", same JSON track list the full app renders), so a playlist
 * created in the shared shell appears in the main Android app and vice versa.
 * Desktop persists one `.m3u` file per playlist — a real, portable format any
 * desktop player can open — inside the app-data folder.
 */
interface PlaylistsRepository {
    val playlists: StateFlow<List<Playlist>>

    suspend fun create(title: String): Result<Playlist>
    suspend fun delete(playlistId: String): Result<Unit>
    suspend fun addToPlaylist(playlistId: String, track: Track): Result<Unit>
    /** Add several tracks as one repository operation where the platform supports batching. */
    suspend fun addTracksToPlaylist(playlistId: String, tracks: List<Track>): Result<Unit> {
        for (track in tracks) {
            val result = addToPlaylist(playlistId, track)
            val error = result.exceptionOrNull()
            if (error != null) return Result.failure(error)
        }
        return Result.success(Unit)
    }
    suspend fun removeFromPlaylist(playlistId: String, trackId: String): Result<Unit>
}
