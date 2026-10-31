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
import kotlin.math.max

/** Independent presentation presets; playback remains owned by MusicPlayer. */
enum class KatPlayerStyle(val label: String) {
    DEFAULT("KatMusic"), POSTER("Poster"), TIMELINE("Timeline"),
    LYRICS("Lyrics Stage"), IPOD("iPod"), VIDEO("Full Video");

    companion object {
        fun fromStored(value: String?): KatPlayerStyle = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

@Composable
internal fun AlternateKatPlayerScreen(
    state: MusicPlayerState,
    player: MusicPlayer,
    style: KatPlayerStyle,
    onStyleSelected: (KatPlayerStyle) -> Unit,
    onCollapse: () -> Unit,
) {
    val track = state.current ?: return
    val duration = max(state.durationMs, 1L)
    val progress = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val dark = Color(0xFF090A0E)

    Box(Modifier.fillMaxSize().background(dark)) {
        if (style == KatPlayerStyle.VIDEO) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), false)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.24f), Color.Transparent, dark.copy(.96f)))))
        } else if (style == KatPlayerStyle.POSTER) {
            Artwork(track.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), false)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, dark.copy(.25f), dark.copy(.96f), dark))))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KatPlayerStyle.entries.filter { it != KatPlayerStyle.DEFAULT }.forEach { option ->
                    FilterChip(selected = style == option, onClick = { onStyleSelected(option) }, label = { Text(option.label, maxLines = 1) })
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(targetState = style, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "player-style") { target ->
                when (target) {
                    KatPlayerStyle.DEFAULT -> Unit
                    KatPlayerStyle.POSTER -> PosterPresentation(track.artworkUrl)
                    KatPlayerStyle.TIMELINE -> TimelinePresentation(progress, accent)
                    KatPlayerStyle.LYRICS -> LyricsPresentation(track.title, track.artist)
                    KatPlayerStyle.IPOD -> IpodPresentation(track.title, track.artist, track.album.orEmpty(), progress, accent)
                    KatPlayerStyle.VIDEO -> VideoPresentation(track.title, track.artist)
                }
            }
            if (style != KatPlayerStyle.POSTER && style != KatPlayerStyle.VIDEO) {
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
private fun PosterPresentation(artwork: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(artwork, Modifier.fillMaxWidth().aspectRatio(.94f), true)
        Spacer(Modifier.height(12.dp))
        Text("NOW PLAYING", color = Color.White.copy(.62f), style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp)
    }
}

@Composable
private fun TimelinePresentation(progress: Float, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("YOUR MOMENT IN MUSIC", color = Color.White.copy(.58f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(.14f))) {
            Box(Modifier.fillMaxWidth(progress).height(7.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PLAYING", color = Color.White.copy(.7f), style = MaterialTheme.typography.labelSmall)
            Text("${(progress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LyricsPresentation(title: String, artist: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("A SONG IN FOCUS", color = Color.White.copy(.52f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
        Text(title, color = Color.White, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold)
        Text(artist.uppercase(), color = Color.White.copy(.62f), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Let the music\nfill the silence.", color = Color.White.copy(.34f), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
        Text("Open KatMusic's Lyrics tab for the live synced lyrics.", color = Color.White.copy(.55f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IpodPresentation(title: String, artist: String, album: String, progress: Float, accent: Color) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp).clip(RoundedCornerShape(30.dp))
            .background(Color(0xFFD8D9D7)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2725)).padding(14.dp)) {
            Text("KATMUSIC", color = Color(0xFFB9D2C1), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Text(title, color = Color(0xFFE8F0E9), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color(0xFFB9D2C1), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.ifBlank { "Now Playing" }, color = Color(0xFF8FA99A), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(.2f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(accent))
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(190.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFF5F5F3), Color(0xFFB9BAB8)))), contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF8D908E)))
            Text("MENU", Modifier.align(Alignment.TopCenter).padding(top = 18.dp), color = Color(0xFF555957), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("‹", Modifier.align(Alignment.CenterStart).padding(start = 18.dp), color = Color(0xFF555957), fontSize = 30.sp)
            Text("›", Modifier.align(Alignment.CenterEnd).padding(end = 18.dp), color = Color(0xFF555957), fontSize = 30.sp)
            Text("●", color = Color(0xFF555957), fontSize = 22.sp)
        }
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
