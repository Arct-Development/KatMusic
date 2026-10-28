package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.LyricLine
import com.itzfallenme.katmusic.data.Lyrics
import com.itzfallenme.katmusic.data.Track
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

    LaunchedEffect(track?.id, settings.lyricsEnabled) {
        val current = track ?: return@LaunchedEffect
        if (!settings.lyricsEnabled) return@LaunchedEffect
        lyricsLoading = true
        lyrics = YtMusic.lyricsFor(current)
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
                        scale = settings.lyricsTextScale
                    )
                    2 -> QueuePane(nav)
                    else -> ArtworkPane(
                        track = track,
                        playing = state.playing,
                        playingFraction = playing
                    )
                }
            }

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(30.dp),
                elevation = 20.dp,
                fillAlpha = 0.7f
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Column {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist.ifBlank { "Unknown artist" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable {
                                        track.artistId?.let {
                                            nav.openDetail(it, track.artist, CardKind.ARTIST)
                                        }
                                    }
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
                                .liquidGlass(shape = CircleShape, fillAlpha = 0.5f)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .liquidGlass(shape = CircleShape, fillAlpha = 0.36f)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                animationSpec = Motion.effectsDefault(),
                label = "tabPill"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(background)
                    .bounceClickable(targetScale = 0.95f) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun ArtworkPane(track: Track, playing: Boolean, playingFraction: Float) {
    val scheme = MaterialTheme.colorScheme
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
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    scaleX = scale * breath
                    scaleY = scale * breath
                }
                .shadow(26.dp, RoundedCornerShape(30.dp), spotColor = scheme.primary)
                .clip(RoundedCornerShape(30.dp))
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
    scale: Float
) {
    val listState = rememberLazyListState()
    val synced = lyrics?.synced.orEmpty()
    val activeIndex = synced.indexOfLast { it.timeMs <= positionMs + offsetMs }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching {
                listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -140)
            }
        }
    }

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
                "LRCLIB doesn't have ${track.title} yet.",
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
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 36.dp)
        ) {
            items(synced.size) { index ->
                val line: LyricLine = synced[index]
                LyricRow(
                    line = line,
                    active = index == activeIndex,
                    distance = abs(index - activeIndex),
                    scale = scale
                )
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, distance: Int, scale: Float) {
    val scheme = MaterialTheme.colorScheme
    val emphasis by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Motion.spatialDefault(),
        label = "lyricEmphasis"
    )
    val lineAlpha = when {
        active -> 1f
        distance == 1 -> 0.62f
        distance == 2 -> 0.4f
        else -> 0.26f
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize * scale * (1f + 0.05f * emphasis),
            fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold
        ),
        color = if (active) scheme.primary else scheme.onSurface.copy(alpha = lineAlpha),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = 10f * emphasis
                alpha = lineAlpha
            }
            .padding(vertical = 8.dp)
    )
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
