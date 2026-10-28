package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.TrackRow
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val progress = ServiceLocator.downloads.progress.value
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(title = "Downloads", onBack = { nav.back() })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "${library.downloads.size} tracks offline",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${ServiceLocator.downloads.humanTotalSize()} used • ${ServiceLocator.downloads.root.absolutePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                enabled = library.downloads.isNotEmpty(),
                                onClick = {
                                    ServiceLocator.player.playTracks(library.downloads.map { it.track })
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.height(0.dp))
                                Text("Play all")
                            }
                            TextButton(onClick = {
                                library.downloads.forEach { ServiceLocator.downloads.delete(it.track.id) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Text("Delete all")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                enabled = library.downloads.isNotEmpty() && !exporting,
                                onClick = {
                                    exporting = true
                                    exportStatus = null
                                    scope.launch {
                                        var copied = 0
                                        var failed = 0
                                        library.downloads.forEach { entry ->
                                            ServiceLocator.downloads.exportToMediaStore(entry)
                                                .onSuccess { copied++ }
                                                .onFailure { failed++ }
                                        }
                                        exporting = false
                                        exportStatus = when {
                                            copied == 0 && failed > 0 ->
                                                "Nothing was exported - the files may have been deleted"
                                            failed > 0 -> "Exported $copied songs, $failed failed"
                                            else -> "Exported $copied songs to Music/KatMusic"
                                        }
                                    }
                                }
                            ) {
                                Text(if (exporting) "Exporting…" else "Export to device library")
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
                        com.itzfallenme.katmusic.ui.InlineProgress(
                            fraction = p.fraction,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }
            }

            if (library.downloads.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Download,
                        title = "No downloads",
                        subtitle = "Tap the download icon on any track and it will be saved here for offline listening."
                    )
                }
            }
        }
    }
}
