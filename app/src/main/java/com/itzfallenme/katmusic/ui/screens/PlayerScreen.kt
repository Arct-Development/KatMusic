package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.PlaylistPickerDialog
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.formatDuration
import com.itzfallenme.katmusic.ui.player.WavySeekBar
import kotlinx.coroutines.delay

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
    val liked = track != null && ServiceLocator.library.isLiked(track.id)
    val downloaded = track != null && ServiceLocator.library.downloadFor(track.id) != null

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

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = track.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background
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
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nav.playerOpen = false }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close player",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        track.album ?: "KatMusic radio",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Now playing", "Lyrics", "Up next").forEachIndexed { index, label ->
                    Chip(text = label, selected = tab == index, onClick = { tab = index })
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
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
                    else -> ArtworkPane(track = track, playing = state.playing)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                WavySeekBar(
                    progress = if (state.durationMs > 0) {
                        state.positionMs.toFloat() / state.durationMs
                    } else 0f,
                    onSeek = { ServiceLocator.player.seekToFraction(it) },
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    playing = state.playing
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(state.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDuration(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(onClick = { ServiceLocator.library.toggleLike(track) }) {
                                Icon(
                                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (liked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                track.artistId?.let { nav.openDetail(it, track.artist, CardKind.ARTIST) }
                            }
                        )
                    }
                    val shareContext = LocalContext.current
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ServiceLocator.player.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { ServiceLocator.player.previous() }) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { ServiceLocator.player.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.buffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(onClick = { ServiceLocator.player.next() }) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { ServiceLocator.player.cycleRepeat() }) {
                        Icon(
                            when (state.repeat) {
                                RepeatMode.ONE -> Icons.Filled.RepeatOne
                                RepeatMode.ALL -> Icons.Filled.Repeat
                                RepeatMode.OFF -> Icons.Filled.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (state.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { nav.queueOpen = true }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Queue")
                    }
                    IconButton(onClick = { sleepDialog = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepAtMs > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { speedDialog = true }) {
                        Text(
                            "${ServiceLocator.player.player.playbackParameters.speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { nav.go(com.itzfallenme.katmusic.ui.Screen.Equalizer) }) {
                        Icon(
                            Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (settings.equalizerEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        if (nav.queueOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { nav.queueOpen = false }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .navigationBarsPadding()
                    .padding(top = 14.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { nav.queueOpen = false }) { Text("Done") }
                }
                QueuePane(nav, height = 360.dp)
            }
        }
    }

    picker?.let { PlaylistPickerDialog(track = it, onDismiss = { picker = null }) }
}

@Composable
private fun ArtworkPane(track: Track, playing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
        ) {
            AsyncImage(
                model = track.artwork,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            )
            if (track.artwork.isBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -120)
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 30.dp)
        ) {
            items(synced.size) { index ->
                val line: LyricLine = synced[index]
                val active = index == activeIndex
                val distance = kotlin.math.abs(index - activeIndex)
                val alpha = when {
                    active -> 1f
                    distance == 1 -> 0.65f
                    distance == 2 -> 0.4f
                    else -> 0.25f
                }
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * scale,
                        fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold
                    ),
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
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
