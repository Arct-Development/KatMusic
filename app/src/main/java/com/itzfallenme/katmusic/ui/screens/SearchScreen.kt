package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.MediaCard
import com.itzfallenme.katmusic.data.Shelf
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.CardShelf
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.theme.ExpressiveSegmentedRow
import com.itzfallenme.katmusic.ui.theme.MorphingShapeLoader
import com.itzfallenme.katmusic.ui.theme.SmoothCornerShape
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import kotlinx.coroutines.delay

private enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

private data class Category(val label: String, val query: String, val colors: List<Color>)

private val searchCategories = listOf(
    Category("Pop", "pop hits", listOf(Color(0xFFF06A9B), Color(0xFF9A3FD9))),
    Category("Hip hop", "hip hop", listOf(Color(0xFFE07A2F), Color(0xFF7A2FD9))),
    Category("Lo-fi", "lofi chill mix", listOf(Color(0xFF4F7CAC), Color(0xFF2B3A67))),
    Category("Rock", "rock classics", listOf(Color(0xFFC0392B), Color(0xFF4A1B1B))),
    Category("Electronic", "electronic dance", listOf(Color(0xFF119DA4), Color(0xFF0C4A6E))),
    Category("Jazz", "jazz standards", listOf(Color(0xFFB58B4A), Color(0xFF4A3418))),
    Category("Bollywood", "bollywood hits", listOf(Color(0xFFE4572E), Color(0xFF7A1F3D))),
    Category("K-pop", "kpop hits", listOf(Color(0xFF7B61FF), Color(0xFFE94FA1)))
)

@Composable
fun SearchScreen(nav: Navigator) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var filterIndex by remember { mutableIntStateOf(0) }
    val filter = SearchFilter.entries[filterIndex]
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(260)
        suggestions = runCatching { YtMusic.suggestions(query) }.getOrDefault(emptyList())
    }

    fun runSearch(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        submitted = trimmed
        suggestions = emptyList()
        loading = true
        ServiceLocator.library.addRecentSearch(trimmed)
    }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        shelves = runCatching { YtMusic.search(submitted) }.getOrDefault(emptyList())
        loading = false
    }

    fun openCard(card: MediaCard) {
        if (card.kind == CardKind.SONG) {
            ServiceLocator.player.playTracks(
                listOf(Track(card.browseId, card.title, card.subtitle, artwork = card.artwork))
            )
        } else {
            nav.openDetail(card.browseId, card.title, card.kind, card.artwork, card.params)
        }
    }

    val filteredShelves = remember(shelves, filter) { filterShelves(shelves, filter) }
    val topTrack = remember(filteredShelves) {
        filteredShelves.firstNotNullOfOrNull { shelf -> shelf.tracks.firstOrNull() }
    }
    val topCard = remember(filteredShelves) {
        filteredShelves.firstNotNullOfOrNull { shelf -> shelf.cards.firstOrNull() }
    }
    val mode = when {
        query.isNotBlank() && suggestions.isNotEmpty() && submitted != query -> "suggestions"
        submitted.isBlank() -> "idle"
        else -> "results"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
        }

        SearchBar(
            query = query,
            onQueryChange = { value ->
                query = value
                if (value.isBlank()) {
                    submitted = ""
                    shelves = emptyList()
                }
            },
            onSubmit = { runSearch(query) },
            onClear = {
                query = ""
                submitted = ""
                shelves = emptyList()
                suggestions = emptyList()
            }
        )

        AnimatedVisibility(
            visible = mode == "results" && !loading,
            enter = fadeIn(tween(220)) + slideInVertically { -it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically { -it / 3 }
        ) {
            ExpressiveSegmentedRow(
                options = SearchFilter.entries.map { it.label },
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "searchBody",
            modifier = Modifier.weight(1f)
        ) { current ->
            when (current) {
                "suggestions" -> LazyColumn(Modifier.fillMaxSize()) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { runSearch(suggestion) }
                                .padding(horizontal = 22.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                suggestion,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                "idle" -> IdleSearch(
                    recent = library.recentSearches,
                    onSearch = { runSearch(it) },
                    onClearRecent = { ServiceLocator.library.clearRecentSearches() }
                )

                else -> {
                    if (loading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            MorphingShapeLoader(size = 34.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Searching for \u201C$submitted\u201D…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp)
                        ) {
                            if (topTrack != null || topCard != null) {
                                item {
                                    TopResult(
                                        track = topTrack,
                                        card = topCard,
                                        onPlayTrack = { track ->
                                            val queue = filteredShelves.flatMap { it.tracks }.distinctBy { it.id }
                                            ServiceLocator.player.playTracks(
                                                queue.ifEmpty { listOf(track) },
                                                queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                            )
                                        },
                                        onOpenCard = { openCard(it) }
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }
                            }

                            items(filteredShelves.size) { index ->
                                val shelf = filteredShelves[index]
                                if (shelf.tracks.isNotEmpty()) {
                                    Column {
                                        com.itzfallenme.katmusic.ui.SectionHeader(title = shelf.title ?: "Songs")
                                        shelf.tracks.take(10).forEachIndexed { i, track ->
                                            TrackRow(
                                                track = track,
                                                index = i + 1,
                                                isCurrent = playerState.track?.id == track.id,
                                                isPlaying = playerState.playing,
                                                onClick = { ServiceLocator.player.playTracks(shelf.tracks, i) },
                                                onStartRadio = { ServiceLocator.player.playRadio(it) },
                                                onPlayNext = { ServiceLocator.player.playNext(it) },
                                                onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                                                onToggleLike = { ServiceLocator.library.toggleLike(it) },
                                                onDownload = { ServiceLocator.downloads.download(it) },
                                                onGoToArtist = { t ->
                                                    t.artistId?.let { nav.openDetail(it, t.artist, CardKind.ARTIST) }
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(14.dp))
                                    }
                                } else {
                                    CardShelf(shelf = shelf, onCardClick = { openCard(it) })
                                    Spacer(Modifier.height(14.dp))
                                }
                            }

                            if (filteredShelves.isEmpty()) {
                                item {
                                    Column(
                                        Modifier.fillMaxWidth().padding(48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(38.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text("No ${filter.label.lowercase()} for \u201C$submitted\u201D")
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Try a different tab or a shorter query.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keeps shelves that contain something the active filter can show. */
private fun filterShelves(shelves: List<Shelf>, filter: SearchFilter): List<Shelf> {
    if (filter == SearchFilter.ALL) return shelves
    val kinds = when (filter) {
        SearchFilter.SONGS -> emptySet()
        SearchFilter.ALBUMS -> setOf(CardKind.ALBUM)
        SearchFilter.ARTISTS -> setOf(CardKind.ARTIST)
        SearchFilter.PLAYLISTS -> setOf(CardKind.PLAYLIST, CardKind.MIX, CardKind.SHOW)
        SearchFilter.ALL -> emptySet()
    }
    return shelves.mapNotNull { shelf ->
        if (filter == SearchFilter.SONGS) {
            if (shelf.tracks.isEmpty()) null else shelf.copy(cards = emptyList())
        } else {
            val cards = shelf.cards.filter { it.kind in kinds }
            if (cards.isEmpty()) null else shelf.copy(cards = cards, tracks = emptyList())
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val elevation by animateDpAsState(
        targetValue = if (query.isNotEmpty()) 6.dp else 0.dp,
        animationSpec = tween(240),
        label = "searchElevation"
    )
    Surface(
        shape = SmoothCornerShape(26.dp, 0.62f),
        color = scheme.surfaceContainerHigh,
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    AnimatedPlaceholder()
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = Brush.verticalGradient(listOf(scheme.primary, scheme.primary)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

/** Cycles the hint so the bar explains what it can find, SimpMusic-style. */
@Composable
private fun AnimatedPlaceholder() {
    val hints = remember { listOf("songs", "albums", "artists", "playlists", "moods") }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_400)
            index = (index + 1) % hints.size
        }
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically { it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically { -it } + fadeOut(tween(160)))
        },
        label = "placeholder"
    ) { position ->
        Text(
            "Search ${hints[position]}…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdleSearch(
    recent: List<String>,
    onSearch: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onClearRecent).padding(6.dp)
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { term -> Chip(text = term, onClick = { onSearch(term) }) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(6.dp)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Browse all", style = MaterialTheme.typography.titleMedium)
        }
        items(searchCategories) { category ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .graphicsLayerShape()
                    .background(Brush.linearGradient(category.colors))
                    .bounceClickable(targetScale = 0.96f) { onSearch(category.query) }
                    .padding(14.dp)
            ) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/** The first hit gets a large, tappable card — the SimpMusic "top result" idea. */
@Composable
private fun TopResult(
    track: Track?,
    card: MediaCard?,
    onPlayTrack: (Track) -> Unit,
    onOpenCard: (MediaCard) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val artwork = track?.artwork ?: card?.artwork.orEmpty()
    val title = track?.title ?: card?.title.orEmpty()
    val subtitle = track?.artist ?: card?.subtitle.orEmpty()
    val liked = track?.let { t -> ServiceLocator.library.isLiked(t.id) } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayerShape(30.dp)
            .background(scheme.surfaceContainerHigh)
            .clickable {
                if (track != null) onPlayTrack(track) else card?.let(onOpenCard)
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(url = artwork, modifier = Modifier.size(78.dp), corner = 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TOP RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (track != null) {
                IconButton(
                    onClick = { ServiceLocator.library.toggleLike(track) },
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayerClip(CircleShape)
                        .background(scheme.surfaceContainerHighest)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = if (liked) "Remove from liked" else "Add to liked",
                        tint = if (liked) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayerShape(16.dp, 0.65f)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Small helpers so the shape calls stay readable at the call sites. */
private fun Modifier.graphicsLayerShape(radius: Int = 26, smoothing: Float = 0.6f): Modifier =
    this.then(
        Modifier.graphicsLayer {
            shape = SmoothCornerShape(radius.dp, smoothing)
            clip = true
        }
    )

private fun Modifier.graphicsLayerShape(radius: androidx.compose.ui.unit.Dp, smoothing: Float = 0.6f): Modifier =
    this.then(
        Modifier.graphicsLayer {
            shape = SmoothCornerShape(radius, smoothing)
            clip = true
        }
    )

private fun Modifier.graphicsLayerClip(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(
        Modifier.graphicsLayer {
            this.shape = shape
            clip = true
        }
    )
