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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.Discover
import com.itzfallenme.katmusic.data.GenreResolver
import com.itzfallenme.katmusic.data.Shelf
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.CardShelf
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import com.itzfallenme.katmusic.ui.TrackRow
import kotlinx.coroutines.launch

/**
 * Discovery radar and Genre DNA.
 *
 * Two engines feed this screen: the listening history (seeds for the track radar and the
 * genre breakdown) and the public catalogue (new releases). Nothing here needs an
 * account, and every section degrades quietly when a source comes back empty.
 */
@Composable
fun DiscoverScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var dna by remember { mutableStateOf<List<GenreResolver.GenreSlice>>(emptyList()) }
    var seeds by remember { mutableStateOf<List<String>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var genreBusy by remember { mutableStateOf<String?>(null) }
    val settings by ServiceLocator.settings.state.collectAsState()
    var aiPrompt by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<com.itzfallenme.katmusic.data.AiSuggest.Result?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        val result = runCatching { Discover.feed(ServiceLocator.library) }
        result.onSuccess {
            tracks = it.tracks
            dna = it.dna
            seeds = it.seeds
        }.onFailure {
            error = it.message ?: "Could not build your radar"
        }
        newReleases = runCatching { YtMusic.newReleases() }.getOrDefault(emptyList())
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    fun playGenreMixes(genre: String) {
        if (genreBusy != null) return
        genreBusy = genre
        scope.launch {
            val mix = runCatching { Discover.genreRadio(genre) }.getOrDefault(emptyList())
            genreBusy = null
            if (mix.isNotEmpty()) ServiceLocator.player.playTracks(mix)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(
            title = "Discover",
            onBack = { nav.back() },
            actions = {
                IconButton(onClick = { scope.launch { load() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Reading your taste…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            tracks.isEmpty() && dna.isEmpty() -> EmptyState(
                icon = Icons.Filled.Explore,
                title = "Nothing to discover yet",
                subtitle = error ?: "Play a few tracks, like a few songs, and the radar fills itself from your taste."
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (dna.isNotEmpty()) {
                    item {
                        GenreDnaCard(
                            dna = dna,
                            busyGenre = genreBusy,
                            onGenre = ::playGenreMixes
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                item {
                    AiPicksCard(
                        configured = com.itzfallenme.katmusic.data.AiSuggest.isConfigured(settings),
                        prompt = aiPrompt,
                        onPrompt = { aiPrompt = it },
                        busy = aiBusy,
                        result = aiResult,
                        error = aiError,
                        onAsk = {
                            if (!aiBusy) {
                                aiBusy = true
                                aiError = null
                                scope.launch {
                                    val history = library.history
                                    val result = runCatching {
                                        com.itzfallenme.katmusic.data.AiSuggest.suggest(
                                            settings = settings,
                                            topArtists = history.groupBy { it.track.artist }
                                                .entries.sortedByDescending { it.value.size }
                                                .map { it.key }.take(12),
                                            topTracks = history.take(12).map { it.track.title },
                                            request = aiPrompt,
                                            count = 20
                                        )
                                    }
                                    aiBusy = false
                                    val value = result.getOrNull()
                                    if (value == null || value.tracks.isEmpty()) {
                                        aiError = result.exceptionOrNull()?.message
                                            ?: value?.answer?.takeIf { it.isNotBlank() }
                                            ?: "No suggestions came back"
                                    } else {
                                        aiResult = value
                                    }
                                }
                            }
                        },
                        onPlay = { aiResult?.tracks?.let { ServiceLocator.player.playTracks(it) } },
                        onSave = {
                            val result = aiResult ?: return@AiPicksCard
                            if (result.tracks.isEmpty()) return@AiPicksCard
                            val name = aiPrompt.ifBlank { "AI picks" }.take(48)
                            val playlist = ServiceLocator.library.createPlaylist(name, result.answer)
                            ServiceLocator.library.addToPlaylist(playlist.id, result.tracks)
                            nav.openDetail(
                                playlist.id,
                                playlist.name,
                                com.itzfallenme.katmusic.data.CardKind.PLAYLIST
                            )
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (tracks.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Your radar", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    if (seeds.isEmpty()) "Fresh from the catalogue"
                                    else "Because you play ${seeds.take(3).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { ServiceLocator.player.playTracks(tracks) },
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                        }
                    }
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            index = index + 1,
                            isCurrent = playerState.track?.id == track.id,
                            isPlaying = playerState.track?.id == track.id && playerState.playing,
                            onClick = { ServiceLocator.player.playQueueStarting(track, tracks) },
                            onToggleLike = { ServiceLocator.library.toggleLike(it) },
                            onGoToArtist = { current ->
                                current.artistId?.let {
                                    nav.openDetail(it, current.artist, CardKind.ARTIST)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }

                if (newReleases.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        CardShelf(
                            shelf = newReleases.first(),
                            onCardClick = { card ->
                                nav.openDetail(card.browseId, card.title, card.kind, card.artwork, card.params)
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }

                if (library.history.isEmpty() && tracks.isNotEmpty()) {
                    item {
                        Text(
                            "Play something and the radar starts learning your taste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI picks. The key is the listener's own, so the card explains what is missing instead of
 * hiding the feature when it is not configured yet.
 */
@Composable
private fun AiPicksCard(
    configured: Boolean,
    prompt: String,
    onPrompt: (String) -> Unit,
    busy: Boolean,
    result: com.itzfallenme.katmusic.data.AiSuggest.Result?,
    error: String?,
    onAsk: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit
) {
    GlassCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("AI picks", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (configured) {
                    "Describe what you want and the model curates it from your taste."
                } else {
                    "Add your own API key in Settings → AI to let a model curate from your taste."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = onPrompt,
                label = { Text("Something like: rainy night, slow soul") },
                singleLine = true,
                enabled = configured,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAsk, enabled = configured && !busy) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Ask for picks")
                    }
                }
                if (result != null && result.tracks.isNotEmpty()) {
                    TextButton(onClick = onPlay) { Text("Play all") }
                    TextButton(onClick = onSave) { Text("Save playlist") }
                }
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            result?.let { picked ->
                if (picked.answer.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        picked.answer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (picked.missing > 0) {
                    Text(
                        "${picked.missing} suggestion(s) had no catalogue match",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                picked.tracks.take(8).forEachIndexed { index, track ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreDnaCard(
    dna: List<GenreResolver.GenreSlice>,
    busyGenre: String?,
    onGenre: (String) -> Unit
) {
    val total = dna.sumOf { it.weight }.coerceAtLeast(1)
    GlassCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Your genre DNA", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Built from the artists you play most",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            dna.take(5).forEach { slice ->
                val fraction = slice.weight.toFloat() / total
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        slice.genre,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(112.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0.06f, 1f))
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dna.take(3).forEach { slice ->
                    Chip(
                        text = if (busyGenre == slice.genre) "Loading…" else "Start ${slice.genre}",
                        selected = busyGenre == slice.genre,
                        onClick = { onGenre(slice.genre) }
                    )
                }
            }
            if (busyGenre != null) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}
