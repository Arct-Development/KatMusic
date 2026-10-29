package com.itzfallenme.katmusic.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.itzfallenme.katmusic.data.LibraryRepository
import com.itzfallenme.katmusic.data.Track

/**
 * Browse tree shared with Android Auto and Android TV.
 *
 * Media3's `MediaLibraryService` lets an external controller ask for a library tree; this
 * builds one from the local library (liked songs, downloads, playlists, recent plays) as
 * plain [MediaItem]s. Playable items carry the video id in their URI, so playback goes
 * through exactly the same URL resolution as the phone UI.
 *
 * Group membership is recorded when a node is built and read back when the controller asks
 * to play one of its children, which is what lets Auto queue the whole playlist instead of
 * just the tapped track.
 */
object AutoLibrary {

    const val ROOT = "root"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"
    const val RECENT = "recent"
    const val PLAYLISTS = "playlists"
    private const val PLAYLIST_PREFIX = "playlist:"

    const val EXTRA_GROUP = "katmusic.auto.group"

    /** Child lists keyed by node id, rebuilt each time the controller browses. */
    private val groups = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()

    fun group(id: String): List<Track> = groups[id].orEmpty()

    fun browseRoot(): MediaItem = node(ROOT, "KatMusic", browsable = true)

    fun children(library: LibraryRepository, parentId: String): List<MediaItem> {
        val data = library.state.value
        return when {
            parentId == ROOT -> listOf(
                node(LIKED, "Liked songs", browsable = true),
                node(DOWNLOADS, "Downloads", browsable = true),
                node(RECENT, "Recently played", browsable = true),
                node(PLAYLISTS, "Playlists", browsable = true)
            )

            parentId == LIKED -> tracks(LIKED, data.liked)

            parentId == DOWNLOADS -> tracks(DOWNLOADS, data.downloads.map { it.track })

            parentId == RECENT -> tracks(
                RECENT,
                data.history.sortedByDescending { it.playedAt }.map { it.track }.distinctBy { it.id }
            )

            parentId == PLAYLISTS -> data.playlists.map { playlist ->
                node(
                    id = playableGroupId(playlist.id),
                    title = playlist.name,
                    browsable = true,
                    subtitle = "${playlist.tracks.size} tracks",
                    artwork = playlist.artwork.ifBlank { playlist.tracks.firstOrNull()?.artwork.orEmpty() }
                )
            }

            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                val playlist = data.playlists.firstOrNull { it.id == playlistId }
                tracks(parentId, playlist?.tracks.orEmpty())
            }

            else -> emptyList()
        }
    }

    /** Node ids for a playlist's children use the same prefix as the playlist node. */
    fun playableGroupId(playlistId: String): String = "$PLAYLIST_PREFIX$playlistId"

    fun item(library: LibraryRepository, mediaId: String): MediaItem? =
        findInTree(library, mediaId)

    private fun findInTree(library: LibraryRepository, mediaId: String): MediaItem? {
        val data = library.state.value
        val lists = buildMap {
            put(LIKED, data.liked)
            put(DOWNLOADS, data.downloads.map { it.track })
            put(RECENT, data.history.map { it.track }.distinctBy { it.id })
            data.playlists.forEach { put(playableGroupId(it.id), it.tracks) }
        }
        lists.values.forEach { tracks ->
            tracks.firstOrNull { it.id == mediaId }?.let { return playable(it, EXTRA_GROUP, mediaId) }
        }
        val playlist = data.playlists.firstOrNull { playableGroupId(it.id) == mediaId }
        return playlist?.let {
            node(
                id = playableGroupId(it.id),
                title = it.name,
                browsable = true,
                subtitle = "${it.tracks.size} tracks",
                artwork = it.artwork
            )
        }
    }

    private fun tracks(groupId: String, list: List<Track>): List<MediaItem> {
        val playable = list.filter { it.isPlayable }
        groups[groupId] = playable
        return playable.map { playable(it, groupId, it.id) }
    }

    /** Public form used when the controller asks to play a whole group. */
    fun mediaItemFor(track: Track, groupId: String): MediaItem = playable(track, groupId, track.id)

    private fun playable(track: Track, groupId: String, mediaId: String): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_GROUP, groupId)
            putString("trackId", track.id)
        }
        val uri = track.localPath?.takeIf { it.isNotBlank() }?.let { "file://$it" }
            ?: "https://katmusic.local/watch?v=${track.id}"
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun node(
        id: String,
        title: String,
        browsable: Boolean,
        subtitle: String = "",
        artwork: String = ""
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                .setIsBrowsable(browsable)
                .setIsPlayable(!browsable)
                .build()
        )
        .build()

    /** Neighbours of [mediaId] inside its group, and the index the controller asked for. */
    fun queueFor(mediaId: String): Pair<List<Track>, Int>? {
        groups.forEach { (_, tracks) ->
            val index = tracks.indexOfFirst { it.id == mediaId }
            if (index >= 0) return tracks to index
        }
        return null
    }
}
