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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.data.HomeCard
import com.itzfallenme.katmusic.shared.data.HomeFeedRepository
import com.itzfallenme.katmusic.shared.library.LibrarySearch
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    tracks: List<Track>,
    playerState: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    homeFeed: HomeFeedRepository? = null,
    onPlayCode: suspend (String) -> Result<Track> = {
        Result.failure(IllegalStateException("KatMusic Code playback is unavailable"))
    },
) {
    var query by remember { mutableStateOf("") }
    var resolvingCode by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var remoteResults by remember { mutableStateOf<List<HomeCard>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val codeVideoId = remember(query) { KatMusicCode.decode(query) }
    val results = remember(query, tracks) { LibrarySearch.filter(tracks, query) }

    LaunchedEffect(query, homeFeed, codeVideoId) {
        val feed = homeFeed
        val searchQuery = query.trim()
        if (feed == null || searchQuery.length < 2 || codeVideoId != null) {
            remoteResults = emptyList()
            remoteLoading = false
            remoteError = null
            return@LaunchedEffect
        }
        delay(350)
        remoteResults = emptyList()
        remoteError = null
        remoteLoading = true
        val result = feed.searchMusic(searchQuery)
        remoteResults = result.getOrDefault(emptyList())
        remoteError = result.exceptionOrNull()?.message
        remoteLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Search", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Songs, artists, albums, or a KatMusic Code") },
            )
            if (codeVideoId != null) {
                Button(
                    onClick = {
                        scope.launch {
                            resolvingCode = true
                            codeError = null
                            val result = onPlayCode(codeVideoId)
                            result
                                .onSuccess { track -> onPlay(listOf(track), 0) }
                                .onFailure { codeError = it.message ?: "Couldn't open this KatMusic Code" }
                            resolvingCode = false
                        }
                    },
                    enabled = !resolvingCode,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(if (resolvingCode) "Opening song…" else "Play KatMusic Code")
                }
            }
            codeError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (results.isNotEmpty()) {
                item {
                    Text(
                        "On this device",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(results, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = playerState.current?.id == track.id,
                    onClick = { onPlay(results, results.indexOf(track)) },
                )
            }
            if (homeFeed != null && query.trim().length >= 2 && codeVideoId == null) {
                item {
                    Text(
                        "Online results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (remoteLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                remoteError?.let { message ->
                    item {
                        Text(
                            "Online search unavailable: $message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(remoteResults, key = { "online:${it.id}" }) { card ->
                    val track = card.toTrack()
                    if (track != null) {
                        TrackRow(
                            track = track,
                            isCurrent = playerState.current?.videoId == card.videoId,
                            onClick = {
                                scope.launch {
                                    homeFeed.resolvePlayback(card)
                                        .onSuccess { playable -> onPlay(listOf(playable), 0) }
                                        .onFailure { remoteError = it.message ?: "Couldn't play this track" }
                                }
                            },
                        )
                    } else {
                        Text(
                            card.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                if (!remoteLoading && remoteError == null && remoteResults.isEmpty()) {
                    item {
                        Text(
                            "No online matches for “${query.trim()}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (query.isNotBlank() && results.isEmpty() && codeVideoId == null &&
                (homeFeed == null || query.trim().length < 2)
            ) {
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
