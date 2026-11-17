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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import kotlinx.coroutines.launch
import java.time.Year

/** Desktop album/release discovery using Piped's YouTube Music album filter. */
@Composable
fun DesktopNewReleasesScreen(
    feed: DesktopHomeFeedRepository,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("new releases ${Year.now().value}") }
    var results by remember { mutableStateOf<List<PipedClient.PlaylistSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<PipedClient.PlaylistInfo?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loadingAlbum by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun search() {
        if (query.isBlank() || searching) return
        searching = true
        error = null
        selected = null
        results = emptyList()
        feed.searchAlbums(query.trim())
            .onSuccess { results = it }
            .onFailure { error = it.message ?: "Couldn't search albums" }
        searching = false
    }

    LaunchedEffect(Unit) { search() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected != null) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to albums")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selected == null) "New releases" else selected?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (selected == null) "Browse YouTube Music albums" else selected?.owner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (selected == null) {
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
                        label = { Text("Album or release search") },
                        singleLine = true,
                        enabled = !searching,
                    )
                    Button(
                        onClick = { scope.launch { search() } },
                        enabled = query.isNotBlank() && !searching,
                    ) {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        }
                    }
                }
            }
            item {
                Text(
                    "Results come from the public YouTube Music album catalog; change the query to explore a specific artist or style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (searching && results.isEmpty()) {
                item {
                    CircularProgressIndicator(
                        Modifier.padding(20.dp).size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (!searching && error == null && results.isEmpty()) {
                item {
                    Text(
                        "No albums found. Try another query.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            items(results, key = { it.id }) { album ->
                Card(
                    onClick = {
                        if (loadingAlbum != null) return@Card
                        scope.launch {
                            loadingAlbum = album.id
                            error = null
                            feed.fetchPlaylist(album.id)
                                .onSuccess { selected = it }
                                .onFailure { error = it.message ?: "Couldn't load this album" }
                            loadingAlbum = null
                        }
                    },
                    enabled = loadingAlbum == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(
                            uri = album.artwork,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            contentDescription = album.title,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                album.owner.ifBlank { "YouTube Music" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (loadingAlbum == album.id) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Album, contentDescription = "Open album")
                        }
                    }
                }
            }
        } else {
            val album = selected
            if (album != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Artwork(
                            uri = album.artwork,
                            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(20.dp)),
                            contentDescription = album.title,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${album.owner.ifBlank { "Unknown artist" }} · ${album.tracks.size} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    val queue = album.tracks.map { item ->
                                        Track(
                                            id = item.videoId,
                                            title = item.title,
                                            artist = item.artist,
                                            album = album.title,
                                            durationMs = item.durationMs,
                                            source = item.videoId,
                                            artwork = item.thumbnail ?: album.artwork,
                                            isLocal = false,
                                            videoId = item.videoId,
                                        )
                                    }
                                    if (queue.isNotEmpty()) onPlay(queue, 0)
                                },
                                enabled = album.tracks.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("  Play album")
                            }
                        }
                    }
                }
                items(album.tracks, key = { it.videoId }) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${track.title} — ${track.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val queue = album.tracks.map { item ->
                                    Track(
                                        id = item.videoId,
                                        title = item.title,
                                        artist = item.artist,
                                        album = album.title,
                                        durationMs = item.durationMs,
                                        source = item.videoId,
                                        artwork = item.thumbnail ?: album.artwork,
                                        isLocal = false,
                                        videoId = item.videoId,
                                    )
                                }
                                onPlay(queue, album.tracks.indexOf(track))
                            },
                        ) { Text("Play") }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}
