package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopHomeFeedRepository
import com.itzfallenme.katmusic.desktop.data.DesktopListeningStats
import com.itzfallenme.katmusic.shared.data.HomeCard
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Desktop discovery combines the online trending feed with a private mix
 * generated from the local library and this computer's listening history.
 */
@Composable
fun DesktopDiscoverScreen(
    tracks: List<Track>,
    playerState: PlayerState,
    feed: DesktopHomeFeedRepository,
    stats: DesktopListeningStats,
    playlists: PlaylistsRepository,
    onPlay: (List<Track>, Int) -> Unit,
    initialRegion: String = "US",
    onRegionChanged: (String) -> Unit = {},
) {
    val feedState by feed.state.collectAsState()
    val statsState by stats.state.collectAsState()
    val scope = rememberCoroutineScope()
    var mixSeed by remember { mutableStateOf(0L) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var genreTracks by remember { mutableStateOf<List<HomeCard>>(emptyList()) }
    var genreLoading by remember { mutableStateOf(false) }
    var genreError by remember { mutableStateOf<String?>(null) }
    var selectedRegion by remember(initialRegion) { mutableStateOf(initialRegion) }

    LaunchedEffect(Unit) {
        if (feed.activeRegion != selectedRegion) feed.refreshRegion(selectedRegion)
    }

    val generatedMix = remember(tracks, statsState.topArtists, statsState.topGenres, mixSeed) {
        val artistRank = statsState.topArtists
            .mapIndexed { index, (name, _) -> name.lowercase() to (statsState.topArtists.size - index) }
            .toMap()
        val genreRank = statsState.topGenres
            .mapIndexed { index, (name, _) -> name.lowercase() to (statsState.topGenres.size - index) }
            .toMap()
        tracks
            .groupBy { track ->
                val artistScore = artistRank[track.artist.lowercase()] ?: 0
                val genreScore = track.genre.split('/', ';', ',')
                    .maxOfOrNull { genreRank[it.trim().lowercase()] ?: 0 } ?: 0
                artistScore * 2 + genreScore
            }
            .toList()
            .sortedByDescending { it.first }
            .flatMap { (_, group) -> group.shuffled(Random(mixSeed)) }
            .take(50)
    }

    fun playOnlineCard(card: HomeCard) {
        if (resolvingId != null) return
        scope.launch {
            resolvingId = card.id
            feedback = null
            feed.resolvePlayback(card)
                .onSuccess { onPlay(listOf(it), 0) }
                .onFailure { feedback = it.message ?: "Couldn't play this track" }
            resolvingId = null
        }
    }

    val genreOptions = remember(statsState.topGenres) {
        (statsState.topGenres.map { it.first } +
            listOf("Pop", "Rock", "Hip-Hop", "Electronic", "R&B", "Jazz", "Country", "Indie"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(12)
    }

    fun searchGenre(genre: String) {
        selectedGenre = genre
        genreTracks = emptyList()
        genreError = null
        scope.launch {
            genreLoading = true
            feed.searchMusic("$genre music")
                .onSuccess { genreTracks = it }
                .onFailure { genreError = it.message ?: "Couldn't load $genre music" }
            genreLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Discover", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Online picks and a mix built on this computer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { scope.launch { feed.refreshRegion(selectedRegion) } },
                    enabled = !feedState.isLoading,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh recommendations")
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Your local mix", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        tracks.isEmpty() -> "Add music to your library to make a personal mix."
                        statsState.topArtists.isEmpty() ->
                            "A shuffled mix from your local library. Your listening history will personalize it over time."
                        else -> "A shuffled mix weighted by artists and genres you play most. History never leaves this computer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (generatedMix.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPlay(generatedMix, 0) },
                            enabled = !saving,
                        ) {
                            Text("Play mix · ${generatedMix.size}")
                        }
                        OutlinedButton(
                            onClick = { mixSeed = Random.nextLong() },
                            enabled = !saving,
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Text("  Shuffle")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    val playlistResult = playlists.create("KatMix")
                                    playlistResult.fold(
                                        onSuccess = { playlist ->
                                            playlists.addTracksToPlaylist(playlist.id, generatedMix)
                                                .onSuccess {
                                                    feedback = "Saved ${generatedMix.size} tracks to “${playlist.title}”"
                                                }
                                                .onFailure { error ->
                                                    feedback = "Couldn't save the mix: ${error.message.orEmpty()}"
                                                }
                                        },
                                        onFailure = { error ->
                                            feedback = error.message ?: "Couldn't save the mix"
                                        },
                                    )
                                    saving = false
                                }
                            },
                            enabled = !saving,
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(if (saving) "Saving…" else "Save as playlist")
                        }
                    }
                }
                feedback?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Couldn't")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                if (generatedMix.isNotEmpty()) {
                    generatedMix.take(5).forEachIndexed { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = playerState.current?.id == track.id,
                            onClick = { onPlay(generatedMix, index) },
                        )
                    }
                    if (generatedMix.size > 5) {
                        Text(
                            "And ${generatedMix.size - 5} more tracks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Browse by genre",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(genreOptions, key = { "genre:$it" }) { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { searchGenre(genre) },
                            label = { Text(genre) },
                        )
                    }
                }
            }
        }
        selectedGenre?.let { genre ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$genre · online picks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            selectedGenre = null
                            genreTracks = emptyList()
                            genreError = null
                        },
                    ) { Text("Clear") }
                }
            }
            if (genreLoading && genreTracks.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading $genre picks…")
                    }
                }
            }
            genreError?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            items(genreTracks, key = { "genre-track:$genre:${it.id}" }) { card ->
                card.toTrack()?.let { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.videoId == track.videoId,
                        onClick = { playOnlineCard(card) },
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Trending online",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        DISCOVERY_REGIONS.firstOrNull { it.first == selectedRegion }?.second ?: selectedRegion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Pick a country to see the regional chart. Recommendations stay on this computer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(DISCOVERY_REGIONS, key = { "region:${it.first}" }) { (code, label) ->
                        FilterChip(
                            selected = selectedRegion == code,
                            onClick = {
                                if (selectedRegion == code) return@FilterChip
                                selectedRegion = code
                                onRegionChanged(code)
                                scope.launch { feed.refreshRegion(code) }
                            },
                            enabled = !feedState.isLoading,
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        if (resolvingId != null) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Preparing online track…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (feedState.isLoading && feedState.sections.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.padding(end = 4.dp), strokeWidth = 2.dp)
                    Text("Loading online picks…")
                }
            }
        }
        feedState.error?.let { error ->
            item {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        feedState.sections.forEach { section ->
            item(key = "section:${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(section.cards, key = { "card:${section.title}:${it.id}" }) { card ->
                val track = card.toTrack()
                if (track != null) {
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.videoId == card.videoId,
                        onClick = { playOnlineCard(card) },
                    )
                } else {
                    Text(
                        card.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** Common Piped/YouTube Music trending regions; code is ISO 3166-1 alpha-2. */
private val DISCOVERY_REGIONS = listOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "DE" to "Germany",
    "FR" to "France",
    "IN" to "India",
    "BR" to "Brazil",
    "JP" to "Japan",
    "KR" to "South Korea",
)
