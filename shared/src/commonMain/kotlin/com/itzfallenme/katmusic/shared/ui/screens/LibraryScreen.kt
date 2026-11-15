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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.library.ScanState
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.SectionLabel
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    scan: ScanState,
    playerState: PlayerState,
    canChooseFolder: Boolean,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = scan.message ?: "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canChooseFolder) {
                IconButton(onClick = onChooseFolder) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Choose music folder")
                }
            }
            IconButton(onClick = onRefresh, enabled = !scan.isScanning) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
            }
        }

        if (scan.isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Scanning…", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (tracks.isEmpty() && !scan.isScanning) {
            EmptyLibrary(canChooseFolder = canChooseFolder, onChooseFolder = onChooseFolder)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { SectionLabel("Songs") }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        onClick = { onPlay(tracks, tracks.indexOf(track)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(canChooseFolder: Boolean, onChooseFolder: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (canChooseFolder) {
                    "Add a folder of audio files and KatMusic will index it in place — nothing is copied or moved."
                } else {
                    "No songs were found on this device yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (canChooseFolder) {
                Button(
                    onClick = onChooseFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Text("  Choose folder")
                }
            }
        }
    }
}
