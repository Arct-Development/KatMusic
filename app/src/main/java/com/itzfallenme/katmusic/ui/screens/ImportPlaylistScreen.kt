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
                    Text("Other services", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Spotify and Apple Music links require their developer APIs and a " +
                            "conversion step. Export the playlist to YouTube Music first (there " +
                            "are free converters), then paste the YouTube Music link here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
