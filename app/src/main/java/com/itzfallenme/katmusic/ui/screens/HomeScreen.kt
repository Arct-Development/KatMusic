package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.MediaCard
import com.itzfallenme.katmusic.data.Shelf
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.CardShelf
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.LoadingRow
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.formatDuration
import com.itzfallenme.katmusic.ui.theme.ExpressiveSegmentedRow
import com.itzfallenme.katmusic.ui.theme.GlassIconButton
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val homeMoods = listOf(
    "Chill" to "lofi chill mix",
    "Energize" to "energetic pop mix",
    "Focus" to "deep focus instrumental",
    "Party" to "party hits",
    "Sleep" to "calm sleep music",
    "Workout" to "workout motivation music"
)

@Composable
fun HomeScreen(nav: Navigator) {
    val shelves by YtMusic.home.collectAsState()
    val loading by YtMusic.homeLoading.collectAsState()
    val error by YtMusic.homeError.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val library by ServiceLocator.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var quickPick by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) { YtMusic.loadHome() }

    fun openCard(card: MediaCard) {
        if (card.kind == CardKind.SONG) {
            val track = Track(
                id = card.browseId,
                title = card.title,
                artist = card.subtitle,
                artwork = card.artwork
            )
            ServiceLocator.player.playTracks(listOf(track))
        } else {
            nav.openDetail(card.browseId, card.title, card.kind, card.artwork, card.params)
        }
    }

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night vibes"
        }
    }
    val listeningMinutes = library.history.sumOf { it.listenedMs } / 60_000
    val homeMixes = remember(library.history) { ServiceLocator.library.generatedMixes() }

    val listState = rememberLazyListState()
    val collapse by remember {
        derivedStateOf {
            (listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
        }
    }
    val headerScale = 1f - collapse * 0.14f

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                // M3 top app bar: the wordmark sits directly on the surface, with
                // trailing tonal icon buttons. No container card.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = headerScale
                                scaleY = headerScale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                    ) {
                        Text(
                            text = "KatMusic",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassIconButton(
                        icon = Icons.Filled.Explore,
                        contentDescription = "Discover",
                        onClick = { nav.go(Screen.Discover) },
                        size = 42.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "Search",
                        onClick = { nav.go(Screen.Search) },
                        size = 42.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = { nav.go(Screen.Settings) },
                        size = 42.dp
                    )
                }

                Spacer(Modifier.height(10.dp))

                ExpressiveSegmentedRow(
                    options = listOf("Chill", "Energize", "Focus", "Radio"),
                    selectedIndex = quickPick,
                    onSelect = { index ->
                        quickPick = index
                        if (index == 3) {
                            val seed = playerState.track ?: library.history.firstOrNull()?.track
                            if (seed != null) ServiceLocator.player.playRadio(seed)
                        } else {
                            val query = homeMoods.getOrNull(index)?.second ?: return@ExpressiveSegmentedRow
                            scope.launch {
                                val results = runCatching { YtMusic.search(query) }.getOrDefault(emptyList())
                                val tracks = results.flatMap { it.tracks }.distinctBy { it.id }
                                if (tracks.isNotEmpty()) {
                                    ServiceLocator.player.playTracks(tracks, 0)
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(14.dp))

                FloatingStatsCard(
                    minutes = listeningMinutes,
                    plays = library.history.size,
                    onOpenStats = { nav.go(Screen.Stats) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(18.dp))
            }
        }

        if (loading && shelves.isEmpty()) {
            item { LoadingRow("Tuning your feed…") }
        }

        if (error != null && shelves.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(error ?: "Something went wrong")
                    TextButton(onClick = { scope.launch { YtMusic.loadHome(force = true) } }) {
                        Text("Retry")
                    }
                }
            }
        }

        if (shelves.isNotEmpty()) {
            val hero = shelves.firstNotNullOfOrNull { shelf ->
                shelf.cards.firstOrNull { it.artwork.isNotBlank() }
            }
            if (hero != null) {
                item {
                    Reveal(index = 0) {
                        HeroCard(card = hero, onClick = { openCard(hero) })
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            items(shelves, key = { (it.title ?: "") + it.cards.size + it.tracks.size }) { shelf ->
                Reveal(index = shelves.indexOf(shelf) + 1) {
                    ShelfBlock(shelf = shelf, nav = nav, onCardClick = { openCard(it) })
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (homeMixes.isNotEmpty()) {
            item {
                val mixes = homeMixes
                Column {
                    com.itzfallenme.katmusic.ui.SectionHeader(
                        title = "Made for you",
                        subtitle = "Built from your listening"
                    )
                    LazyRowOfMixes(
                        mixes = mixes.map {
                            MediaCard(
                                title = it.name,
                                subtitle = it.description,
                                artwork = it.artwork,
                                browseId = it.id,
                                kind = CardKind.MIX
                            )
                        },
                        onClick = {
                            val mix = mixes.firstOrNull { m -> m.name == it.title }
                            if (mix != null) ServiceLocator.player.playTracks(mix.tracks)
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        item {
            Text(
                text = "KatMusic • made by ItzFallenMe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun LazyRowOfMixes(mixes: List<MediaCard>, onClick: (MediaCard) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(mixes, key = { it.browseId }) { card ->
            com.itzfallenme.katmusic.ui.MediaCardItem(card = card, onClick = { onClick(card) })
        }
    }
}

@Composable
private fun ShelfBlock(shelf: Shelf, nav: Navigator, onCardClick: (MediaCard) -> Unit) {
    if (shelf.cards.isNotEmpty()) {
        CardShelf(shelf = shelf, onCardClick = onCardClick)
    } else if (shelf.tracks.isNotEmpty()) {
        Column {
            com.itzfallenme.katmusic.ui.SectionHeader(title = shelf.title ?: "Quick picks")
            shelf.tracks.take(5).forEachIndexed { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    showArtwork = true,
                    onClick = { ServiceLocator.player.playTracks(shelf.tracks, index) },
                    onStartRadio = { ServiceLocator.player.playRadio(it) },
                    onPlayNext = { ServiceLocator.player.playNext(it) },
                    onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                    onToggleLike = { ServiceLocator.library.toggleLike(it) },
                    onDownload = { ServiceLocator.downloads.download(it) },
                    onGoToArtist = { track -> nav.go(Screen.Search) }
                )
            }
        }
    }
}

@Composable
private fun FloatingStatsCard(
    minutes: Long,
    plays: Int,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .bounceClickable(targetScale = 0.98f, onClick = onOpenStats)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = scheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your listening", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (plays == 0) "Play something to start your story"
                    else "$minutes min • $plays plays tracked",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = scheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HeroCard(card: MediaCard, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(196.dp)
            .graphicsLayer {
                shape = com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(32.dp, 0.6f)
                clip = true
            }
            .bounceClickable(targetScale = 0.975f, onClick = onClick)
    ) {
        AsyncImage(
            model = card.artwork,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.96f }
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        shape = com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(18.dp, 0.65f)
                        clip = true
                    }
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressOrPlay()
            }
        }
    }
}

@Composable
private fun CircularProgressOrPlay() {
    Icon(
        Icons.Filled.PlayArrow,
        contentDescription = "Play",
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(26.dp)
    )
}

@Composable
fun DurationLabel(ms: Long) {
    Text(formatDuration(ms), style = MaterialTheme.typography.bodySmall)
}

/** Fades and lifts content in once, slightly after the item above it. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(6) * 45).toLong())
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(Motion.effectsDefault()) + slideInVertically(Motion.spatialDefault()) { it / 10 }
    ) {
        content()
    }
}
