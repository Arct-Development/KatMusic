package com.itzfallenme.katmusic.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.MusicPlayerState
import com.itzfallenme.katmusic.playback.PlaybackProgressState
import com.itzfallenme.katmusic.data.canvas.CanvasArtwork
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** Independent presentation presets; playback remains owned by MusicPlayer. */
enum class KatPlayerStyle(val label: String, val description: String) {
    DEFAULT("Classic", "The full KatMusic player with tabs, queue and advanced controls"),
    ARTWORK("Artwork", "Large album art, clean track details and focused playback controls"),
    LYRICS("Lyrics", "Put synchronized lyrics at the center of the player"),
    VIDEO("Canvas", "Use animated Canvas artwork as an immersive background");

    companion object {
        fun fromStored(value: String?): KatPlayerStyle = when (value) {
            "POSTER" -> ARTWORK
            "TIMELINE", "IPOD" -> DEFAULT
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
    val accent = MaterialTheme.colorScheme.primary
    val dark = Color(0xFF090A0E)

    Box(Modifier.fillMaxSize().background(dark)) {
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
            AnimatedContent(targetState = style, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "player-style") { target ->
                when (target) {
                    KatPlayerStyle.DEFAULT -> Unit
                    KatPlayerStyle.ARTWORK -> ArtworkPresentation(track.artworkUrl)
                    KatPlayerStyle.LYRICS -> LyricsPresentation(
                        state = state,
                        player = player,
                        lyricsState = lyricsState,
                        progressState = progressState,
                        lyricsOffsetMs = lyricsOffsetMs,
                        lyricsFontScale = lyricsFontScale,
                        controlsAutoHideSeconds = lyricsButtonsAutoHideSeconds,
                        translationLanguage = translationLanguage,
                        wavySeekbarEnabled = wavySeekbarEnabled,
                        onRetry = onRetryLyrics,
                        onFontScaleChange = onSetLyricsFontScale,
                        onSelectLyricsProvider = onSelectLyricsProvider,
                        onTryAnotherLyricsSource = onTryAnotherLyricsSource,
                    )
                    KatPlayerStyle.VIDEO -> VideoPresentation(track.title, track.artist)
                }
            }
            if (style != KatPlayerStyle.VIDEO) {
                Spacer(Modifier.height(18.dp))
                Text(track.title, color = Color.White, fontSize = if (style == KatPlayerStyle.LYRICS) 30.sp else 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(.66f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
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
                IconButton(onClick = player::previous, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous track", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Surface(onClick = player::togglePlayPause, shape = CircleShape, color = Color.White, modifier = Modifier.size(70.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(38.dp))
                    }
                }
                IconButton(onClick = player::next, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next track", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ArtworkPresentation(artwork: String?) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = 0.06f),
            shadowElevation = 22.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).padding(horizontal = 8.dp),
        ) {
            Artwork(artwork, Modifier.fillMaxWidth().aspectRatio(1f), true)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "NOW PLAYING",
            color = Color.White.copy(.62f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 3.sp,
        )
    }
}

@Composable
private fun VideoPresentation(title: String, artist: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("IMMERSIVE MODE", color = Color.White.copy(.72f), style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(artist, color = Color.White.copy(.72f), style = MaterialTheme.typography.titleMedium)
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
