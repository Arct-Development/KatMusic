package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.background
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
import com.itzfallenme.katmusic.ui.theme.wobbleOverscroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.playback.DownloadProgress
import com.itzfallenme.katmusic.playback.DownloadState
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.InlineProgress
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SectionHeader
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.TrackRow
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val progress by ServiceLocator.downloads.progress.collectAsState()
    val queue by ServiceLocator.downloads.queue.collectAsState()
    val activeId by ServiceLocator.downloads.activeTrackId.collectAsState()
    val settingsState by ServiceLocator.settings.state.collectAsState()
    val wifiOnly = settingsState.wifiOnlyDownloads
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val used = ServiceLocator.downloads.totalBytes()
    val free = ServiceLocator.downloads.freeBytes()
    val failed = progress.filterValues { it.state == DownloadState.FAILED }

    // Running item first, then the queue in order, then anything that failed.
    val taskIds = remember(progress, queue, activeId) {
        buildList {
            activeId?.let { add(it) }
            addAll(queue.map { it.id })
            addAll(progress.values.filter { it.state == DownloadState.FAILED }.mapNotNull { it.track?.id })
        }.distinct()
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = "Downloads", onBack = { nav.back() })
        LazyColumn(
            modifier = Modifier.fillMaxSize().wobbleOverscroll(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${library.downloads.size} tracks offline",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${ServiceLocator.downloads.fmt(used)} used • ${ServiceLocator.downloads.fmt(free)} free",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (taskIds.isNotEmpty()) {
                                Text(
                                    "${taskIds.size} in queue",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (used + free > 0) {
                            LinearProgressIndicator(
                                progress = { (used.toFloat() / (used + free)).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                enabled = library.downloads.isNotEmpty(),
                                onClick = {
                                    ServiceLocator.player.playTracks(library.downloads.map { it.track })
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Play all")
                            }
                            TextButton(
                                enabled = taskIds.isNotEmpty(),
                                onClick = { ServiceLocator.downloads.cancelAll() }
                            ) {
                                Text("Cancel queue")
                            }
                            TextButton(
                                enabled = library.downloads.isNotEmpty(),
                                onClick = { confirmDeleteAll = true }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                enabled = library.downloads.isNotEmpty() && !exporting,
                                onClick = {
                                    exporting = true
                                    exportStatus = null
                                    scope.launch {
                                        var copied = 0
                                        var failedCount = 0
                                        library.downloads.forEach { entry ->
                                            ServiceLocator.downloads.exportToMediaStore(entry)
                                                .onSuccess { copied++ }
                                                .onFailure { failedCount++ }
                                        }
                                        exporting = false
                                        exportStatus = when {
                                            copied == 0 && failedCount > 0 ->
                                                "Nothing was exported - the files may have been deleted"
                                            failedCount > 0 -> "Exported $copied songs, $failedCount failed"
                                            else -> "Exported $copied songs to Music/KatMusic"
                                        }
                                    }
                                }
                            ) {
                                Text(if (exporting) "Exporting…" else "Export to device library")
                            }
                            if (failed.isNotEmpty()) {
                                TextButton(onClick = { failed.values.forEach { it.track?.let(ServiceLocator.downloads::retry) } }) {
                                    Text("Retry ${failed.size} failed")
                                }
                            }
                        }
                        if (exportStatus != null) {
                            Text(
                                exportStatus ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (wifiOnly && !ServiceLocator.downloads.onWifi()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Queued until Wi-Fi is back",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            ServiceLocator.settings.update { it.copy(wifiOnlyDownloads = false) }
                        }) { Text("Use data") }
                    }
                }
            }

            if (taskIds.isNotEmpty()) {
                item { SectionHeader("In progress", "${taskIds.size} waiting") }
                items(taskIds, key = { "task-" + it }) { id ->
                    val state = progress[id]
                    if (state != null) {
                        val position = queue.indexOfFirst { it.id == id }
                        DownloadTaskRow(
                            progress = state,
                            queuedPosition = if (position >= 0) position + 1 else null,
                            onCancel = { ServiceLocator.downloads.cancel(id) },
                            onRetry = { state.track?.let { ServiceLocator.downloads.retry(it) } }
                        )
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (library.downloads.isNotEmpty()) {
                item { SectionHeader("Saved offline", "Tap a row to play the offline copy") }
            }

            items(library.downloads, key = { it.track.id }) { entry ->
                Column {
                    TrackRow(
                        track = entry.track,
                        isCurrent = playerState.track?.id == entry.track.id,
                        isPlaying = playerState.playing,
                        onClick = {
                            val all = library.downloads.map { it.track }
                            ServiceLocator.player.playTracks(all, all.indexOfFirst { it.id == entry.track.id })
                        },
                        onDownload = { ServiceLocator.downloads.delete(it.id) },
                        onRemove = { ServiceLocator.downloads.delete(it.id) },
                        onToggleLike = { ServiceLocator.library.toggleLike(it) }
                    )
                    val p = progress[entry.track.id]
                    if (p != null && p.running) {
                        InlineProgress(
                            fraction = p.fraction,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }
            }

            if (library.downloads.isEmpty() && taskIds.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Download,
                        title = "No downloads",
                        subtitle = "Tap the download icon on any track and it is queued here. " +
                            "One song transfers at a time, and Wi-Fi-only queues wait for a network."
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = {
                Text("${library.downloads.size} songs and their lyrics will be removed from this device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    library.downloads.forEach { ServiceLocator.downloads.delete(it.track.id) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DownloadTaskRow(
    progress: DownloadProgress,
    queuedPosition: Int?,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val track = progress.track ?: return
    val scheme = MaterialTheme.colorScheme
    val failed = progress.state == DownloadState.FAILED
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), corner = 18.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (progress.state) {
                            DownloadState.WAITING_NETWORK -> Icons.Filled.WifiOff
                            DownloadState.FAILED -> Icons.Filled.Refresh
                            DownloadState.DONE -> Icons.Filled.DownloadDone
                            else -> Icons.Filled.Download
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (failed) scheme.error else scheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusLine(progress, queuedPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = if (failed) onRetry else onCancel) {
                    Icon(
                        if (failed) Icons.Filled.Refresh else Icons.Filled.Close,
                        contentDescription = if (failed) "Retry" else "Cancel download",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (progress.state == DownloadState.RUNNING) {
                Spacer(Modifier.height(6.dp))
                InlineProgress(progress.fraction, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun statusLine(progress: DownloadProgress, queuedPosition: Int?): String {
    val manager = ServiceLocator.downloads
    return when (progress.state) {
        DownloadState.WAITING_NETWORK -> "Waiting for Wi-Fi"
        DownloadState.QUEUED -> queuedPosition?.let { "Queued · #$it" } ?: "Queued"
        DownloadState.RUNNING -> {
            val done = (progress.fraction * 100).toInt()
            val size = if (progress.totalBytes > 0) {
                " · ${manager.fmt(progress.bytes)} of ${manager.fmt(progress.totalBytes)}"
            } else ""
            if (progress.attempt > 1) "$done% · retrying$size" else "$done%$size"
        }
        DownloadState.DONE -> "Downloaded"
        DownloadState.FAILED -> progress.error ?: "Download failed"
    }
}
