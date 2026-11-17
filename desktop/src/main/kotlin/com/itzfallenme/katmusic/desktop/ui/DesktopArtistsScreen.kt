package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.desktop.data.PipedClient
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import kotlinx.coroutines.launch

/** Public YouTube Music artist search and channel-track browsing. */
@Composable
fun DesktopArtistsScreen(
    feed: DesktopHomeFeedRepository,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf<List<PipedClient.ArtistSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<PipedClient.ArtistInfo?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loadingArtist by remember { mutableStateOf<String?>(null) }
    var radioLoading by remember { mutableStateOf(false) }
    var artistAlbums by remember { mutableStateOf<List<PipedClient.PlaylistSummary>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var loadingAlbum by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank() || searching) return
        scope.launch {
            searching = true
            error = null
            artists = emptyList()
            selected = null
            feed.searchArtists(query.trim())
                .onSuccess { artists = it }
                .onFailure { error = it.message ?: "Couldn't search artists" }
            searching = false
        }
    }

    val selectedArtist = selected
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedArtist != null) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to artists")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedArtist?.name ?: "Artists",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (selectedArtist == null) "Search public YouTube Music artist channels"
                        else formatSubscriberCount(selectedArtist.subscribers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (selectedArtist == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Artist name") },
                        singleLine = true,
                        enabled = !searching,
                    )
                    Button(
                        onClick = ::search,
                        enabled = query.isNotBlank() && !searching,
                    ) {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = "Search artists")
                        }
                    }
                }
            }
            if (searching && artists.isEmpty()) {
                item { CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp) }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (!searching && query.isNotBlank() && artists.isEmpty() && error == null) {
                item {
                    Text(
                        "No artists found. Try another search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            items(artists, key = { it.id }) { artist ->
                Card(
                    onClick = {
                        if (loadingArtist != null) return@Card
                        scope.launch {
                            loadingArtist = artist.id
                            error = null
                            artistAlbums = emptyList()
                            feed.fetchArtist(artist.id)
                                .onSuccess { info ->
                                    selected = info
                                    albumsLoading = true
                                    feed.searchAlbums("${info.name} albums")
                                        .onSuccess { results -> artistAlbums = results.take(8) }
                                        .onFailure { /* Albums are supplementary; tracks still work. */ }
                                    albumsLoading = false
                                }
                                .onFailure { error = it.message ?: "Couldn't load this artist" }
                            loadingArtist = null
                        }
                    },
                    enabled = loadingArtist == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(
                            uri = artist.artwork,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)),
                            contentDescription = artist.name,
                        )
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (loadingArtist == artist.id) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Open", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            val artist = selectedArtist
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        uri = artist.banner ?: artist.artwork,
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(20.dp)),
                        contentDescription = artist.name,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.titleLarge)
                        if (artist.subscribers > 0L) {
                            Text(
                                formatSubscriberCount(artist.subscribers),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                val queue = artist.tracks.map { it.toTrack(artist.name) }
                                if (queue.isNotEmpty()) onPlay(queue, 0)
                            },
                            enabled = artist.tracks.isNotEmpty() && !radioLoading,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("  Play artist tracks")
                        }
                        TextButton(
                            onClick = {
                                val seed = artist.tracks.firstOrNull() ?: return@TextButton
                                scope.launch {
                                    radioLoading = true
                                    error = null
                                    feed.radio(seed.videoId)
                                        .onSuccess { queue ->
                                            if (queue.isNotEmpty()) onPlay(queue, 0)
                                            else error = "No radio tracks found for ${artist.name}"
                                        }
                                        .onFailure { error = it.message ?: "Couldn't start artist radio" }
                                    radioLoading = false
                                }
                            },
                            enabled = artist.tracks.isNotEmpty() && !radioLoading,
                        ) {
                            if (radioLoading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("  Starting…")
                            } else {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Text("  Start radio")
                            }
                        }
                    }
                }
            }
            artist.description.takeIf(String::isNotBlank)?.let { description ->
                item {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            item {
                Text(
                    "Recent tracks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 10.dp),
                )
            }
            items(artist.tracks, key = { it.videoId }) { item ->
                val track = item.toTrack(artist.name)
                TrackRow(
                    track = track,
                    isCurrent = false,
                    onClick = { onPlay(artist.tracks.map { it.toTrack(artist.name) }, artist.tracks.indexOf(item)) },
                )
            }
            if (albumsLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Loading albums…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (artistAlbums.isNotEmpty()) {
                item {
                    Text(
                        "Albums",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                    )
                }
                items(artistAlbums, key = { "album:${it.id}" }) { album ->
                    Card(
                        onClick = {
                            if (loadingAlbum != null) return@Card
                            scope.launch {
                                loadingAlbum = album.id
                                error = null
                                feed.fetchPlaylist(album.id)
                                    .onSuccess { info ->
                                        val queue = info.tracks.map {
                                            it.toTrack(album.owner.ifBlank { artist.name })
                                        }
                                        if (queue.isNotEmpty()) onPlay(queue, 0)
                                        else error = "“${album.title}” has no playable tracks"
                                    }
                                    .onFailure { error = it.message ?: "Couldn't open this album" }
                                loadingAlbum = null
                            }
                        },
                        enabled = loadingAlbum == null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(
                                uri = album.artwork,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                contentDescription = album.title,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    album.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    album.owner.ifBlank { artist.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (loadingAlbum == album.id) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Play", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

private fun PipedClient.FeedTrack.toTrack(artistName: String): Track =
    Track(
        id = videoId,
        title = title,
        artist = artist.ifBlank { artistName },
        durationMs = durationMs,
        source = videoId,
        artwork = thumbnail,
        isLocal = false,
        videoId = videoId,
    )

private fun formatSubscriberCount(value: Long): String {
    if (value <= 0L) return ""
    val label = when {
        value >= 1_000_000_000 -> "${value / 1_000_000_000}B"
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 1_000 -> "${value / 1_000}K"
        else -> value.toString()
    }
    return "$label subscribers"
}
