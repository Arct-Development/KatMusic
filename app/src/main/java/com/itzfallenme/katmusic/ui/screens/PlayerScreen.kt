package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.Lyrics
import com.itzfallenme.katmusic.ui.player.KaraokeLyrics
import com.itzfallenme.katmusic.data.LyricSyllable
import com.itzfallenme.katmusic.data.LyricsShareCard
import com.itzfallenme.katmusic.data.LyricsTranslation
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.WordLyricsApi
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.playback.RepeatMode
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.PlaylistPickerDialog
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.formatDuration
import com.itzfallenme.katmusic.ui.player.WavySeekBar
import com.itzfallenme.katmusic.ui.theme.AmbientBackdrop
import com.itzfallenme.katmusic.ui.theme.GlassIconButton
import com.itzfallenme.katmusic.ui.theme.GlassSurface
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import com.itzfallenme.katmusic.ui.theme.rememberArtworkSeed
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(nav: Navigator) {
    val state by ServiceLocator.player.state.collectAsState()
    val settings by ServiceLocator.settings.state.collectAsState()
    val track = state.track
    var tab by remember { mutableIntStateOf(0) }
    var sleepDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Track?>(null) }
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(track?.id) { mutableStateOf(false) }
    val libraryState by ServiceLocator.library.state.collectAsState()
    val liked = track != null && libraryState.liked.any { it.id == track.id }
    val downloaded = track != null && libraryState.downloads.any { it.track.id == track.id }
    val scheme = MaterialTheme.colorScheme

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragAlpha by animateFloatAsState(
        targetValue = 1f - (abs(dragOffset) / 1200f).coerceIn(0f, 0.35f),
        animationSpec = Motion.effectsFast(),
        label = "dragAlpha"
    )

    LaunchedEffect(track?.id, settings.lyricsEnabled, settings.wordByWordLyrics) {
        val current = track ?: return@LaunchedEffect
        if (!settings.lyricsEnabled) return@LaunchedEffect
        lyricsLoading = true
        val wordSynced = if (settings.wordByWordLyrics) {
            runCatching {
                WordLyricsApi.find(
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    durationSec = current.durationMs / 1000
                )
            }.getOrNull()
        } else null
        lyrics = wordSynced ?: YtMusic.lyricsFor(current)
        lyricsLoading = false
    }

    if (sleepDialog) {
        SleepTimerDialog(
            activeUntil = state.sleepAtMs,
            onDismiss = { sleepDialog = false },
            onSelect = { minutes ->
                ServiceLocator.player.setSleepTimer(minutes)
                sleepDialog = false
            },
            onCancelTimer = {
                ServiceLocator.player.cancelSleepTimer()
                sleepDialog = false
            }
        )
    }

    if (speedDialog) {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        AlertDialog(
            onDismissRequest = { speedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.forEach { speed ->
                        Chip(
                            text = if (speed == 1f) "1x" else "${speed}x",
                            selected = ServiceLocator.player.player.playbackParameters.speed == speed,
                            onClick = {
                                ServiceLocator.player.setSpeed(speed)
                                speedDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing playing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { nav.playerOpen = false }) { Text("Close") }
            }
        }
        return
    }

    val seed = rememberArtworkSeed(track.artwork, scheme.primary)
    val playing by animateFloatAsState(
        targetValue = if (state.playing) 1f else 0f,
        animationSpec = Motion.spatialDefault(),
        label = "playing"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset
                alpha = dragAlpha
                // Dragging down also pulls the whole page back into the
                // screen, so dismissing feels like the sheet is being peeled
                // off rather than sliding a static image away.
                val shrink = (1f - dragOffset / 2200f).coerceIn(0.88f, 1f)
                scaleX = shrink
                scaleY = shrink
            }
    ) {
        // Blurred, oversized artwork gives the page its colour; the ambient blobs keep it moving.
        AsyncImage(
            model = track.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(70.dp)
                .graphicsLayer { alpha = 0.55f }
        )
        AmbientBackdrop(accent = seed, intensity = 1.15f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.background.copy(alpha = 0.62f),
                            scheme.background.copy(alpha = 0.88f),
                            scheme.background.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta ->
                                if (delta > 0 || dragOffset > 0f) {
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset > 160f) nav.playerOpen = false
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Close player",
                    onClick = { nav.playerOpen = false },
                    size = 42.dp,
                    iconSize = 24.dp
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        track.album ?: "KatMusic radio",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    GlassIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "More",
                        onClick = { menu = true },
                        size = 42.dp
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = { menu = false; picker = track }
                        )
                        DropdownMenuItem(
                            text = { Text("Start radio") },
                            leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null) },
                            onClick = { menu = false; ServiceLocator.player.playRadio(track) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (downloaded) "Remove download" else "Download") },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menu = false
                                if (downloaded) ServiceLocator.downloads.delete(track.id)
                                else ServiceLocator.downloads.download(track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to artist") },
                            onClick = {
                                menu = false
                                track.artistId?.let {
                                    nav.openDetail(it, track.artist, CardKind.ARTIST)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Equalizer") },
                            leadingIcon = { Icon(Icons.Filled.Equalizer, contentDescription = null) },
                            onClick = { menu = false; nav.go(com.itzfallenme.katmusic.ui.Screen.Equalizer) }
                        )
                    }
                }
            }

            PlayerTabs(selected = tab, onSelect = { tab = it })

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(Motion.effectsDefault()) + scaleIn(Motion.spatialFast(), initialScale = 0.97f))
                        .togetherWith(fadeOut(Motion.effectsFast()))
                },
                modifier = Modifier.weight(1f),
                label = "playerTabs"
            ) { selected ->
                when (selected) {
                    1 -> LyricsPane(
                        track = track,
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        positionMs = state.positionMs,
                        offsetMs = settings.lyricsOffsetMs,
                        scale = settings.lyricsTextScale,
                        playing = state.playing
                    )
                    2 -> QueuePane(nav)
                    else -> ArtworkPane(
                        track = track,
                        playing = state.playing,
                        playingFraction = playing
                    )
                }
            }

            // The transport sits directly on the page, M3-style: a scrim that fades
            // the artwork out under the text instead of a floating glass card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                scheme.background.copy(alpha = 0.55f),
                                scheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .padding(bottom = 6.dp)
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Column {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 30.sp,
                                lineHeight = 36.sp
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist.ifBlank { "Unknown artist" },
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(scheme.surfaceContainerHigh)
                                    .clickable {
                                        track.artistId?.let {
                                            nav.openDetail(it, track.artist, CardKind.ARTIST)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            val shareContext = LocalContext.current
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .bounceClickable(targetScale = 0.86f) {
                                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.id}"
                                            )
                                        }
                                        runCatching {
                                            shareContext.startActivity(
                                                android.content.Intent.createChooser(share, "Share track")
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(16.dp),
                                    tint = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    WavySeekBar(
                        progress = if (state.durationMs > 0) {
                            state.positionMs.toFloat() / state.durationMs
                        } else 0f,
                        onSeek = { ServiceLocator.player.seekToFraction(it) },
                        activeColor = scheme.primary,
                        inactiveColor = scheme.onSurface.copy(alpha = 0.14f),
                        playing = state.playing
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatDuration(state.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(
                            icon = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            onClick = { ServiceLocator.player.toggleShuffle() },
                            size = 44.dp,
                            iconSize = 19.dp,
                            tint = if (state.shuffle) scheme.primary else scheme.onSurfaceVariant
                        )
                        GlassIconButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            onClick = { ServiceLocator.player.previous() },
                            size = 52.dp,
                            iconSize = 30.dp
                        )
                        PlayPauseButton(
                            playing = state.playing,
                            buffering = state.buffering,
                            onClick = { ServiceLocator.player.togglePlayPause() }
                        )
                        GlassIconButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            onClick = { ServiceLocator.player.next() },
                            size = 52.dp,
                            iconSize = 30.dp
                        )
                        GlassIconButton(
                            icon = if (state.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne
                            else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            onClick = { ServiceLocator.player.cycleRepeat() },
                            size = 44.dp,
                            iconSize = 19.dp,
                            tint = if (state.repeat != RepeatMode.OFF) scheme.primary
                            else scheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(
                            icon = Icons.Filled.QueueMusic,
                            contentDescription = "Queue",
                            onClick = { nav.queueOpen = true },
                            size = 40.dp,
                            iconSize = 19.dp,
                            tint = scheme.onSurfaceVariant
                        )
                        GlassIconButton(
                            icon = Icons.Filled.Favorite,
                            contentDescription = "Like",
                            onClick = { ServiceLocator.library.toggleLike(track) },
                            size = 40.dp,
                            iconSize = 19.dp,
                            tint = if (liked) scheme.primary else scheme.onSurfaceVariant
                        )
                        GlassIconButton(
                            icon = Icons.Filled.Timer,
                            contentDescription = "Sleep timer",
                            onClick = { sleepDialog = true },
                            size = 40.dp,
                            iconSize = 19.dp,
                            tint = if (state.sleepAtMs > 0) scheme.primary else scheme.onSurfaceVariant
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceContainerHigh)
                                .bounceClickable(targetScale = 0.9f) { speedDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${ServiceLocator.player.player.playbackParameters.speed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        GlassIconButton(
                            icon = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            onClick = { nav.go(com.itzfallenme.katmusic.ui.Screen.Equalizer) },
                            size = 40.dp,
                            iconSize = 19.dp,
                            tint = if (settings.equalizerEnabled) scheme.primary
                            else scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = nav.queueOpen,
            enter = fadeIn(Motion.effectsDefault()),
            exit = fadeOut(Motion.effectsFast())
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { nav.queueOpen = false }
            )
        }
        AnimatedVisibility(
            visible = nav.queueOpen,
            enter = slideInVertically(Motion.spatialDefault()) { it } + fadeIn(Motion.effectsFast()),
            exit = slideOutVertically(Motion.spatialFast()) { it } + fadeOut(Motion.effectsFast()),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        fillAlpha = 0.94f
                    )
                    .navigationBarsPadding()
                    .padding(top = 10.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = 0.25f))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { nav.queueOpen = false }) { Text("Done") }
                }
                QueuePane(nav, height = 380.dp)
            }
        }
    }

    picker?.let { PlaylistPickerDialog(track = it, onDismiss = { picker = null }) }
}

@Composable
private fun PlayerTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Now playing", "Lyrics", "Up next")
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / labels.size
        // The selected pill slides between tabs on a spatial spring instead of
        // cross-fading, which is the whole point of an M3 Expressive indicator.
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
            label = "tabIndicator"
        )
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(tabWidth)
                .height(38.dp)
                .clip(CircleShape)
                .background(scheme.primary)
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val isSelected = selected == index
                val tint by animateColorAsState(
                    targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    animationSpec = Motion.effectsFast(),
                    label = "tabTint"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(CircleShape)
                        .bounceClickable(targetScale = 0.95f) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "playRing")
    val sweepAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart
        ),
        label = "sweep"
    )
    Box(contentAlignment = Alignment.Center) {
        // Slowly sweeping light ring: the button reads as alive even when the
        // artwork is still loading.
        Box(
            Modifier
                .size(84.dp)
                .graphicsLayer { rotationZ = sweepAngle }
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            scheme.primary.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            Modifier
                .size(72.dp)
                .shadow(18.dp, CircleShape, ambientColor = scheme.primary, spotColor = scheme.primary)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.primaryContainer)
                    )
                )
                .bounceClickable(targetScale = 0.92f, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (buffering) {
                BufferingDots(color = scheme.onPrimary)
            } else {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(Motion.spatialFast(), initialScale = 0.6f) + fadeIn(Motion.effectsFast()))
                        .togetherWith(scaleOut(Motion.spatialFast(), targetScale = 0.6f) + fadeOut(Motion.effectsFast()))
                },
                label = "playPause"
            ) { isPlaying ->
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        }
    }
}

/** A record that keeps turning while music plays, tucked behind the sleeve. */
@Composable
private fun SpinningVinyl(playing: Boolean, modifier: Modifier = Modifier) {
    val angle = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            angle.animateTo(
                targetValue = angle.value + 360f,
                animationSpec = tween(26_000, easing = LinearEasing)
            )
        }
    }
    Canvas(modifier.graphicsLayer { rotationZ = angle.value }) {
        val center = this.center
        val radius = size.minDimension / 2f
        drawCircle(Color(0xFF07070A), radius = radius, center = center)
        var groove = radius * 0.95f
        val step = 5.dp.toPx()
        while (groove > radius * 0.32f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = groove,
                center = center,
                style = Stroke(width = 1f)
            )
            groove -= step
        }
        drawCircle(
            color = scheme.primary.copy(alpha = 0.12f),
            radius = radius * 0.3f,
            center = center
        )
        drawCircle(color = Color(0xFF101015), radius = radius * 0.26f, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = radius * 0.035f,
            center = center
        )
    }
}

/** Three dots that pulse in sequence while a stream is buffering. */
@Composable
private fun BufferingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "bufferDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart
        ),
        label = "dots"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val distance = abs(phase - index).coerceAtMost(1.5f)
            val scale = 0.6f + 0.4f * (1f - distance)
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.45f + 0.55f * scale
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun ArtworkPane(
    track: Track,
    playing: Boolean,
    playingFraction: Float
) {
    val scheme = MaterialTheme.colorScheme
    // One artwork treatment everywhere: a spinning vinyl behind a breathing
    // cover. There is no "classic" or "apple" variant to switch between.
    val corner = 30.dp
    val widthFraction = 0.9f
    val transition = rememberInfiniteTransition(label = "breathing")
    val breath by transition.animateFloat(
        initialValue = 0.995f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Reverse
        ),
        label = "breath"
    )
    val scale = 0.94f + 0.06f * playingFraction

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .offset(x = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            SpinningVinyl(playing = playing, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .graphicsLayer {
                    scaleX = scale * breath
                    scaleY = scale * breath
                }
                .shadow(26.dp, RoundedCornerShape(corner), spotColor = scheme.primary)
                .clip(RoundedCornerShape(corner))
        ) {
            AsyncImage(
                model = track.artwork,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            if (track.artwork.isBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f)
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite
                        )
                    )
            )
        }
    }
}

@Composable
private fun LyricsPane(
    track: Track,
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    offsetMs: Long,
    scale: Float,
    playing: Boolean
) {
    val synced = lyrics?.synced.orEmpty()
    val settings by ServiceLocator.settings.state.collectAsState()
    var subLines by remember(lyrics) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(synced, settings.romanizedLyrics) {
        if (!settings.romanizedLyrics || synced.isEmpty()) {
            subLines = null
            return@LaunchedEffect
        }
        subLines = runCatching {
            LyricsTranslation.romanize(synced)?.texts
        }.getOrNull()
    }

    // Frame-accurate clock: the player only publishes a position a few times a second,
    // which would make word highlighting stutter, so interpolate between updates.
    var smoothMs by remember { mutableLongStateOf(positionMs + offsetMs) }
    LaunchedEffect(positionMs, playing, offsetMs) {
        if (!playing) {
            smoothMs = positionMs + offsetMs
            return@LaunchedEffect
        }
        val base = positionMs + offsetMs
        val startedAt = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                smoothMs = base + (now - startedAt) / 1_000_000L
            }
        }
    }
    val activeIndex = synced.indexOfLast { it.timeMs <= smoothMs }

    when {
        loading -> LoadingLyrics()
        lyrics == null || lyrics.isEmpty -> Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No lyrics found", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "No synced lyrics available for ${track.title}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        !lyrics.isSynced -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp)
        ) {
            items(lyrics.plain.split("\n")) { line ->
                Text(
                    line.ifBlank { " " },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
            val backgroundArgb = MaterialTheme.colorScheme.background.toArgb()
            var sharing by remember { mutableStateOf(false) }
            var optionsOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lyrics.hasWordTiming) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Word-by-word • ${lyrics.source.ifBlank { "synced" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (lyrics.source.isNotBlank()) {
                    Text(
                        lyrics.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Lyrics options",
                    size = 36.dp,
                    iconSize = 17.dp,
                    onClick = { optionsOpen = true }
                )
                Spacer(Modifier.width(4.dp))
                GlassIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "Share lyrics",
                    size = 36.dp,
                    iconSize = 17.dp,
                    onClick = {
                        if (sharing) return@GlassIconButton
                        sharing = true
                        scope.launch {
                            val card = runCatching {
                                LyricsShareCard.render(
                                    context = context,
                                    track = track,
                                    lines = synced,
                                    activeIndex = activeIndex,
                                    accent = accentArgb,
                                    background = backgroundArgb
                                )
                            }.getOrNull()
                            sharing = false
                            if (card != null) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            LyricsShareCard.shareIntent(context, card),
                                            "Share lyrics"
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }
            KaraokeLyrics(
                lines = synced,
                positionMs = { smoothMs },
                isPlaying = playing,
                onSeek = { ServiceLocator.player.seekTo(it) },
                modifier = Modifier.fillMaxSize(),
                scale = scale,
                glow = settings.lyricsGlow,
                subLines = subLines,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurface
            )
            if (optionsOpen) {
                LyricsOptionsDialog(onDismiss = { optionsOpen = false })
            }
        }
    }
}

@Composable
private fun LoadingLyrics() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Finding lyrics…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueuePane(nav: Navigator, height: androidx.compose.ui.unit.Dp? = null) {
    val state by ServiceLocator.player.state.collectAsState()
    val modifier = if (height != null) Modifier.fillMaxWidth().height(height)
    else Modifier.fillMaxSize()
    if (state.queue.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(state.queue, key = { it.id + state.queue.indexOf(it) }) { track ->
            TrackRow(
                track = track,
                isCurrent = state.track?.id == track.id,
                isPlaying = state.playing,
                onClick = {
                    val index = state.queue.indexOfFirst { it.id == track.id }
                    if (index >= 0) {
                        ServiceLocator.player.player.seekTo(index, 0)
                        ServiceLocator.player.player.play()
                    }
                },
                onRemove = {
                    val index = state.queue.indexOfFirst { it.id == track.id }
                    if (index >= 0) ServiceLocator.player.removeFromQueue(index)
                },
                onToggleLike = { ServiceLocator.library.toggleLike(it) },
                onStartRadio = { ServiceLocator.player.playRadio(it) },
                onDownload = { ServiceLocator.downloads.download(it) }
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    activeUntil: Long,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (activeUntil > 0) {
                    val remaining = ((activeUntil - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                    Text(
                        "Playing for another $remaining min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { minutes ->
                        Chip(text = "$minutes min", onClick = { onSelect(minutes) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(45, 60, 90).forEach { minutes ->
                        Chip(text = "$minutes min", onClick = { onSelect(minutes) })
                    }
                }
            }
        },
        confirmButton = {
            if (activeUntil > 0) TextButton(onClick = onCancelTimer) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * Lyrics options, in place: the same controls the settings screen exposes, but
 * reachable while listening, with a live preview line so size and offset can be
 * judged against the song playing right now.
 */
@Composable
private fun LyricsOptionsDialog(onDismiss: () -> Unit) {
    val settings by ServiceLocator.settings.state.collectAsState()
    fun update(block: (com.itzfallenme.katmusic.data.AppSettings) -> com.itzfallenme.katmusic.data.AppSettings) {
        ServiceLocator.settings.update(block)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Every word lights up as it is sung",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * settings.lyricsTextScale
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lyric glow", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Halo follows the sung word",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.lyricsGlow,
                        onCheckedChange = { on -> update { it.copy(lyricsGlow = on) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Word-by-word", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Prefer TTML word timing when available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.wordByWordLyrics,
                        onCheckedChange = { on -> update { it.copy(wordByWordLyrics = on) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Romanised sub-lines", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show a Latin reading under each line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.romanizedLyrics,
                        onCheckedChange = { on -> update { it.copy(romanizedLyrics = on) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Text size: ${(settings.lyricsTextScale * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = settings.lyricsTextScale,
                    onValueChange = { value -> update { it.copy(lyricsTextScale = value) } },
                    valueRange = 0.7f..1.6f
                )
                Text(
                    "Timing offset: ${settings.lyricsOffsetMs} ms",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = settings.lyricsOffsetMs.toFloat(),
                    onValueChange = { value -> update { it.copy(lyricsOffsetMs = value.toLong()) } },
                    valueRange = -3000f..3000f
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
