package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itzfallenme.katmusic.shared.data.HomeCard
import com.itzfallenme.katmusic.shared.data.HomeFeedRepository
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import kotlinx.coroutines.launch

/**
 * The shared Home feed: real sections from the platform's [HomeFeedRepository]
 * (Android: InnerTube home shelves; desktop: Piped trending/music search).
 * Tapping a card plays it through the shared player; provider-side blocks
 * surface as player errors, never as silent dead rows.
 */
@Composable
fun HomeScreen(
    repository: HomeFeedRepository,
    playerQueue: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<HomeCard>?>(null) }
    // A card tap resolves through the repository (desktop: extractor tiers
    // take a moment; Android: immediate). While resolving, the card shows a
    // spinner; a refusal shows the provider's own reason.
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }

    fun playCard(card: HomeCard) {
        if (resolvingId != null) return
        if (card.isPlaylist) {
            resolveError = "Browsing “${card.title}” needs the playlist screen, which isn't shared yet."
            return
        }
        scope.launch {
            resolvingId = card.id
            resolveError = null
            repository.resolvePlayback(card)
                .onSuccess { track -> playerQueue(listOf(track), 0) }
                .onFailure { error -> resolveError = error.message ?: "Couldn't play this track" }
            resolvingId = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    searchResults = null
                    scope.launch { repository.refresh() }
                }, enabled = !state.isLoading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh feed")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search music…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            scope.launch {
                                searching = true
                                repository.searchMusic(query.trim())
                                    .onSuccess { searchResults = it }
                                    .onFailure { searchResults = emptyList() }
                                searching = false
                            }
                        }
                    },
                    enabled = query.isNotBlank() && !searching,
                ) {
                    Text("Search")
                }
            }

            searchResults?.let { results ->
                SectionRow(
                    title = "Results for “${query.trim()}”",
                    cards = results,
                    resolvingId = resolvingId,
                    onPlayCard = ::playCard,
                )
            }

            resolveError?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { resolveError = null }) { Text("Dismiss") }
                }
            }

            when {
                state.isLoading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading feed…", style = MaterialTheme.typography.bodyMedium)
                }
                state.error != null && state.sections.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Couldn't load the feed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { scope.launch { repository.refresh() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("  Try again")
                    }
                }
                else -> state.sections.forEach { section ->
                    SectionRow(
                        title = section.title,
                        cards = section.cards,
                        resolvingId = resolvingId,
                        onPlayCard = ::playCard,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    cards: List<HomeCard>,
    resolvingId: String?,
    onPlayCard: (HomeCard) -> Unit,
) {
    if (cards.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().adaptiveContentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                HomeCardView(
                    card = card,
                    isResolving = resolvingId == card.id,
                    onPlay = { onPlayCard(card) },
                )
            }
        }
    }
}

@Composable
private fun HomeCardView(
    card: HomeCard,
    isResolving: Boolean,
    onPlay: () -> Unit,
) {
    Card(
        onClick = onPlay,
        enabled = !isResolving,
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (card.artworkUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    AsyncImage(
                        model = card.artworkUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (card.isPlaylist) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Playlist",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    )
                }
                if (isResolving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.dp,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
