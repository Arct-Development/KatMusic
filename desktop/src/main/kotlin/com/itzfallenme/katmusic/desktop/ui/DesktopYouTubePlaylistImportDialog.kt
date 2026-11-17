package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.shared.data.HomeCard
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class ImportTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val videoId: String? = null,
    val artwork: String? = null,
)

private data class ImportPlaylist(
    val title: String,
    val owner: String,
    val artwork: String?,
    val tracks: List<ImportTrack>,
    val needsMatching: Boolean,
    val provider: String,
)

/**
 * Public playlist importer. YouTube entries are portable video IDs; Spotify
 * and Apple Music public rows are matched against Piped song search, never
 * authenticated with or read from the user's browser.
 */
@Composable
fun DesktopYouTubePlaylistImportDialog(
    feed: DesktopHomeFeedRepository,
    playlists: PlaylistsRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<ImportPlaylist?>(null) }
    var loading by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf(false) }

    fun loadPlaylist() {
        if (input.isBlank() || loading) return
        scope.launch {
            loading = true
            message = null
            playlist = null
            val isSpotify = input.trim().let { value ->
                value.startsWith("spotify:", ignoreCase = true) ||
                    value.contains("spotify.com", ignoreCase = true) ||
                    value.contains("spotify.link", ignoreCase = true) ||
                    value.matches(Regex("[A-Za-z0-9]{22}"))
            }
            val isAppleMusic = input.trim().let { value ->
                value.startsWith("pl.", ignoreCase = true) ||
                    value.contains("music.apple.com", ignoreCase = true)
            }
            if (isSpotify) {
                feed.fetchSpotifyPlaylist(input.trim())
                    .onSuccess { result ->
                        playlist = ImportPlaylist(
                            title = result.title,
                            owner = result.owner.orEmpty(),
                            artwork = result.artwork,
                            tracks = result.tracks.map { row ->
                                ImportTrack(
                                    title = row.title,
                                    artist = row.artist,
                                    album = row.album.orEmpty(),
                                )
                            },
                            needsMatching = true,
                            provider = "Spotify",
                        )
                    }
                    .onFailure { message = it.message ?: "Couldn't load that Spotify playlist" }
            } else if (isAppleMusic) {
                feed.fetchAppleMusicPlaylist(input.trim())
                    .onSuccess { result ->
                        playlist = ImportPlaylist(
                            title = result.title,
                            owner = "",
                            artwork = null,
                            tracks = result.tracks.map { row ->
                                ImportTrack(
                                    title = row.title,
                                    artist = row.artist,
                                )
                            },
                            needsMatching = true,
                            provider = "Apple Music",
                        )
                    }
                    .onFailure { message = it.message ?: "Couldn't load that Apple Music playlist" }
            } else {
                feed.fetchPlaylist(input.trim())
                    .onSuccess { result ->
                        if (result.tracks.isEmpty()) {
                            message = "No playable tracks were found in that public playlist."
                        } else {
                            playlist = ImportPlaylist(
                                title = result.title,
                                owner = result.owner,
                                artwork = result.artwork,
                                tracks = result.tracks.map { track ->
                                    ImportTrack(
                                        title = track.title,
                                        artist = track.artist,
                                        album = result.title,
                                        durationMs = track.durationMs,
                                        videoId = track.videoId,
                                        artwork = track.thumbnail ?: result.artwork,
                                    )
                                },
                                needsMatching = false,
                                provider = "YouTube",
                            )
                        }
                    }
                    .onFailure { message = it.message ?: "Couldn't load that playlist" }
            }
            if (playlist?.tracks.isNullOrEmpty() && message == null) {
                message = "No tracks were found in that public playlist."
            }
            loading = false
        }
    }

    fun importPlaylist() {
        val selected = playlist ?: return
        if (importing) return
        scope.launch {
            importing = true
            message = null
            val candidates = if (selected.needsMatching) {
                val semaphore = Semaphore(4)
                selected.tracks.take(MAX_EXTERNAL_MATCHES).map { item ->
                    async {
                        semaphore.withPermit {
                            val results = feed.searchMusic("${item.title} ${item.artist}").getOrNull().orEmpty()
                            val match = results.maxByOrNull { candidate -> matchScore(item, candidate) }
                                ?.takeIf { matchScore(item, it) >= MIN_MATCH_SCORE }
                            match?.let { candidate ->
                                val videoId = candidate.videoId ?: return@let null
                                Track(
                                    id = videoId,
                                    title = item.title,
                                    artist = item.artist.ifBlank { candidate.subtitle },
                                    album = item.album,
                                    durationMs = 0L,
                                    source = videoId,
                                    artwork = candidate.artworkUrl ?: selected.artwork,
                                    isLocal = false,
                                    videoId = videoId,
                                )
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            } else {
                selected.tracks.mapNotNull { item ->
                    val id = item.videoId ?: return@mapNotNull null
                    Track(
                        id = id,
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        durationMs = item.durationMs,
                        source = id,
                        artwork = item.artwork ?: selected.artwork,
                        isLocal = false,
                        videoId = id,
                    )
                }
            }

            if (candidates.isEmpty()) {
                message = if (selected.needsMatching) {
                    "No tracks could be matched. Try again later or import a YouTube playlist."
                } else {
                    "No playable tracks were found in that playlist."
                }
                importing = false
                return@launch
            }

            playlists.create(selected.title).fold(
                onSuccess = { target ->
                    playlists.addTracksToPlaylist(target.id, candidates)
                        .onSuccess {
                            val matchDetail = if (selected.needsMatching) {
                                " Matched ${candidates.size} of ${selected.tracks.size} ${selected.provider} tracks."
                            } else {
                                ""
                            }
                            message = "Imported ${candidates.size} tracks to “${target.title}”.$matchDetail"
                            imported = true
                        }
                        .onFailure { error ->
                            message = "Couldn't import the playlist: ${error.message.orEmpty()}"
                        }
                },
                onFailure = { error ->
                    message = error.message ?: "Couldn't create a local playlist"
                },
            )
            importing = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (imported) "Playlist imported" else "Import online playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!imported) {
                    Text(
                        "Paste a public YouTube, Spotify or Apple Music playlist link. External tracks are matched to playable YouTube Music results. The M3U stays on this computer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            playlist = null
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Playlist URL or ID") },
                        singleLine = true,
                        enabled = !loading && !importing,
                    )
                    playlist?.let { details ->
                        Text(details.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(details.provider)
                                if (details.owner.isNotBlank()) append(" · ").append(details.owner)
                                append(" · ").append(details.tracks.size).append(" tracks")
                                if (details.needsMatching && details.tracks.size > MAX_EXTERNAL_MATCHES) {
                                    append(" · first ").append(MAX_EXTERNAL_MATCHES).append(" will be matched")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        details.tracks.take(4).forEach { track ->
                            Text(
                                "• ${track.title} — ${track.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        if (details.tracks.size > 4) {
                            Text(
                                "…and ${details.tracks.size - 4} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                message?.let { value ->
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (value.startsWith("Imported")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (imported) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else if (playlist == null) {
                TextButton(
                    onClick = ::loadPlaylist,
                    enabled = input.isNotBlank() && !loading && !importing,
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    }
                    Text(if (loading) "Loading…" else "Load")
                }
            } else {
                TextButton(
                    onClick = ::importPlaylist,
                    enabled = !loading && !importing,
                ) {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    }
                    Text(if (importing) "Matching and importing…" else "Import playlist")
                }
            }
        },
        dismissButton = {
            if (!imported) {
                TextButton(onClick = onDismiss, enabled = !loading && !importing) { Text("Cancel") }
            }
        },
    )
}

private fun matchScore(source: ImportTrack, candidate: HomeCard): Float {
    val titleScore = tokenOverlap(source.title, candidate.title)
    val artistScore = tokenOverlap(source.artist, candidate.subtitle)
    return titleScore * 3f + artistScore
}

private fun tokenOverlap(first: String, second: String): Float {
    val ignored = setOf("official", "audio", "video", "lyrics", "lyric", "topic", "remaster", "remastered")
    fun tokens(value: String) = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 1 && it !in ignored }
        .toSet()
    val left = tokens(first)
    val right = tokens(second)
    if (left.isEmpty() || right.isEmpty()) return 0f
    return left.intersect(right).size.toFloat() / maxOf(left.size, right.size)
}

private const val MAX_EXTERNAL_MATCHES = 40
private const val MIN_MATCH_SCORE = 1.5f
