/*
 * Adapted portions of the Poster, Timeline and expressive title player styles from Mochy Music.
 * Original project: https://github.com/InlitX/Mochy-Music
 * Original style sources and GPL-3.0 license: third_party/Mochy-Music/
 *
 * Modified for KatMusic on 2026-10-31: player controls, track metadata, lyrics state and seeking
 * are connected to KatMusic's MusicPlayer and LyricsUiState APIs. This adapted file is licensed
 * under GNU GPL v3.0; see the repository LICENSE file.
 */
package com.itzfallenme.katmusic.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.MusicPlayerState
import com.itzfallenme.katmusic.playback.PlaybackProgressState
import com.itzfallenme.katmusic.data.canvas.CanvasArtwork
import com.itzfallenme.katmusic.data.lyrics.isRtlText
import com.itzfallenme.katmusic.data.lyrics.LyricLine
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** Independent presentation presets; playback remains owned by MusicPlayer. */
enum class KatPlayerStyle(val label: String, val description: String) {
    DEFAULT("Classic", "KatMusic's full player with tabs, queue and advanced controls"),
    POSTER("Poster", "Oversized circular cover art with expressive, synchronized lyrics"),
    TIMELINE("Timeline", "A colour-split playhead layout with oversized elapsed and total time"),
    LYRICS("Lyrics", "Mochy-inspired expressive lyrics using KatMusic's synced lyric engine"),
    ARTWORK("Artwork", "Large album artwork with focused playback controls"),
    VIDEO("Canvas", "Immersive animated Canvas artwork as the player background"),
    VINYL("Vinyl", "A rotating record deck with a progress-etched LP and physical controls"),
    CASSETTE("Cassette", "A tactile tape deck with animated reels and a printed track label");

    companion object {
        fun fromStored(value: String?): KatPlayerStyle = when (value) {
            "IPOD" -> DEFAULT
            else -> entries.firstOrNull { it.name == value } ?: DEFAULT
        }
    }
}

internal object KatPlayerStylePreferences {
    const val FILE_NAME = "katmusic_player_style"
    const val KEY_STYLE = "selected_style"
}

@Composable
internal fun AlternateKatPlayerScreen(
    state: MusicPlayerState,
    player: MusicPlayer,
    style: KatPlayerStyle,
    onCollapse: () -> Unit,
    lyricsState: LyricsUiState,
    progressState: StateFlow<PlaybackProgressState>,
    canvas: CanvasArtwork?,
    lyricsOffsetMs: Long,
    lyricsFontScale: Float,
    lyricsButtonsAutoHideSeconds: Int,
    translationLanguage: String,
    canvasEnabled: Boolean,
    wavySeekbarEnabled: Boolean,
    onRetryLyrics: () -> Unit,
    onSetLyricsFontScale: (Float) -> Unit,
    onSelectLyricsProvider: (com.itzfallenme.katmusic.data.local.LyricsProvider) -> Unit,
    onTryAnotherLyricsSource: () -> Unit,
) {
    if (state.current == null) return
    if (style == KatPlayerStyle.VINYL) {
        VinylDeckPlayer(
            state = state,
            player = player,
            onCollapse = onCollapse,
        )
        return
    }
    if (style == KatPlayerStyle.CASSETTE) {
        CassetteDeckPlayer(
            state = state,
            player = player,
            onCollapse = onCollapse,
        )
        return
    }
    when (style) {
        KatPlayerStyle.POSTER -> {
            PosterStylePlayer(
                state = state,
                player = player,
                lyricsState = lyricsState,
                lyricsOffsetMs = lyricsOffsetMs,
                onCollapse = onCollapse,
            )
            return
        }
        KatPlayerStyle.TIMELINE -> {
            TimelineStylePlayer(state, player, onCollapse)
            return
        }
        KatPlayerStyle.LYRICS -> {
            LyricsStylePlayer(
                state = state,
                player = player,
                lyricsState = lyricsState,
                lyricsOffsetMs = lyricsOffsetMs,
                onCollapse = onCollapse,
            )
            return
        }
        KatPlayerStyle.ARTWORK -> {
            GalleryStylePlayer(state, player, onCollapse)
            return
        }
        KatPlayerStyle.VIDEO -> {
            CanvasStylePlayer(
                state = state,
                player = player,
                canvas = canvas,
                canvasEnabled = canvasEnabled,
                onCollapse = onCollapse,
            )
            return
        }
        KatPlayerStyle.DEFAULT, KatPlayerStyle.VINYL, KatPlayerStyle.CASSETTE -> Unit
    }
    // Classic is rendered by PlayerHost; this surface only owns alternate styles.

}

@Composable
private fun PosterStylePlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    lyricsState: LyricsUiState,
    lyricsOffsetMs: Long,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.tertiary,
            isPlaying = state.isPlaying,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("POSTER", onCollapse)
            Spacer(Modifier.height(6.dp))
            PosterPresentation(
                artwork = track.artworkUrl,
                title = track.title,
                artist = track.artist,
                lyricsState = lyricsState,
                positionMs = state.positionMs,
                lyricsOffsetMs = lyricsOffsetMs,
                progress = progress,
                isPlaying = state.isPlaying,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (track.album ?: "").ifBlank { "SINGLE" }.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "COVER STORY",
                        color = Color.White.copy(alpha = 0.46f),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                    )
                }
                Text(
                    "${state.currentIndex + 1} / ${state.queue.size.coerceAtLeast(1)}",
                    color = Color.White.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                )
            }
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            PosterTransport(state, player)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TimelineStylePlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = accent,
            secondary = Color(0xFF69D6FF),
            isPlaying = state.isPlaying,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("TIMELINE", onCollapse)
            Spacer(Modifier.height(12.dp))
            TimelinePresentation(
                artwork = track.artworkUrl,
                title = track.title,
                artist = track.artist,
                positionMs = state.positionMs,
                durationMs = duration,
                progress = progress,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "PLAYHEAD",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (track.album ?: "").ifBlank { "NOW PLAYING" }.uppercase(),
                    color = Color.White.copy(alpha = 0.50f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = accent,
            )
            Spacer(Modifier.height(14.dp))
            TimelineTransport(state, player, accent)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun LyricsStylePlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    lyricsState: LyricsUiState,
    lyricsOffsetMs: Long,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = Color(0xFFAA8CFF),
            secondary = MaterialTheme.colorScheme.primary,
            isPlaying = state.isPlaying,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("LYRIC STAGE", onCollapse)
            Spacer(Modifier.height(12.dp))
            LyricsStagePresentation(
                artwork = track.artworkUrl,
                title = track.title,
                artist = track.artist,
                lyricsState = lyricsState,
                positionMs = state.positionMs,
                lyricsOffsetMs = lyricsOffsetMs,
                progress = progress,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(Color(0xFFAA8CFF), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (lyricsState is LyricsUiState.Success) "LYRICS SYNCED" else "LYRIC STAGE",
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    track.title,
                    color = Color.White.copy(alpha = 0.52f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = Color(0xFFAA8CFF),
            )
            Spacer(Modifier.height(6.dp))
            LyricsTransport(state, player)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun GalleryStylePlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = Color(0xFFFFC978),
            secondary = MaterialTheme.colorScheme.primary,
            isPlaying = state.isPlaying,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("ARTWORK", onCollapse)
            Spacer(Modifier.height(14.dp))
            GalleryArtworkPresentation(
                artwork = track.artworkUrl,
                title = track.title,
                artist = track.artist,
                album = track.album.orEmpty(),
                isPlaying = state.isPlaying,
            )
            Spacer(Modifier.height(20.dp))
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = Color(0xFFFFC978),
            )
            Spacer(Modifier.height(10.dp))
            GalleryTransport(state, player)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun CanvasStylePlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    canvas: CanvasArtwork?,
    canvasEnabled: Boolean,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (canvasEnabled && canvas != null) {
            CanvasArtworkPlayer(
                canvas = canvas,
                isPlaying = state.isPlaying,
                modifier = Modifier.fillMaxSize(),
                bottomFade = 0.34f,
            )
        } else {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), false)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.38f to Color.Transparent,
                        0.62f to Color(0xFF080A0F).copy(alpha = 0.44f),
                        1f to Color(0xFF080A0F).copy(alpha = 0.98f),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            PlayerStyleHeader("CANVAS", onCollapse)
            Spacer(Modifier.height(this@BoxWithConstraints.maxHeight * 0.28f))
            Text(
                if (canvasEnabled && canvas != null) "VISUALIZER ACTIVE" else "ARTWORK MOTION",
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                track.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(track.artist, track.album).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = accent,
            )
            Spacer(Modifier.height(8.dp))
            CanvasTransport(state, player, accent)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PosterTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF0A0C11).copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.58f),
            )
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
        }
        FilledIconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF10131A),
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = player::next, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
        }
        IconButton(onClick = player::cycleRepeatMode) {
            Icon(
                if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Cycle repeat mode",
                tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun TimelineTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101722).copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (state.isPlaying) "LIVE PLAYHEAD" else "PLAYBACK PAUSED",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    "${state.currentIndex + 1} / ${state.queue.size.coerceAtLeast(1)}",
                    color = Color.White.copy(alpha = 0.48f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) accent else Color.White.copy(alpha = 0.56f),
                    )
                }
                IconButton(onClick = player::previous, modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                FilledIconButton(
                    onClick = player::togglePlayPause,
                    modifier = Modifier.size(62.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF071116),
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
                }
                IconButton(onClick = player::cycleRepeatMode) {
                    Icon(
                        if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Cycle repeat mode",
                        tint = if (state.repeatMode != 0) accent else Color.White.copy(alpha = 0.56f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171322).copy(alpha = 0.90f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) Color(0xFFAA8CFF) else Color.White.copy(alpha = 0.55f),
            )
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
        }
        FilledIconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFFAA8CFF),
                contentColor = Color(0xFF151020),
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = player::next, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
        }
        IconButton(onClick = player::cycleRepeatMode) {
            Icon(
                if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Cycle repeat mode",
                tint = if (state.repeatMode != 0) Color(0xFFAA8CFF) else Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun GalleryTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF4E7CC))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) Color(0xFF168BFF) else Color(0xFF5B5D5E),
            )
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color(0xFF141820))
        }
        FilledIconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(62.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF168BFF),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = player::next, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color(0xFF141820))
        }
        IconButton(onClick = player::cycleRepeatMode) {
            Icon(
                if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Cycle repeat mode",
                tint = if (state.repeatMode != 0) Color(0xFF168BFF) else Color(0xFF5B5D5E),
            )
        }
    }
}

@Composable
private fun CanvasTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) accent else Color.White.copy(alpha = 0.68f),
            )
        }
        IconButton(onClick = player::previous, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
        }
        Surface(
            onClick = player::togglePlayPause,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.94f),
            contentColor = Color(0xFF10131A),
            modifier = Modifier.size(66.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        IconButton(onClick = player::next, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
        }
        IconButton(onClick = player::cycleRepeatMode) {
            Icon(
                if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Cycle repeat mode",
                tint = if (state.repeatMode != 0) accent else Color.White.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun GalleryArtworkPresentation(
    artwork: String?,
    title: String,
    artist: String,
    album: String,
    isPlaying: Boolean,
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 110f),
        label = "gallery-artwork-lift",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .aspectRatio(0.84f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFD990), Color(0xFFF1B85D), Color(0xFFB7783F)),
                ),
                cornerRadius = CornerRadius(25.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.33f),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                cornerRadius = CornerRadius(25.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF7F0DE),
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "KATMUSIC ARCHIVE",
                        color = Color(0xFF34363B),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.8.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "A / 01",
                        color = Color(0xFF168BFF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }
                Artwork(
                    artwork,
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = Color(0xFF17191E),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            artist,
                            color = Color(0xFF5F6265),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        album.takeIf(String::isNotBlank)?.uppercase() ?: "SIDE A",
                        color = Color(0xFF5F6265),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VinylDeckPlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    val rotation = remember(trackKey) { Animatable(0f) }
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }
    val accent = Color(0xFF25DCE8)

    LaunchedEffect(trackKey, state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 12_000, easing = LinearEasing),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = MaterialTheme.colorScheme.primary,
            secondary = accent,
            isPlaying = state.isPlaying,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("VINYL DECK", onCollapse)
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 390.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val discSize = maxWidth * 0.92f
                Box(Modifier.size(discSize), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotation.value },
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension / 2f
                        drawCircle(Color(0xFF090B10), radius = radius)
                        repeat(22) { index ->
                            val grooveRadius = radius * (0.43f + index * 0.025f)
                            drawCircle(
                                color = Color.White.copy(alpha = if (index % 3 == 0) 0.13f else 0.07f),
                                radius = grooveRadius,
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        repeat(72) { index ->
                            val angle = index * (360f / 72f) - 90f
                            val radians = Math.toRadians(angle.toDouble())
                            val inner = radius * 0.94f
                            val outer = radius * 0.985f
                            val start = Offset(
                                center.x + kotlin.math.cos(radians).toFloat() * inner,
                                center.y + kotlin.math.sin(radians).toFloat() * inner,
                            )
                            val end = Offset(
                                center.x + kotlin.math.cos(radians).toFloat() * outer,
                                center.y + kotlin.math.sin(radians).toFloat() * outer,
                            )
                            drawLine(
                                color = if (index / 72f < progress) accent.copy(alpha = 0.82f)
                                else Color.White.copy(alpha = 0.18f),
                                start = start,
                                end = end,
                                strokeWidth = if (index % 6 == 0) 2.dp.toPx() else 1.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                        drawArc(
                            color = accent.copy(alpha = 0.20f),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(radius * 0.025f, radius * 0.025f),
                            size = Size(size.width * 0.95f, size.height * 0.95f),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        // The tonearm is a fixed part of the deck, not the rotating record.
                    }
                    Artwork(
                        track.artworkUrl,
                        Modifier
                            .size(discSize * 0.38f)
                            .graphicsLayer { rotationZ = rotation.value }
                            .clip(CircleShape),
                        false,
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF12151B), CircleShape)
                            .align(Alignment.Center),
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val start = Offset(size.width * 0.86f, size.height * 0.12f)
                        val elbow = Offset(size.width * 0.77f, size.height * 0.18f)
                        val needle = Offset(size.width * 0.70f, size.height * 0.32f)
                        drawCircle(Color(0xFFBBC4CE), radius = 8.dp.toPx(), center = start)
                        drawLine(
                            Color(0xFFBBC4CE),
                            start = start,
                            end = elbow,
                            strokeWidth = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            Color(0xFFBBC4CE),
                            start = elbow,
                            end = needle,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawCircle(accent, radius = 4.dp.toPx(), center = needle)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Text(
                    if (state.isPlaying) "SIDE A · SPINNING" else "SIDE A · PAUSED",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.6.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                track.title,
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.7).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(track.artist, track.album).filter { it.isNotBlank() }.joinToString("  /  "),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = accent,
            )
            Spacer(Modifier.height(8.dp))
            VinylTransport(
                state = state,
                player = player,
                accent = accent,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CassetteDeckPlayer(
    state: MusicPlayerState,
    player: MusicPlayer,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, track.durationMs ?: 0L).coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val trackKey = track.videoId ?: "${track.artist}|${track.title}"
    val reelRotation = remember(trackKey) { Animatable(0f) }
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }
    val shell = Color(0xFF168BFF)
    val ink = Color(0xFF111820)

    LaunchedEffect(trackKey, state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                reelRotation.animateTo(
                    targetValue = reelRotation.value + 360f,
                    animationSpec = tween(durationMillis = 2_400, easing = LinearEasing),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = Color(0xFF25DCE8),
            secondary = Color(0xFFFF9D69),
            isPlaying = state.isPlaying,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStyleHeader("TAPE DECK", onCollapse)
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .aspectRatio(1.42f)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val corner = CornerRadius(26.dp.toPx())
                    drawRoundRect(
                        color = shell,
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.20f),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = corner,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color(0xFFF4E8C7),
                        topLeft = Offset(size.width * 0.07f, size.height * 0.08f),
                        size = Size(size.width * 0.86f, size.height * 0.30f),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color(0xFF101820),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.44f),
                        size = Size(size.width * 0.84f, size.height * 0.39f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                    val reelY = size.height * 0.63f
                    val reelRadius = size.minDimension * 0.115f
                    val leftReel = Offset(size.width * 0.32f, reelY)
                    val rightReel = Offset(size.width * 0.68f, reelY)
                    listOf(leftReel, rightReel).forEach { center ->
                        drawCircle(Color(0xFFB7C4CB), radius = reelRadius, center = center)
                        drawCircle(Color(0xFF202B32), radius = reelRadius * 0.72f, center = center)
                        drawCircle(Color(0xFFD2D8D3), radius = reelRadius * 0.20f, center = center)
                        rotate(reelRotation.value, pivot = center) {
                            repeat(6) { spoke ->
                                val radians = Math.toRadians(spoke * 60.0)
                                val innerRadius = reelRadius * 0.30f
                                val outerRadius = reelRadius * 0.65f
                                drawLine(
                                    color = Color(0xFFB7C4CB),
                                    start = Offset(
                                        center.x + kotlin.math.cos(radians).toFloat() * innerRadius,
                                        center.y + kotlin.math.sin(radians).toFloat() * innerRadius,
                                    ),
                                    end = Offset(
                                        center.x + kotlin.math.cos(radians).toFloat() * outerRadius,
                                        center.y + kotlin.math.sin(radians).toFloat() * outerRadius,
                                    ),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                    drawLine(
                        color = Color(0xFFDA8F55),
                        start = Offset(leftReel.x + reelRadius * 0.88f, reelY),
                        end = Offset(rightReel.x - reelRadius * 0.88f, reelY),
                        strokeWidth = 3.dp.toPx(),
                    )
                    // Tape-window markings and the little pressure pad make the object read as a cassette.
                    drawRoundRect(
                        color = Color(0xFFE7E9E2),
                        topLeft = Offset(size.width * 0.46f, size.height * 0.75f),
                        size = Size(size.width * 0.08f, size.height * 0.055f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    repeat(5) { mark ->
                        val x = size.width * (0.18f + mark * 0.16f)
                        drawLine(
                            color = Color(0xFF6CA1C4),
                            start = Offset(x, size.height * 0.89f),
                            end = Offset(x, size.height * 0.95f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(
                            start = maxWidth * 0.12f,
                            end = maxWidth * 0.12f,
                            top = maxHeight * 0.105f,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        track.title,
                        color = ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist.uppercase(),
                        color = ink.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (state.isPlaying) "KATMUSIC  •  PLAYING" else "KATMUSIC  •  PAUSED",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = maxHeight * 0.055f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "NOW ON THE DECK",
                        color = Color(0xFF25DCE8),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.4.sp,
                    )
                    Text(
                        track.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (state.isPlaying) "SIDE A" else "PAUSED",
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            PlayerStyleSeekbar(
                progress = progress,
                positionMs = state.positionMs,
                durationMs = duration,
                scrubPosition = scrubPosition,
                onScrub = { scrubPosition = it },
                onSeek = {
                    scrubPosition?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }
                    scrubPosition = null
                },
                accent = Color(0xFF25DCE8),
            )
            Spacer(Modifier.height(8.dp))
            CassetteTransport(
                state = state,
                player = player,
                accent = Color(0xFF25DCE8),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlayerStyleHeader(
    label: String,
    onCollapse: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Collapse player", tint = Color.White)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "KATMUSIC",
                color = Color.White.copy(alpha = 0.42f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.2.sp,
            )
            Text(
                label,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.4.sp,
            )
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun PlayerStyleSeekbar(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    scrubPosition: Float?,
    onScrub: (Float?) -> Unit,
    onSeek: () -> Unit,
    accent: Color,
) {
    val seekProgress = scrubPosition ?: progress
    Slider(
        value = seekProgress.coerceIn(0f, 1f),
        onValueChange = { onScrub(it) },
        onValueChangeFinished = onSeek,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatPlayerTime((scrubPosition?.times(durationMs)?.toLong()) ?: positionMs),
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            formatPlayerTime(durationMs),
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun VinylTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
    accent: Color,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = if (state.shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                tint = if (state.shuffleEnabled) accent else Color.White.copy(alpha = 0.55f),
            )
        }
        IconButton(
            onClick = player::previous,
            modifier = Modifier
                .size(54.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape),
        ) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        FilledIconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent,
                contentColor = Color(0xFF071116),
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(
            onClick = player::next,
            modifier = Modifier
                .size(54.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape),
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = player::cycleRepeatMode) {
            Icon(
                if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Cycle repeat mode",
                tint = if (state.repeatMode != 0) accent else Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CassetteTransport(
    state: MusicPlayerState,
    player: MusicPlayer,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TapeTransportKey(
                label = "REW",
                icon = Icons.Filled.SkipPrevious,
                onClick = player::previous,
                modifier = Modifier.weight(1f),
                accent = accent,
            )
            TapeTransportKey(
                label = if (state.isPlaying) "PAUSE" else "PLAY",
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = player::togglePlayPause,
                modifier = Modifier.weight(1.2f),
                accent = accent,
                prominent = true,
            )
            TapeTransportKey(
                label = "F.FWD",
                icon = Icons.Filled.SkipNext,
                onClick = player::next,
                modifier = Modifier.weight(1f),
                accent = accent,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { player.setShuffleEnabled(!state.shuffleEnabled) }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = if (state.shuffleEnabled) accent else Color.White.copy(alpha = 0.58f),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (state.shuffleEnabled) "SHUFFLE ON" else "SHUFFLE",
                    color = if (state.shuffleEnabled) accent else Color.White.copy(alpha = 0.64f),
                    letterSpacing = 1.2.sp,
                )
            }
            TextButton(onClick = player::cycleRepeatMode) {
                Icon(
                    if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = if (state.repeatMode != 0) accent else Color.White.copy(alpha = 0.58f),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (state.repeatMode != 0) "REPEAT ON" else "REPEAT",
                    color = if (state.repeatMode != 0) accent else Color.White.copy(alpha = 0.64f),
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

@Composable
private fun TapeTransportKey(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color,
    prominent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (prominent) 18.dp else 13.dp),
        color = if (prominent) accent else Color(0xFF202C38),
        contentColor = if (prominent) Color(0xFF071116) else Color.White,
        tonalElevation = if (prominent) 4.dp else 0.dp,
        modifier = modifier.height(58.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
            Spacer(Modifier.size(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
        }
    }
}

/** Small, genuinely different silhouettes for the settings style picker. */
@Composable
internal fun PlayerStylePreview(
    style: KatPlayerStyle,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10141B)),
    ) {
        val w = size.width
        val h = size.height
        when (style) {
            KatPlayerStyle.DEFAULT -> {
                drawRoundRect(
                    color = accent.copy(alpha = 0.74f),
                    topLeft = Offset(w * 0.12f, h * 0.14f),
                    size = Size(w * 0.76f, h * 0.72f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                drawCircle(Color.White, radius = h * 0.13f, center = Offset(w * 0.50f, h * 0.50f))
            }
            KatPlayerStyle.POSTER -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.85f), Color(0xFF10141B)),
                    ),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.70f),
                    radius = h * 0.25f,
                    center = Offset(w * 0.50f, h * 0.35f),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.52f),
                    topLeft = Offset(w * 0.16f, h * 0.74f),
                    size = Size(w * 0.68f, h * 0.04f),
                )
            }
            KatPlayerStyle.VIDEO -> {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF102348),
                            accent.copy(alpha = 0.82f),
                            Color(0xFF18203A),
                        ),
                    ),
                )
                repeat(3) { ring ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f - ring * 0.035f),
                        radius = h * (0.17f + ring * 0.10f),
                        center = Offset(w * 0.69f, h * 0.34f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawRoundRect(
                    color = Color(0xFF080B12).copy(alpha = 0.72f),
                    topLeft = Offset(w * 0.08f, h * 0.69f),
                    size = Size(w * 0.84f, h * 0.20f),
                    cornerRadius = CornerRadius(7.dp.toPx()),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.72f),
                    topLeft = Offset(w * 0.14f, h * 0.76f),
                    size = Size(w * 0.46f, h * 0.035f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            KatPlayerStyle.TIMELINE -> {
                drawRect(Color(0xFF131B27))
                drawRect(
                    color = accent.copy(alpha = 0.72f),
                    size = Size(w * 0.52f, h),
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.52f, 0f),
                    end = Offset(w * 0.52f, h),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            KatPlayerStyle.LYRICS -> {
                drawRoundRect(
                    color = accent.copy(alpha = 0.18f),
                    topLeft = Offset(w * 0.1f, h * 0.12f),
                    size = Size(w * 0.80f, h * 0.76f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                repeat(3) { line ->
                    val lineWidth = if (line == 1) 0.70f else 0.48f
                    drawRoundRect(
                        color = if (line == 1) Color.White else Color.White.copy(alpha = 0.42f),
                        topLeft = Offset(w * 0.16f, h * (0.30f + line * 0.18f)),
                        size = Size(w * lineWidth, h * 0.055f),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
            KatPlayerStyle.ARTWORK -> {
                drawRoundRect(
                    color = accent.copy(alpha = 0.18f),
                    topLeft = Offset(w * 0.18f, h * 0.07f),
                    size = Size(w * 0.64f, h * 0.66f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent, Color(0xFF10141B))),
                    radius = h * 0.26f,
                    center = Offset(w * 0.50f, h * 0.38f),
                )
            }
            KatPlayerStyle.VINYL -> {
                drawCircle(
                    color = Color(0xFF080A0D),
                    radius = h * 0.40f,
                    center = Offset(w * 0.50f, h * 0.50f),
                )
                repeat(4) { ring ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.20f),
                        radius = h * (0.20f + ring * 0.055f),
                        center = Offset(w * 0.50f, h * 0.50f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(accent, radius = h * 0.13f, center = Offset(w * 0.50f, h * 0.50f))
            }
            KatPlayerStyle.CASSETTE -> {
                drawRoundRect(
                    color = Color(0xFF168BFF),
                    topLeft = Offset(w * 0.10f, h * 0.13f),
                    size = Size(w * 0.80f, h * 0.72f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFF101820),
                    topLeft = Offset(w * 0.18f, h * 0.46f),
                    size = Size(w * 0.64f, h * 0.28f),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                )
                drawCircle(Color(0xFFB7C4CB), h * 0.10f, Offset(w * 0.36f, h * 0.60f))
                drawCircle(Color(0xFFB7C4CB), h * 0.10f, Offset(w * 0.64f, h * 0.60f))
            }
        }
    }
}

@Composable
private fun AmbientBackdrop(
    accent: Color,
    secondary: Color,
    isPlaying: Boolean,
) {
    val motion = rememberInfiniteTransition(label = "player-ambient")
    val sway by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ambient-sway",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(6200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ambient-pulse",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF07080C))
        val radius = size.maxDimension * 0.72f
        val strength = if (isPlaying) 0.34f else 0.20f
        val warm = Offset(
            x = size.width * (0.08f + 0.22f * sway),
            y = size.height * (0.10f + 0.12f * sway),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = strength), Color.Transparent),
                center = warm,
                radius = radius * pulse,
            ),
            radius = radius * pulse,
            center = warm,
        )
        val cool = Offset(
            x = size.width * (0.92f - 0.18f * sway),
            y = size.height * (0.70f + 0.08f * sway),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = strength * 0.55f), Color.Transparent),
                center = cool,
                radius = radius * 0.92f,
            ),
            radius = radius * 0.92f,
            center = cool,
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF07080C).copy(alpha = 0.72f)),
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsStagePresentation(
    artwork: String?,
    title: String,
    artist: String,
    lyricsState: LyricsUiState,
    positionMs: Long,
    lyricsOffsetMs: Long,
    progress: Float,
) {
    val lines = (lyricsState as? LyricsUiState.Success)
        ?.takeIf { it.isSynced }
        ?.lines
        .orEmpty()
    val lyricPosition = positionMs + lyricsOffsetMs
    val activeIndex = lines.indexOfLast { it.timeMs <= lyricPosition }
    val active = lines.getOrNull(activeIndex)
    val next = lines.getOrNull(activeIndex + 1)
    val played = MaterialTheme.colorScheme.primaryContainer
    val remaining = Color(0xFF10141D)
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(28.dp)),
    ) {
        val playerWidth = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val split = size.height * progress.coerceIn(0f, 1f)
            drawRect(remaining)
            drawRect(played.copy(alpha = 0.95f), size = Size(size.width, split))
            drawLine(
                color = accent,
                start = Offset(0f, split),
                end = Offset(size.width, split),
                strokeWidth = 2.dp.toPx(),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(artwork, Modifier.size(52.dp), true)
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist,
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "LYRICS",
                    color = Color.White.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AnimatedContent(
                    targetState = activeIndex,
                    transitionSpec = {
                        (fadeIn(tween(360)) + slideInVertically(tween(420)) { it / 5 }) togetherWith
                            (fadeOut(tween(220)) + slideOutVertically(tween(280)) { -it / 7 })
                    },
                    label = "lyrics-line",
                ) { index ->
                    val line = lines.getOrNull(index)
                    if (line == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpressiveTitle(
                                text = title,
                                baseSize = (playerWidth.value * 0.115f).coerceIn(34f, 54f).sp,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                if (lyricsState is LyricsUiState.Loading) "Finding synced lyrics…" else "No synced lyrics available for this track",
                                color = Color.White.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        ExpressiveLyricLine(
                            line = line,
                            positionMs = lyricPosition,
                            baseSize = (playerWidth.value * 0.105f).coerceIn(32f, 52f).sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                AnimatedVisibility(visible = !next?.text.isNullOrBlank()) {
                    next?.text?.let { nextText ->
                        Text(
                            nextText,
                            color = Color.White.copy(alpha = 0.42f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatPlayerTime(positionMs),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    artist,
                    color = Color.White.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpressiveLyricLine(
    line: LyricLine,
    positionMs: Long,
    baseSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val syllables = remember(line) {
        line.syllables.filter { !it.isBackground && it.text.isNotBlank() }
    }
    if (syllables.isEmpty()) {
        ExpressiveTitle(line.text, baseSize, Color.White, modifier)
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        syllables.forEach { word ->
            val current = positionMs >= word.timeMs && positionMs < word.timeMs + word.durationMs.coerceAtLeast(1L)
            val alreadySung = positionMs >= word.timeMs + word.durationMs
            val lyricColor by animateColorAsState(
                targetValue = when {
                    current -> Color.White
                    alreadySung -> Color.White.copy(alpha = 0.58f)
                    else -> Color.White.copy(alpha = 0.30f)
                },
                animationSpec = tween(180),
                label = "lyric-word-highlight",
            )
            val lyricEmphasis by animateFloatAsState(
                targetValue = if (current) 1.07f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
                label = "lyric-word-emphasis",
            )
            Text(
                word.text,
                color = lyricColor,
                modifier = Modifier.graphicsLayer {
                    scaleX = lyricEmphasis
                    scaleY = lyricEmphasis
                },
                fontSize = baseSize,
                fontWeight = if (current) FontWeight.Black else FontWeight.Bold,
                letterSpacing = (-0.035).em,
                lineHeight = baseSize * 0.98f,
            )
        }
    }
}

@Composable
private fun PosterPresentation(
    artwork: String?,
    title: String,
    artist: String,
    lyricsState: LyricsUiState,
    positionMs: Long,
    lyricsOffsetMs: Long,
    progress: Float,
    isPlaying: Boolean,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(560.dp).clip(RoundedCornerShape(28.dp)),
    ) {
        val width = maxWidth
        val coverSize = width * 2.28f
        val accent = MaterialTheme.colorScheme.primary
        val synced = (lyricsState as? LyricsUiState.Success)
            ?.takeIf { it.isSynced }
            ?.lines
            .orEmpty()
            .filter { it.text.isNotBlank() }
        val lyricPosition = positionMs + lyricsOffsetMs
        val activeIndex = synced.indexOfLast { it.timeMs <= lyricPosition }.coerceAtLeast(0)
        val activeLine = synced.getOrNull(activeIndex)?.text?.takeIf { synced.isNotEmpty() }
        val nextLine = synced.getOrNull(activeIndex + 1)?.text

        val coverScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.035f else 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 90f),
            label = "poster-cover-breathe",
        )
        val coverSpin by animateFloatAsState(
            targetValue = if (isPlaying) 360f else 0f,
            animationSpec = tween(durationMillis = 52000, easing = LinearEasing),
            label = "poster-cover-spin",
        )
        val glowMotion = rememberInfiniteTransition(label = "poster-glow")
        val ringGlow by glowMotion.animateFloat(
            initialValue = 0.24f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "poster-ring-glow",
        )
        AsyncImage(
            model = artwork,
            contentDescription = "Album artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(coverSize)
                .offset(x = -(width * 1.01f), y = -(width * 1.08f))
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                    rotationZ = coverSpin
                }
                .clip(CircleShape),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.05f),
                    0.42f to Color.Black.copy(alpha = 0.08f),
                    0.73f to Color(0xFF090A0E).copy(alpha = 0.80f),
                    1f to Color(0xFF090A0E),
                ),
            ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.width * 1.14f
            val center = Offset(size.width * 0.13f, size.width * 0.06f)
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = accent.copy(alpha = ringGlow * 0.42f),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = accent.copy(alpha = 0.92f),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (activeLine != null) "NOW SINGING" else "NOW PLAYING",
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.5.sp,
            )
            ExpressiveTitle(
                text = activeLine ?: title,
                baseSize = (width.value * 0.115f).coerceIn(34f, 58f).sp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!nextLine.isNullOrBlank()) {
                Text(
                    nextLine,
                    color = Color.White.copy(alpha = 0.48f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                artist,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimelinePresentation(
    artwork: String?,
    title: String,
    artist: String,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
) {
    val played = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.primary
    val remaining = Color(0xFF121722)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(240),
        label = "timeline-progress",
    )
    val timelineMotion = rememberInfiniteTransition(label = "timeline-motion")
    val playheadPulse by timelineMotion.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "timeline-playhead-pulse",
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(470.dp).clip(RoundedCornerShape(28.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val boundary = size.height * animatedProgress
            drawRect(remaining)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to played,
                    1f to played.copy(alpha = 0.72f),
                ),
                size = Size(size.width, boundary),
            )
            drawLine(
                color = accent,
                start = Offset(0f, boundary),
                end = Offset(size.width, boundary),
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = 10.dp.toPx() * playheadPulse,
                center = Offset(size.width - 3.dp.toPx(), boundary),
            )
            drawCircle(
                color = accent,
                radius = 4.dp.toPx() * playheadPulse,
                center = Offset(size.width - 3.dp.toPx(), boundary),
            )
            val tickCount = 36
            repeat(tickCount) { index ->
                val x = size.width * (index + 0.5f) / tickCount
                val elapsed = (index + 0.5f) / tickCount <= animatedProgress
                drawLine(
                    color = Color.White.copy(alpha = if (elapsed) 0.30f else 0.10f),
                    start = Offset(x, size.height * 0.72f),
                    end = Offset(x, size.height * 0.72f + (if (index % 4 == 0) 15.dp else 8.dp).toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Artwork(artwork, Modifier.size(66.dp), true)
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatPlayerTime(positionMs),
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(30.dp).height(2.dp).background(Color.White.copy(alpha = 0.70f)))
                    Text(
                        "OF " + formatPlayerTime(durationMs),
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpressiveTitle(
    text: String,
    baseSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val words = remember(text) { text.split(' ').filter(String::isNotBlank) }
    val emphasised = remember(words) {
        words.withIndex().filter { it.value.length >= 3 }.minByOrNull { it.value.length }?.index ?: -1
    }
    val rtl = remember(text) { isRtlText(text) }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            words.forEachIndexed { index, word ->
                val scale = when {
                    index == emphasised && words.size > 1 -> 1.45f
                    word.length >= 11 -> 0.74f
                    word.length >= 9 -> 0.86f
                    else -> 1f
                }
                Text(
                    word,
                    color = color,
                    fontSize = baseSize * scale,
                    fontWeight = if (scale > 1f) FontWeight.Black else FontWeight.Bold,
                    letterSpacing = (-0.035).em,
                    lineHeight = baseSize * 0.96f,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ArtworkPresentation(artwork: String?) {
    val motion = rememberInfiniteTransition(label = "artwork-float")
    val float by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "artwork-breathe",
    )
    val glow by motion.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(tween(5400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "artwork-glow",
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 372.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    val lift = 1f + float * 0.018f
                    scaleX = lift
                    scaleY = lift
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glow * 0.55f), Color.Transparent),
                    ),
                    radius = size.minDimension * 0.57f,
                )
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, accent.copy(alpha = glow), Color.Transparent),
                    ),
                    cornerRadius = CornerRadius(36.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.White.copy(alpha = 0.06f),
                shadowElevation = 26.dp,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) {
                Artwork(artwork, Modifier.fillMaxSize(), true)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(accent.copy(alpha = 0.55f + glow * 0.45f), CircleShape),
            )
            Text(
                "NOW PLAYING",
                color = Color.White.copy(.62f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
            )
        }
    }
}

@Composable
private fun VideoPresentation() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("IMMERSIVE MODE", color = Color.White.copy(.72f), style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp)
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier, rounded: Boolean) {
    if (url.isNullOrBlank()) {
        Box(modifier.clip(if (rounded) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp)).background(Color(0xFF242630)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(.35f), modifier = Modifier.size(64.dp))
        }
    } else {
        AsyncImage(model = url, contentDescription = "Album artwork", contentScale = ContentScale.Crop, modifier = modifier.clip(if (rounded) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp)))
    }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
