package com.itzfallenme.katmusic.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.DetailPage
import com.itzfallenme.katmusic.data.MediaCard
import com.itzfallenme.katmusic.data.Playlist
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.Artwork
import com.itzfallenme.katmusic.ui.CardShelf
import com.itzfallenme.katmusic.ui.LoadingRow
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.PlaylistPickerDialog
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.TrackRow
import com.itzfallenme.katmusic.ui.formatDuration
import com.itzfallenme.katmusic.ui.theme.GlassIconButton
import com.itzfallenme.katmusic.ui.theme.Motion
import com.itzfallenme.katmusic.ui.theme.bounceClickable
import com.itzfallenme.katmusic.ui.theme.liquidGlass
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(nav: Navigator, screen: Screen.Detail) {
    val library by ServiceLocator.library.state.collectAsState()
    val playerState by ServiceLocator.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var remote by remember(screen.browseId) { mutableStateOf<DetailPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pickerTrack by remember { mutableStateOf<Track?>(null) }

    val localPlaylist: Playlist? = remember(library.playlists, library.history, screen.browseId) {
        library.playlists.firstOrNull { it.id == screen.browseId }
            ?: ServiceLocator.library.generatedMixes().firstOrNull { it.id == screen.browseId }
    }

    LaunchedEffect(screen.browseId) {
        if (localPlaylist != null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { YtMusic.detail(screen.browseId, screen.params) }
            .onSuccess { remote = it }
            .onFailure { error = it.message ?: "Could not load this page" }
        loading = false
    }

    val title = localPlaylist?.name ?: remote?.title ?: screen.title
    val artworkUrl = localPlaylist?.artwork?.ifBlank {
        localPlaylist.tracks.firstOrNull()?.artwork.orEmpty()
    } ?: remote?.artwork ?: screen.artwork
    val tracks: List<Track> = localPlaylist?.tracks ?: remote?.tracks.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .blur(60.dp)
                            .graphicsLayer { alpha = 0.5f }
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = { nav.back() },
                            size = 42.dp,
                            iconSize = 20.dp
                        )
                        Spacer(Modifier.weight(1f))
                        Box {
                            GlassIconButton(
                                icon = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                onClick = { menu = true },
                                size = 42.dp
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Download all") },
                                    leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        tracks.forEach { ServiceLocator.downloads.download(it) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add all to queue") },
                                    onClick = {
                                        menu = false
                                        ServiceLocator.player.addAllToQueue(tracks)
                                    }
                                )
                                if (localPlaylist == null) {
                                    DropdownMenuItem(
                                        text = { Text("Save as playlist") },
                                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            val created = ServiceLocator.library.createPlaylist(
                                                title,
                                                "Saved from YouTube Music"
                                            )
                                            ServiceLocator.library.addToPlaylist(created.id, tracks)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Artwork(
                        url = artworkUrl,
                        modifier = Modifier
                            .size(206.dp)
                            .shadow(
                                26.dp,
                                CircleShape.takeIf { screen.kind == CardKind.ARTIST }
                                    ?: RoundedCornerShape(28.dp),
                                spotColor = MaterialTheme.colorScheme.primary
                            ),
                        corner = if (screen.kind == CardKind.ARTIST) 103.dp else 28.dp,
                        fallbackIcon = if (screen.kind == CardKind.ARTIST) Icons.Filled.Verified
                        else Icons.Filled.PlayArrow
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    val subtitle = buildString {
                        val sub = localPlaylist?.description?.takeIf { it.isNotBlank() }
                            ?: remote?.subtitle.orEmpty()
                        if (sub.isNotBlank()) append(sub)
                        if (tracks.isNotEmpty()) {
                            if (isNotEmpty()) append(" • ")
                            append("${tracks.size} tracks")
                            val total = tracks.sumOf { it.durationMs }
                            if (total > 0) append(" • ${formatDuration(total)}")
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BigPlayButton(enabled = tracks.isNotEmpty()) {
                            ServiceLocator.player.playTracks(tracks, 0)
                        }
                        CircleAction(Icons.Filled.Shuffle) {
                            if (tracks.isNotEmpty()) {
                                ServiceLocator.player.playTracks(tracks.shuffled(), 0)
                            }
                        }
                        CircleAction(Icons.Filled.Radio) {
                            tracks.firstOrNull()?.let { ServiceLocator.player.playRadio(it) }
                        }
                        Spacer(Modifier.weight(1f))
                        val likedAll = tracks.isNotEmpty() && tracks.all { ServiceLocator.library.isLiked(it.id) }
                        CircleAction(if (likedAll) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                            tracks.forEach { track ->
                                val liked = ServiceLocator.library.isLiked(track.id)
                                if (likedAll && liked) ServiceLocator.library.toggleLike(track)
                                if (!likedAll && !liked) ServiceLocator.library.toggleLike(track)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (loading) item { LoadingRow("Loading…") }
        if (error != null) item {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp)
            )
        }

        if (screen.kind == CardKind.ARTIST) {
            remote?.shelves?.forEach { shelf ->
                item {
                    CardShelf(
                        shelf = shelf,
                        onCardClick = { card ->
                            if (card.kind == CardKind.SONG) {
                                ServiceLocator.player.playTracks(
                                    listOf(
                                        Track(card.browseId, card.title, card.subtitle, artwork = card.artwork)
                                    )
                                )
                            } else {
                                nav.openDetail(card.browseId, card.title, card.kind, card.artwork, card.params)
                            }
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            val topTracks = remote?.tracks.orEmpty()
            if (topTracks.isNotEmpty()) {
                item { com.itzfallenme.katmusic.ui.SectionHeader("Popular") }
                items(topTracks.take(10), key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.track?.id == track.id,
                        isPlaying = playerState.playing,
                        onClick = { ServiceLocator.player.playTracks(topTracks, topTracks.indexOf(track)) },
                        onToggleLike = { ServiceLocator.library.toggleLike(it) },
                        onStartRadio = { ServiceLocator.player.playRadio(it) },
                        onPlayNext = { ServiceLocator.player.playNext(it) },
                        onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                        onDownload = { ServiceLocator.downloads.download(it) },
                        onAddToPlaylist = { pickerTrack = it }
                    )
                }
            }
        } else {
            items(tracks, key = { it.id + it.title }) { track ->
                TrackRow(
                    track = track,
                    index = tracks.indexOf(track) + 1,
                    isCurrent = playerState.track?.id == track.id,
                    isPlaying = playerState.playing,
                    onClick = { ServiceLocator.player.playTracks(tracks, tracks.indexOf(track)) },
                    onToggleLike = { ServiceLocator.library.toggleLike(it) },
                    onStartRadio = { ServiceLocator.player.playRadio(it) },
                    onPlayNext = { ServiceLocator.player.playNext(it) },
                    onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                    onDownload = { ServiceLocator.downloads.download(it) },
                    onAddToPlaylist = { pickerTrack = it },
                    onGoToArtist = { t ->
                        t.artistId?.let { nav.openDetail(it, t.artist, CardKind.ARTIST) }
                    },
                    onRemove = if (localPlaylist != null) {
                        { ServiceLocator.library.removeFromPlaylist(localPlaylist.id, it.id) }
                    } else null
                )
            }
        }

        if (!loading && tracks.isEmpty() && remote?.shelves.isNullOrEmpty()) {
            item {
                Text(
                    "Nothing to show here yet.",
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pickerTrack?.let { track ->
        PlaylistPickerDialog(track = track, onDismiss = { pickerTrack = null })
    }
}

@Composable
private fun BigPlayButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .bounceClickable(enabled = enabled, targetScale = 0.93f, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Play",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .liquidGlass(shape = CircleShape, fillAlpha = 0.5f)
            .bounceClickable(targetScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}
