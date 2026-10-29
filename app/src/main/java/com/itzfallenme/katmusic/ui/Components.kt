package com.itzfallenme.katmusic.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.playback.DownloadState
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.MediaCard
import com.itzfallenme.katmusic.data.Playlist
import com.itzfallenme.katmusic.data.Shelf
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.playback.PlayerUiState
import com.itzfallenme.katmusic.ui.theme.GlassIconButton
import com.itzfallenme.katmusic.ui.theme.GlassSurface
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

@Composable
fun Artwork(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    fallbackIcon: ImageVector = Icons.Filled.Album
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                shape = if (corner >= 50.dp) CircleShape
                else com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(corner, 0.55f)
                clip = true
            }
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp)
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.98f }
            )
        }
    }
}

/** Frosted glass container used across the app in place of flat cards. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(corner, 0.55f),
        fillAlpha = 1f
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMore != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .bounceClickable(targetScale = 0.88f, onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowForwardIos,
                    contentDescription = "See all",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MediaCardItem(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    round: Boolean = false
) {
    Column(
        modifier = modifier
            .width(width)
            .bounceClickable(targetScale = 0.95f, onClick = onClick)
    ) {
        Artwork(
            url = card.artwork,
            modifier = Modifier.size(width),
            corner = if (round) 100.dp else 22.dp,
            fallbackIcon = when (card.kind) {
                CardKind.ARTIST -> Icons.Filled.Verified
                CardKind.MIX -> Icons.Filled.Radio
                else -> Icons.Filled.Album
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CardShelf(
    shelf: Shelf,
    onCardClick: (MediaCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (!shelf.title.isNullOrBlank()) {
            SectionHeader(shelf.title.orEmpty())
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(shelf.cards, key = { index, card -> "$index:${card.browseId}" }) { _, card ->
                MediaCardItem(
                    card = card,
                    onClick = { onCardClick(card) },
                    round = card.kind == CardKind.ARTIST
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showArtwork: Boolean = true,
    index: Int? = null,
    onAddToPlaylist: (Track) -> Unit = {},
    onToggleLike: (Track) -> Unit = {},
    onDownload: (Track) -> Unit = {},
    onStartRadio: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onGoToArtist: (Track) -> Unit = {},
    onRemove: ((Track) -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val libraryState by ServiceLocator.library.state.collectAsState()
    val downloadState by ServiceLocator.downloads.progress.collectAsState()
    val liked = libraryState.liked.any { it.id == track.id }
    val downloaded = libraryState.downloads.any { it.track.id == track.id }
    val activeDownload = downloadState[track.id]
    val accent = MaterialTheme.colorScheme.primary
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Rows cascade in on first composition; the animation only runs once per row so
    // scrolling back does not replay it.
    var entered by remember { mutableStateOf(index == null) }
    LaunchedEffect(Unit) {
        if (!entered) {
            kotlinx.coroutines.delay((((index ?: 1) - 1).coerceIn(0, 8) * 40L))
            entered = true
        }
    }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = Motion.effectsDefault(),
        label = "rowAlpha"
    )
    val entranceOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 26f,
        animationSpec = Motion.spatialDefault(),
        label = "rowOffset"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = entranceOffset
            }
            .clip(RoundedCornerShape(18.dp))
            .then(
                when {
                    selected -> Modifier.background(accent.copy(alpha = 0.18f))
                    isCurrent -> Modifier.background(accent.copy(alpha = 0.10f))
                    else -> Modifier
                }
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.bounceClickable(targetScale = 0.985f, onClick = onClick)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            index != null -> {
                Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                    if (isCurrent) {
                        if (isPlaying) {
                            PlayingBars(color = accent)
                        } else {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            showArtwork -> {
                Artwork(url = track.artwork, modifier = Modifier.size(50.dp), corner = 14.dp)
                Spacer(Modifier.width(12.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.explicit) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Explicit,
                        contentDescription = "Explicit",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (downloaded) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(13.dp),
                        tint = accent
                    )
                } else if (activeDownload?.state == DownloadState.FAILED) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = "Download failed",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                } else if (activeDownload?.state == DownloadState.WAITING_NETWORK) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.WifiOff,
                        contentDescription = "Waiting for Wi-Fi",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (activeDownload?.active == true) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(
                        progress = { activeDownload.fraction },
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
            Text(
                text = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album)
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (liked) "Remove from liked" else "Add to liked") },
                    leadingIcon = {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null
                        )
                    },
                    onClick = { menuOpen = false; onToggleLike(track) }
                )
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                    onClick = { menuOpen = false; onPlayNext(track) }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = { menuOpen = false; onAddToQueue(track) }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { menuOpen = false; onAddToPlaylist(track) }
                )
                DropdownMenuItem(
                    text = { Text("Start radio") },
                    leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null) },
                    onClick = { menuOpen = false; onStartRadio(track) }
                )
                DropdownMenuItem(
                    text = { Text(if (downloaded) "Remove download" else "Download") },
                    leadingIcon = {
                        Icon(
                            if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.CloudDownload,
                            contentDescription = null
                        )
                    },
                    onClick = { menuOpen = false; onDownload(track) }
                )
                if (track.artistId != null) {
                    DropdownMenuItem(
                        text = { Text("Go to artist") },
                        leadingIcon = { Icon(Icons.Filled.Verified, contentDescription = null) },
                        onClick = { menuOpen = false; onGoToArtist(track) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        // The popup's context is not always an Activity, so this adds
                        // FLAG_ACTIVITY_NEW_TASK and never throws.
                        context.shareLink("https://music.youtube.com/watch?v=${track.id}")
                    }
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from list") },
                        onClick = { menuOpen = false; onRemove(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistPickerDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val library = ServiceLocator.library
    val playlists = library.state.value.playlists
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist name") },
                        singleLine = true
                    )
                } else {
                    if (playlists.isEmpty()) {
                        Text(
                            "No playlists yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    playlists.take(8).forEach { playlist: Playlist ->
                        Text(
                            text = playlist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    library.addToPlaylist(playlist.id, listOf(track))
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        val created = library.createPlaylist(newName.ifBlank { "New playlist" })
                        library.addToPlaylist(created.id, listOf(track))
                        onDismiss()
                    }
                ) { Text("Create & add") }
            } else {
                TextButton(onClick = { creating = true }) { Text("New playlist") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LoadingRow(message: String = "Loading…") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.itzfallenme.katmusic.ui.theme.MorphingShapeLoader(size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.track ?: return
    val progress by animateFloatAsState(
        targetValue = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = Motion.effectsDefault(),
        label = "progress"
    )
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = 14.dp,
        fillAlpha = 0.8f
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClickable(targetScale = 0.98f, onClick = onOpen)
                    .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    url = track.artwork,
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp)),
                    corner = 14.dp
                )
                Spacer(Modifier.width(11.dp))
                // Track changes fade the title block instead of swapping it.
                val textReveal = remember(track.id) {
                    androidx.compose.animation.core.Animatable(if (Motion.reduced) 1f else 0f)
                }
                LaunchedEffect(track.id) {
                    if (Motion.reduced) {
                        textReveal.snapTo(1f)
                    } else {
                        textReveal.animateTo(1f, Motion.spatialDefault())
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = textReveal.value
                            translationY = (1f - textReveal.value) * 8f
                        }
                ) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            track.artist.ifBlank { "KatMusic" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (state.playing) {
                            Spacer(Modifier.width(8.dp))
                            PlayingBars()
                        }
                    }
                }
                GlassIconButton(
                    icon = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    onClick = { ServiceLocator.player.togglePlayPause() },
                    size = 40.dp,
                    iconSize = 22.dp
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    onClick = { ServiceLocator.player.next() },
                    size = 40.dp,
                    iconSize = 20.dp
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 9.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** Tiny animated equaliser shown next to the artist while a song is playing. */
@Composable
fun PlayingBars(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    bars: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "playingBars")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "barsPhase"
    )
    val phase = if (Motion.reduced) 0f else animatedPhase
    Canvas(modifier.size(width = 14.dp, height = 12.dp)) {
        val barWidth = size.width / (bars * 2f - 1f)
        repeat(bars) { index ->
            val height = if (Motion.reduced) size.height * 0.45f
            else size.height * (0.35f + 0.32f * abs(sin(phase + index * 1.1f)))
            drawRoundRect(
                color = color,
                topLeft = Offset(index * barWidth * 2f, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
fun DownloadStatusChip(track: Track, onClick: () -> Unit) {
    val progressMap by ServiceLocator.downloads.progress.collectAsState()
    val progress = progressMap[track.id]
    val downloaded = ServiceLocator.library.downloadFor(track.id) != null
    if (!downloaded && progress == null) return
    val label = when {
        downloaded -> "Downloaded"
        progress?.state == DownloadState.WAITING_NETWORK -> "Waiting for Wi-Fi"
        progress?.state == DownloadState.QUEUED -> "Queued"
        progress?.state == DownloadState.FAILED -> "Retry download"
        progress?.state == DownloadState.RUNNING -> "${(progress.fraction * 100).toInt()}%"
        else -> "Download"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (progress?.state) {
                DownloadState.FAILED -> Icons.Filled.ErrorOutline
                DownloadState.WAITING_NETWORK -> Icons.Filled.WifiOff
                else -> if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (progress?.state == DownloadState.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Expressive wavy progress line, used for downloads and track loading. */
@Composable
fun InlineProgress(fraction: Float, modifier: Modifier = Modifier) {
    com.itzfallenme.katmusic.ui.theme.WavyProgressLine(
        progress = fraction,
        modifier = modifier,
        height = 8.dp
    )
}

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = Motion.effectsDefault(),
        label = "chipBackground"
    )
    // M3 Expressive selection morphs the shape as well as the colour: a pill
    // settles into a softer squircle when it is the chosen option.
    val radius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 12.dp else 26.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "chipCorner"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .graphicsLayer {
                shape = com.itzfallenme.katmusic.ui.theme.SmoothCornerShape(radius, 0.6f)
                clip = true
            }
            .background(background)
            .bounceClickable(targetScale = 0.94f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
fun ExplicitBadge() {
    Text(
        "E",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    )
}

fun trackAsCard(track: Track): MediaCard = MediaCard(
    title = track.title,
    subtitle = track.artist,
    artwork = track.artwork,
    browseId = track.id,
    kind = CardKind.SONG
)

/** Re-resolve helper for playlist pages that store bare track ids. */
suspend fun resolveTrack(id: String, fallbackTitle: String = ""): Track? =
    runCatching { InnerTube.upNext(id).firstOrNull { it.id == id } }.getOrNull()
