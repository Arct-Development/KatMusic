package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.library.LibraryGrouping
import com.itzfallenme.katmusic.shared.library.ScanState
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.SectionLabel
import com.itzfallenme.katmusic.shared.ui.components.TrackRow
import com.itzfallenme.katmusic.shared.ui.components.formatDuration
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import com.itzfallenme.katmusic.ui.components.expressiveBounceClickable

private val LIBRARY_TABS = listOf("Songs", "Albums", "Artists", "Genres")
/**
 * The shared Library: the same three views Android's library offers (songs,
 * albums, artists) built from whichever track list the platform supplied.
 * Downloads and Playlists entries appear when the platform provides them.
 */
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    scan: ScanState,
    playerState: PlayerState,
    canChooseFolder: Boolean,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenAlbum: (LibraryGrouping.Album) -> Unit = {},
    onOpenArtist: (LibraryGrouping.Artist) -> Unit = {},
    onOpenGenre: (String, List<Track>) -> Unit = { _, _ -> },
    onOpenDownloads: (() -> Unit)? = null,
    onOpenPlaylists: (() -> Unit)? = null,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = scan.message ?: "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canChooseFolder) {
                IconButton(onClick = onChooseFolder) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Choose music folder")
                }
            }
            IconButton(onClick = onRefresh, enabled = !scan.isScanning) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
            }
        }

        if (scan.isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Scanning…", style = MaterialTheme.typography.bodySmall)
            }
        }

        scan.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (tracks.isEmpty() && !scan.isScanning) {
            EmptyLibrary(canChooseFolder = canChooseFolder, onChooseFolder = onChooseFolder)
            LibraryEntriesRow(onOpenDownloads = onOpenDownloads, onOpenPlaylists = onOpenPlaylists)
            return@Column
        }

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            LIBRARY_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        when (selectedTab) {
            0 -> SongsTab(tracks = tracks, playerState = playerState, onPlay = onPlay)
            1 -> AlbumsTab(tracks = tracks, onOpenAlbum = onOpenAlbum)
            2 -> ArtistsTab(tracks = tracks, onOpenArtist = onOpenArtist)
            else -> GenresTab(tracks = tracks, onOpenGenre = onOpenGenre)
        }
    }
}

@Composable
private fun SongsTab(
    tracks: List<Track>,
    playerState: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrent = playerState.current?.id == track.id,
                onClick = { onPlay(tracks, tracks.indexOf(track)) },
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    tracks: List<Track>,
    onOpenAlbum: (LibraryGrouping.Album) -> Unit,
) {
    val albums = remember(tracks) { LibraryGrouping.albums(tracks) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(albums, key = { it.key }) { album ->
            Column(
                modifier = Modifier.expressiveBounceClickable { onOpenAlbum(album) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Artwork(
                    uri = album.artwork,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                    contentDescription = album.album,
                )
                Text(
                    text = album.album,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.artist} · ${album.tracks.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenresTab(
    tracks: List<Track>,
    onOpenGenre: (String, List<Track>) -> Unit,
) {
    val genres = remember(tracks) { genresOf(tracks) }
    if (genres.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No genre tags in this library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { SectionLabel("${genres.size} genres") }
        items(genres, key = { it.first }) { (genre, genreTracks) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveBounceClickable { onOpenGenre(genre, genreTracks) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Artwork(
                    uri = genreTracks.firstOrNull()?.artwork,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    contentDescription = genre,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${genreTracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Splits multi-genre tags (`Rock/Pop`, `Rock; Pop`) into one entry per tag. */
private fun genresOf(tracks: List<Track>): List<Pair<String, List<Track>>> = tracks
    .flatMap { track ->
        track.genre.split('/', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it to track }
    }
    .groupBy({ it.first }, { it.second })
    .map { (genre, genreTracks) ->
        genre to genreTracks.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }
    .sortedBy { it.first.lowercase() }

@Composable
private fun ArtistsTab(
    tracks: List<Track>,
    onOpenArtist: (LibraryGrouping.Artist) -> Unit,
) {
    val artists = remember(tracks) { LibraryGrouping.artists(tracks) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { SectionLabel("${artists.size} artists") }
        items(artists, key = { it.key }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveBounceClickable { onOpenArtist(artist) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Artwork(
                    uri = artist.artwork,
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                    contentDescription = artist.artist,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${artist.tracks.size} songs · ${artist.albumCount} albums · " +
                            formatDuration(artist.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryEntriesRow(
    onOpenDownloads: (() -> Unit)?,
    onOpenPlaylists: (() -> Unit)?,
) {
    if (onOpenDownloads == null && onOpenPlaylists == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onOpenDownloads != null) {
            Button(onClick = onOpenDownloads) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Text("  Downloads")
            }
        }
        if (onOpenPlaylists != null) {
            Button(onClick = onOpenPlaylists) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                Text("  Playlists")
            }
        }
    }
}

@Composable
private fun EmptyLibrary(canChooseFolder: Boolean, onChooseFolder: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (canChooseFolder) {
                    "Add a folder of audio files and KatMusic will index it in place — nothing is copied or moved."
                } else {
                    "No songs were found on this device yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (canChooseFolder) {
                Button(
                    onClick = onChooseFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Text("  Choose folder")
                }
            }
        }
    }
}
