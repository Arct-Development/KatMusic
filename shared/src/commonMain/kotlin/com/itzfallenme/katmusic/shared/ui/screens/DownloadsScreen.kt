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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.data.DownloadsRepository
import com.itzfallenme.katmusic.shared.data.DownloadedTrack
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.shared.ui.components.formatDuration
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import kotlinx.coroutines.launch

/**
 * The shared Downloads screen: real offline files on each platform (Android:
 * the app's Room download table; desktop: the local file store). Deleting a
 * row deletes the underlying audio file through the platform repository.
 */
@Composable
fun DownloadsScreen(
    repository: DownloadsRepository,
    playerState: com.itzfallenme.katmusic.shared.player.PlayerState,
    onPlay: (List<com.itzfallenme.katmusic.shared.models.Track>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val downloads by repository.downloads.collectAsState()
    val totalBytes by repository.totalBytes.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmTarget by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${downloads.size} tracks · ${repository.formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloads.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Remove all downloads")
                }
            }
        }

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No downloads yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val tracks = remember(downloads) { downloads.map { it.toTrack() } }
            LazyColumn(
                modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = item.toTrack(),
                                isCurrent = playerState.current?.id == item.id,
                                onClick = { onPlay(tracks, tracks.indexOfFirst { it.id == item.id }.coerceAtLeast(0)) },
                            )
                        }
                        IconButton(onClick = { confirmTarget = item.id }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${item.title}")
                        }
                    }
                    Text(
                        text = itemSubtitle(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 84.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }

    confirmTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("Delete download?") },
            text = { Text("The audio file will be removed from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.remove(id) }
                    confirmTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all downloads?") },
            text = { Text("Every downloaded audio file will be deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.clearAll() }
                    confirmClear = false
                }) { Text("Remove all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun itemSubtitle(item: DownloadedTrack): String = buildList {
    if (item.fileSizeBytes > 0) add(formatBytes(item.fileSizeBytes))
    if (item.durationMs > 0) add(formatDuration(item.durationMs))
}.joinToString(" · ")

/** Kilo/mega/giga formatting for download sizes, shared by rows and headers. */
fun formatBytes(bytes: Long): String = com.itzfallenme.katmusic.shared.data.formatByteCount(bytes)
