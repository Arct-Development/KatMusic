package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.PlaylistImporter
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import kotlinx.coroutines.launch

@Composable
fun ImportPlaylistScreen(nav: Navigator) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var listText by remember { mutableStateOf("") }
    var listName by remember { mutableStateOf("") }
    var listBusy by remember { mutableStateOf(false) }
    var listMessage by remember { mutableStateOf<String?>(null) }
    var listSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(title = "Import playlist", onBack = { nav.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("From YouTube Music", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Paste a playlist link (music.youtube.com/playlist?list=…) or a bare " +
                            "playlist id. Every track is copied into a new local playlist, so you " +
                            "can reorder, download or remove tracks without touching the original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; message = null; success = false },
                        label = { Text("Playlist link or id") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !busy && input.isNotBlank(),
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                when (val result = PlaylistImporter.import(input, ServiceLocator.library)) {
                                    is PlaylistImporter.Result.Success -> {
                                        success = true
                                        message = "Imported \"${result.playlist.name}\" with ${result.playlist.tracks.size} tracks"
                                    }
                                    is PlaylistImporter.Result.Failure -> {
                                        success = false
                                        message = result.message
                                    }
                                }
                                busy = false
                            }
                        }
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Import")
                        }
                    }
                    if (message != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            message ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("From a list of songs", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Exported a playlist from Spotify or Apple Music? Paste the song list - one " +
                            "track per line as \"Artist - Title\". Each line is matched on YouTube " +
                            "Music and saved as a new playlist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it },
                        label = { Text("Playlist name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = listText,
                        onValueChange = { listText = it; listMessage = null; listSuccess = false },
                        label = { Text("Artist - Title") },
                        placeholder = { Text("Radiohead - Everything In Its Right Place") },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !listBusy && listText.isNotBlank(),
                        onClick = {
                            listBusy = true
                            listMessage = null
                            scope.launch {
                                val result = PlaylistImporter.importTrackList(
                                    listText,
                                    ServiceLocator.library,
                                    listName
                                )
                                when (result) {
                                    is PlaylistImporter.Result.Success -> {
                                        listSuccess = true
                                        listMessage = "Matched ${result.playlist.tracks.size} of " +
                                            "${PlaylistImporter.parseTrackList(listText).size} songs"
                                    }
                                    is PlaylistImporter.Result.Failure -> {
                                        listSuccess = false
                                        listMessage = result.message
                                    }
                                }
                                listBusy = false
                            }
                        }
                    ) {
                        if (listBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Match and import")
                        }
                    }
                    if (listMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            listMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (listSuccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
