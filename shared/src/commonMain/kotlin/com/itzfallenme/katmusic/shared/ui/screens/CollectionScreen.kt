package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth

/**
 * One album or artist, opened from the shared Library. Album and artist
 * collections are the same screen: a header followed by its tracks, playing the
 * collection as a queue.
 */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    artwork: String?,
    tracks: List<Track>,
    playerState: PlayerState,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(
                uri = artwork,
                modifier = Modifier.size(120.dp),
                contentDescription = title,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tracks.isNotEmpty()) {
                    Button(onClick = { onPlay(tracks, 0) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text("  Play")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
