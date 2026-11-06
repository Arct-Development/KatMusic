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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    VIDEO("Canvas", "Immersive animated Canvas artwork as the player background");

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
    val track = state.current ?: return
    val duration = max(state.durationMs, 1L)
    val progress = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val dark = Color(0xFF090A0E)

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accent = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.tertiary,
            isPlaying = state.isPlaying,
        )
        if (style == KatPlayerStyle.VIDEO) {
            if (canvasEnabled && canvas != null) {
                CanvasArtworkPlayer(
                    canvas = canvas,
                    isPlaying = state.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                    bottomFade = 0.22f,
                )
            } else {
                Artwork(track.artworkUrl, Modifier.fillMaxSize(), false)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.24f), Color.Transparent, dark.copy(.96f)))))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCollapse) { Icon(Icons.Filled.ArrowBack, "Collapse player", tint = Color.White) }
                Text(style.label.uppercase(), color = Color.White.copy(.78f), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = style,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 18 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 24 })
                },
                label = "player-style",
            ) { target ->
                when (target) {
                    KatPlayerStyle.DEFAULT -> Unit
                    KatPlayerStyle.POSTER -> PosterPresentation(
                        artwork = track.artworkUrl,
                        title = track.title,
                        artist = track.artist,
                        lyricsState = lyricsState,
                        positionMs = state.positionMs,
                        lyricsOffsetMs = lyricsOffsetMs,
                        progress = progress,
                        isPlaying = state.isPlaying,
                    )
                    KatPlayerStyle.TIMELINE -> TimelinePresentation(
                        artwork = track.artworkUrl,
                        title = track.title,
                        artist = track.artist,
                        positionMs = state.positionMs,
                        durationMs = duration,
                        progress = progress,
                    )
                    KatPlayerStyle.ARTWORK -> ArtworkPresentation(track.artworkUrl)
                    KatPlayerStyle.LYRICS -> LyricsStagePresentation(
                        artwork = track.artworkUrl,
                        title = track.title,
                        artist = track.artist,
                        lyricsState = lyricsState,
                        positionMs = state.positionMs,
                        lyricsOffsetMs = lyricsOffsetMs,
                        progress = progress,
                    )
                    KatPlayerStyle.VIDEO -> VideoPresentation()
                }
            }
            if (style != KatPlayerStyle.VIDEO && style != KatPlayerStyle.POSTER && style != KatPlayerStyle.TIMELINE && style != KatPlayerStyle.LYRICS) {
                Spacer(Modifier.height(18.dp))
                Text(track.title, color = Color.White, fontSize = if (style == KatPlayerStyle.LYRICS) 30.sp else 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(.66f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (style == KatPlayerStyle.VIDEO) {
                Spacer(Modifier.height(14.dp))
                Text(track.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(.72f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))
            var dragProgress by remember(state.current?.videoId, state.durationMs) { mutableFloatStateOf(progress) }
            Slider(
                value = dragProgress.coerceIn(0f, 1f),
                onValueChange = { dragProgress = it },
                onValueChangeFinished = { player.seekTo((dragProgress * duration).toLong().coerceIn(0L, duration)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(state.positionMs), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall)
                Text(formatPlayerTime(state.durationMs), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                IconButton(
                    onClick = player::previous,
                    modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(Icons.Filled.SkipPrevious, "Previous track", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                val playControlScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
                    label = "play-control-scale",
                )
                val playElevation by animateDpAsState(
                    targetValue = if (state.isPlaying) 18.dp else 7.dp,
                    animationSpec = tween(320),
                    label = "play-control-elevation",
                )
                val controlMotion = rememberInfiniteTransition(label = "transport-glow")
                val controlGlow by controlMotion.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.85f,
                    animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "transport-glow-pulse",
                )
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(84.dp * (0.92f + controlGlow * 0.08f))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f * controlGlow),
                                        Color.Transparent,
                                    ),
                                ),
                                CircleShape,
                            ),
                    )
                    Surface(
                        onClick = player::togglePlayPause,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = playElevation,
                        modifier = Modifier.size(70.dp).graphicsLayer {
                            scaleX = playControlScale
                            scaleY = playControlScale
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = state.isPlaying,
                                transitionSpec = {
                                    (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.62f)) togetherWith
                                        (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.62f))
                                },
                                label = "play-pause-glyph",
                            ) { playing ->
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (playing) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = player::next,
                    modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(Icons.Filled.SkipNext, "Next track", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
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
