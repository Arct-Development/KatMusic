package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.Playlist
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.formatDuration
import com.itzfallenme.katmusic.ui.resolveTrack
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val libraryTabs = listOf("Playlists", "Liked", "Downloads", "History", "Mixes")

@Composable
fun LibraryScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    val mixes = remember(library.history) { ServiceLocator.library.generatedMixes() }
    val scope = rememberCoroutineScope()

    if (creating || renaming != null) {
        AlertDialog(
            onDismissRequest = { creating = false; renaming = null },
            title = { Text(if (renaming != null) "Rename playlist" else "New playlist") },
            text = {
                OutlinedTextField(
                    value = if (renaming != null) renameText else newName,
                    onValueChange = { if (renaming != null) renameText = it else newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renaming
                    if (target != null) {
                        ServiceLocator.library.renamePlaylist(target.id, renameText.ifBlank { target.name })
                    } else {
                        ServiceLocator.library.createPlaylist(newName.ifBlank { "New playlist" })
                    }
                    creating = false
                    renaming = null
                    newName = ""
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; renaming = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${library.liked.size} liked • ${library.playlists.size} playlists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { nav.go(Screen.Stats) }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Stats")
                    }
                    IconButton(onClick = { nav.go(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    libraryTabs.forEachIndexed { index, label ->
                        Chip(text = label, selected = tab == index, onClick = { tab = index })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        when (tab) {
            0 -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction(
                            icon = Icons.Filled.Add,
                            label = "New playlist",
                            modifier = Modifier.weight(1f),
                            onClick = { creating = true }
                        )
                        QuickAction(
                            icon = Icons.Filled.Favorite,
                            label = "Liked songs",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (library.liked.isNotEmpty()) {
                                    ServiceLocator.player.playTracks(library.liked)
                                } else tab = 1
                            }
                        )
                        QuickAction(
                            icon = Icons.Filled.Download,
                            label = "Downloads",
                            modifier = Modifier.weight(1f),
                            onClick = { nav.go(Screen.Downloads) }
                        )
                        QuickAction(
                            icon = Icons.Filled.LibraryMusic,
                            label = "Import",
                            modifier = Modifier.weight(1f),
                            onClick = { nav.go(Screen.Import) }
                        )
                    }
                }
                items(library.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = {
                            nav.openDetail(playlist.id, playlist.name, CardKind.PLAYLIST, playlist.artwork)
                        },
                        onPlay = {
                            if (playlist.tracks.isNotEmpty()) {
                                ServiceLocator.player.playTracks(playlist.tracks)
                            }
                        },
                        onRename = { renaming = playlist; renameText = playlist.name },
                        onDelete = { ServiceLocator.library.deletePlaylist(playlist.id) }
                    )
                }
                if (library.playlists.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.LibraryMusic,
                            title = "No playlists yet",
                            subtitle = "Create a playlist and fill it with anything from YouTube Music."
                        )
                    }
                }
            }

            1 -> {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            url = library.liked.firstOrNull()?.artwork.orEmpty(),
                            modifier = Modifier.size(64.dp),
                            corner = 18.dp,
                            fallbackIcon = Icons.Filled.Favorite
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Liked songs", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${library.liked.size} tracks • ${formatDuration(library.liked.sumOf { it.durationMs })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            if (library.liked.isNotEmpty()) {
                                ServiceLocator.player.playTracks(library.liked.shuffled())
                            }
                        }) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle")
                        }
                        IconButton(onClick = {
                            if (library.liked.isNotEmpty()) {
                                ServiceLocator.player.playTracks(library.liked)
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                    }
                }
                items(library.liked, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.track?.id == track.id,
                        isPlaying = playerState.playing,
                        onClick = { ServiceLocator.player.playTracks(library.liked, library.liked.indexOf(track)) },
                        onToggleLike = { ServiceLocator.library.toggleLike(it) },
                        onStartRadio = { ServiceLocator.player.playRadio(it) },
                        onPlayNext = { ServiceLocator.player.playNext(it) },
                        onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                        onDownload = { ServiceLocator.downloads.download(it) }
                    )
                }
                if (library.liked.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Favorite,
                            title = "Nothing liked yet",
                            subtitle = "Tap the heart on any song to keep it here."
                        )
                    }
                }
            }

            2 -> {
                item {
                    val entries = library.downloads
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Downloads", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${entries.size} tracks • ${ServiceLocator.downloads.humanTotalSize()} saved offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { nav.go(Screen.Downloads) }) { Text("Manage") }
                    }
                }
                items(library.downloads, key = { it.track.id }) { entry ->
                    TrackRow(
                        track = entry.track,
                        isCurrent = playerState.track?.id == entry.track.id,
                        isPlaying = playerState.playing,
                        onClick = {
                            ServiceLocator.player.playTracks(library.downloads.map { it.track })
                        },
                        onToggleLike = { ServiceLocator.library.toggleLike(it) },
                        onDownload = { ServiceLocator.downloads.delete(it.id) },
                        onRemove = {
                            ServiceLocator.downloads.delete(it.id)
                        }
                    )
                }
                if (library.downloads.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Download,
                            title = "No offline music",
                            subtitle = "Download tracks to play them without a connection."
                        )
                    }
                }
            }

            3 -> {
                items(library.history.distinctBy { it.track.id }.take(100)) { entry ->
                    TrackRow(
                        track = entry.track,
                        onClick = { ServiceLocator.player.playTracks(listOf(entry.track)) },
                        onToggleLike = { ServiceLocator.library.toggleLike(it) },
                        onStartRadio = { ServiceLocator.player.playRadio(it) },
                        onDownload = { ServiceLocator.downloads.download(it) }
                    )
                }
                if (library.history.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = "No history yet",
                            subtitle = "Your recently played tracks will appear here."
                        )
                    }
                }
            }

            else -> {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart mixes", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Generated from the artists you play most",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { nav.go(Screen.Generator) }) { Text("Generate") }
                    }
                }
                items(mixes, key = { it.id }) { mix ->
                    PlaylistRow(
                        playlist = mix,
                        onClick = {
                            nav.openDetail(mix.id, mix.name, CardKind.MIX, mix.artwork)
                        },
                        onPlay = {
                            if (mix.tracks.isNotEmpty()) ServiceLocator.player.playTracks(mix.tracks)
                        }
                    )
                }
                if (mixes.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Shuffle,
                            title = "Mixes appear as you listen",
                            subtitle = "Play a few songs and KatMusic will build taste mixes from your history."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier = modifier, corner = 20.dp) {
        Column(
            Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            url = playlist.artwork.ifBlank { playlist.tracks.firstOrNull()?.artwork.orEmpty() },
            modifier = Modifier.size(58.dp),
            corner = 16.dp,
            fallbackIcon = Icons.Filled.LibraryMusic
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.tracks.size} tracks" +
                    if (playlist.description.isNotBlank()) " • ${playlist.description}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
