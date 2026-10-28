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
    private val dashSplit = Regex("\\s+[\u2013\u2014-]\\s+")
    private val numberingRegex = Regex("^\\d+[.)]\\s*")
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

    /**
     * Reads a plain list of songs - one per line, as "Artist - Title". Playlists exported
     * from Spotify, Apple Music or anywhere else can be pasted straight in.
     */
    fun parseTrackList(text: String): List<Pair<String, String>> {
        val entries = ArrayList<Pair<String, String>>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("-").removePrefix("•").trim()
            if (line.length < 3 || line.startsWith("http", ignoreCase = true)) return@forEach
            val cleaned = line.replace(numberingRegex, "")
            val parts = cleaned.split(dashSplit, limit = 2)
            if (parts.size != 2) return@forEach
            val artist = parts[0].trim()
            val title = parts[1].trim()
            if (artist.isNotBlank() && title.isNotBlank()) entries.add(artist to title)
        }
        return entries.distinctBy { it.second.lowercase() }.take(100)
    }

    suspend fun importTrackList(
        text: String,
        library: LibraryRepository,
        name: String = ""
    ): Result {
        val entries = parseTrackList(text)
        if (entries.isEmpty()) {
            return Result.Failure("Add one song per line, written as Artist - Title")
        }
        val tracks = ArrayList<Track>()
        entries.forEach { (artist, title) ->
            val match = runCatching { InnerTube.search("$artist $title") }
                .getOrNull()
                ?.asSequence()
                ?.flatMap { it.tracks }
                ?.firstOrNull { it.id.isNotBlank() }
            if (match != null && tracks.none { it.id == match.id }) tracks.add(match)
        }
        if (tracks.isEmpty()) {
            return Result.Failure("None of those songs could be matched on YouTube Music")
        }
        val playlist = library.createPlaylist(
            name.ifBlank { "Imported list" },
            "Imported from a song list"
        )
        library.addToPlaylist(playlist.id, tracks)
        val saved = library.state.value.playlists.firstOrNull { it.id == playlist.id } ?: playlist
        return Result.Success(saved)
    }

    suspend fun import(input: String, library: LibraryRepository): Result {
        if (input.isBlank()) return Result.Failure("Paste a playlist link first")
        if (isUnsupportedSource(input)) {
            return Result.Failure(
                "Spotify and Apple Music links can't be read without their developer keys. " +
                    "Paste the matching YouTube Music playlist link, or paste the track list below."
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
