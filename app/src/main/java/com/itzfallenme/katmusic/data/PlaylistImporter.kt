package com.itzfallenme.katmusic.data

/**
 * Imports a public YouTube Music playlist from a shared link, a raw playlist id or
 * pasted share text. Spotify / Apple links are recognised so the UI can explain what
 * is supported instead of failing silently.
 */
object PlaylistImporter {

    sealed interface Result {
        data class Success(val playlist: Playlist) : Result
        data class Failure(val message: String) : Result
    }

    private val playlistRegex = Regex("[?&]list=([A-Za-z0-9_-]{10,})")
    private val rawIdRegex = Regex("^(VL|PL|RD|OLAK|UU|LL)[A-Za-z0-9_-]{8,}$")
    private val spotifyRegex = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)")
    private val appleRegex = Regex("music\\.apple\\.com/.*playlist")

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        playlistRegex.find(trimmed)?.let { return it.groupValues[1] }
        if (rawIdRegex.matches(trimmed)) {
            return trimmed.removePrefix("VL")
        }
        return null
    }

    fun isUnsupportedSource(input: String): Boolean =
        spotifyRegex.containsMatchIn(input) || appleRegex.containsMatchIn(input)

    suspend fun import(input: String, library: LibraryRepository): Result {
        if (input.isBlank()) return Result.Failure("Paste a playlist link first")
        if (isUnsupportedSource(input)) {
            return Result.Failure(
                "Spotify and Apple Music links can't be read without their developer keys. " +
                    "Paste the matching YouTube Music playlist link instead."
            )
        }
        val id = extractPlaylistId(input)
            ?: return Result.Failure("That doesn't look like a YouTube playlist link")
        val page = runCatching { InnerTube.detail("VL$id") }.getOrNull()
            ?: return Result.Failure("Could not load that playlist")
        if (page.tracks.isEmpty()) {
            return Result.Failure("No playable tracks found in this playlist")
        }
        val name = page.title.ifBlank { "Imported playlist" }
        val playlist = library.createPlaylist(name, page.subtitle.ifBlank { "Imported from YouTube Music" })
        library.addToPlaylist(playlist.id, page.tracks)
        val saved = library.state.value.playlists.firstOrNull { it.id == playlist.id } ?: playlist
        return Result.Success(saved)
    }
}
