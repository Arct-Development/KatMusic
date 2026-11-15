package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.library.LibrarySearch
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth

@Composable
fun SearchScreen(
    tracks: List<Track>,
    playerState: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks) { LibrarySearch.filter(tracks, query) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Search", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Songs, artists, albums") },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(results, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = playerState.current?.id == track.id,
                    onClick = { onPlay(results, results.indexOf(track)) },
                )
            }
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Text(
                        text = "No matches for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}
