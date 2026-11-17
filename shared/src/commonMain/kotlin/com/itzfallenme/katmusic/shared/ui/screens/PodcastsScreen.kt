package com.itzfallenme.katmusic.shared.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.data.PodcastEpisode
import com.itzfallenme.katmusic.shared.data.PodcastShow
import com.itzfallenme.katmusic.shared.data.PodcastsRepository
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.formatDuration
import kotlinx.coroutines.launch

/**
 * Podcast discovery, show browsing and episode playback.
 *
 * Search returns real shows from the platform's podcast directory; opening a
 * show reads its RSS feed and plays the enclosure URL through the shared
 * player.
 */
@Composable
fun PodcastsScreen(
    repository: PodcastsRepository,
    onPlay: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(repository) {
        if (state.shows.isEmpty() && state.selectedShow == null && !state.isLoading) {
            repository.search("podcasts")
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.selectedShow != null) {
                IconButton(onClick = repository::backToResults) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to podcast search")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.selectedShow?.title ?: "Podcasts",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.selectedShow?.author ?: "Search shows and play episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    if (state.selectedShow != null) {
                        scope.launch { repository.openShow(state.selectedShow!!) }
                    } else {
                        scope.launch { repository.search(query) }
                    }
                },
                enabled = !state.isLoading,
            ) {
                Icon(
                    if (state.selectedShow != null) Icons.Rounded.Refresh else Icons.Rounded.Search,
                    contentDescription = if (state.selectedShow != null) "Refresh episodes" else "Search podcasts",
                )
            }
        }

        if (state.selectedShow == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search podcasts") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch { repository.search(query) }
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        val error = state.error
        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (state.selectedShow != null) {
                        scope.launch { repository.openShow(state.selectedShow!!) }
                    } else {
                        scope.launch { repository.search(query.ifBlank { "podcasts" }) }
                    }
                }) {
                    Text("Retry")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.selectedShow != null -> {
                    EpisodeList(
                        episodes = state.episodes,
                        onPlay = { index ->
                            val show = state.selectedShow ?: return@EpisodeList
                            onPlay(state.episodes.map { it.toTrack(show) }, index)
                        },
                    )
                }
                else -> {
                    ShowList(
                        shows = state.shows,
                        onOpen = { show -> scope.launch { repository.openShow(show) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowList(shows: List<PodcastShow>, onOpen: (PodcastShow) -> Unit) {
    if (shows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No shows found. Try another search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        itemsIndexed(shows, key = { _, show -> show.id }) { _, show ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(show) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    uri = show.artworkUrl,
                    modifier = Modifier.size(64.dp),
                    contentDescription = show.title,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = show.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (show.episodeCount > 0) {
                        Text(
                            text = "${show.episodeCount} episodes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(episodes: List<PodcastEpisode>, onPlay: (Int) -> Unit) {
    if (episodes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "This show has no playable episodes right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    uri = episode.artworkUrl,
                    modifier = Modifier.size(56.dp),
                    contentDescription = episode.title,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(episode.publishedAt, formatDuration(episode.durationMs))
                            .filter { it.isNotBlank() && it != "--:--" }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (episode.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = episode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play episode",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
