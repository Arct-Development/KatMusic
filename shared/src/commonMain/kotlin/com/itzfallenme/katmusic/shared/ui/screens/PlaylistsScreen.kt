package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.data.Playlist
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.shared.ui.screens.CollectionTargetHolder
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import com.itzfallenme.katmusic.ui.components.expressiveBounceClickable
import kotlinx.coroutines.launch

/**
 * The shared Playlists screen: playlist list on the left of the flow, contents
 * in the same screen once opened. Android persists in Room (shared with the
 * main app), desktop as `.m3u` files.
 */
@Composable
fun PlaylistsScreen(
    repository: PlaylistsRepository,
    playerState: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    /** Called with a collection to open in the shared Collection route. */
    onOpenCollection: (CollectionTargetHolder) -> Unit = {},
    /** Optional platform file import; hidden when the platform has none. */
    onImport: (() -> Unit)? = null,
) {
    val playlists by repository.playlists.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Playlist?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Playlists", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${playlists.size} playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            onImport?.let { import ->
                IconButton(onClick = import) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = "Import M3U playlist")
                }
            }
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist")
            }
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No playlists yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().adaptiveContentWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveBounceClickable { open = playlist }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Artwork(
                            uri = playlist.tracks.firstOrNull()?.artwork,
                            modifier = Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                            contentDescription = playlist.title,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (playlist.trackCount > 0) {
                            IconButton(onClick = { onPlay(playlist.tracks, 0) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${playlist.title}")
                            }
                        }
                        IconButton(onClick = { confirmDelete = playlist }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${playlist.title}")
                        }
                    }
                }
            }
        }
    }

    open?.let { playlist ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(playlist.title) },
            text = {
                if (playlist.tracks.isEmpty()) {
                    Text("This playlist is empty.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        playlist.tracks.take(6).forEach { t ->
                            Text("• ${t.title} — ${t.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (playlist.tracks.size > 6) Text("…and ${playlist.tracks.size - 6} more")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onOpenCollection(CollectionTargetHolder(playlist.title, "${playlist.trackCount} tracks", playlist.tracks.firstOrNull()?.artwork, playlist.tracks))
                    open = null
                }) { Text("Open") }
            },
            dismissButton = {
                Row {
                    if (playlist.tracks.isNotEmpty()) {
                        TextButton(onClick = {
                            onPlay(playlist.tracks, 0)
                            open = null
                        }) { Text("Play") }
                    }
                    TextButton(onClick = { open = null }) { Text("Close") }
                }
            },
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            scope.launch {
                                repository.create(newTitle.trim())
                                newTitle = ""
                                showCreate = false
                            }
                        }
                    },
                    enabled = newTitle.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${playlist.title}”?") },
            text = { Text("The playlist will be removed. Tracks in your library are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.delete(playlist.id) }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
