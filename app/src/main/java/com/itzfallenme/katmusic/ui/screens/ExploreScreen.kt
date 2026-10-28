package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.MediaCard
import com.itzfallenme.katmusic.data.Shelf
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.YtMusic
import com.itzfallenme.katmusic.ui.CardShelf
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.LoadingRow
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.Screen
import com.itzfallenme.katmusic.ui.TrackRow

private enum class ExploreTab(val label: String) {
    CHARTS("Charts"),
    MOODS("Moods & genres"),
    NEW("New releases"),
    PODCASTS("Podcasts")
}

@Composable
fun ExploreScreen(nav: Navigator) {
    var tab by remember { mutableIntStateOf(0) }
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab) {
        loading = true
        error = null
        runCatching {
            when (ExploreTab.entries[tab]) {
                ExploreTab.CHARTS -> YtMusic.charts()
                ExploreTab.MOODS -> YtMusic.moods()
                ExploreTab.NEW -> YtMusic.newReleases()
                ExploreTab.PODCASTS -> YtMusic.podcasts()
            }
        }.onSuccess {
            shelves = it
        }.onFailure {
            error = it.message ?: "Could not load"
            shelves = emptyList()
        }
        loading = false
    }

    fun openCard(card: MediaCard) {
        if (card.kind == CardKind.SONG) {
            ServiceLocator.player.playTracks(
                listOf(Track(card.browseId, card.title, card.subtitle, artwork = card.artwork))
            )
        } else {
            nav.openDetail(card.browseId, card.title, card.kind, card.artwork, card.params)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    text = "Charts, moods and everything new",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreTab.entries.forEachIndexed { index, entry ->
                        Chip(text = entry.label, selected = tab == index, onClick = { tab = index })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (loading) item { LoadingRow("Loading ${ExploreTab.entries[tab].label.lowercase()}…") }
        if (error != null) item {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp)
            )
        }

        items(shelves.size) { index ->
            val shelf = shelves[index]
            if (shelf.tracks.isNotEmpty()) {
                Column {
                    com.itzfallenme.katmusic.ui.SectionHeader(title = shelf.title ?: "Tracks")
                    shelf.tracks.take(8).forEachIndexed { i, track ->
                        TrackRow(
                            track = track,
                            index = i + 1,
                            onClick = { ServiceLocator.player.playTracks(shelf.tracks, i) },
                            onStartRadio = { ServiceLocator.player.playRadio(it) },
                            onPlayNext = { ServiceLocator.player.playNext(it) },
                            onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                            onToggleLike = { ServiceLocator.library.toggleLike(it) },
                            onDownload = { ServiceLocator.downloads.download(it) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                CardShelf(shelf = shelf, onCardClick = { openCard(it) })
                Spacer(Modifier.height(16.dp))
            }
        }

        if (!loading && shelves.isEmpty() && error == null) {
            item { LoadingRow("Nothing here yet") }
        }

        item {
            val favoriteGenres = remember(shelves) {
                shelves.flatMap { it.cards }.filter { it.kind == CardKind.MIX }.take(8)
            }
            if (favoriteGenres.isNotEmpty()) {
                Column {
                    com.itzfallenme.katmusic.ui.SectionHeader(title = "Start a mix")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favoriteGenres, key = { it.browseId + it.title }) { card ->
                            Chip(text = card.title, onClick = { openCard(card) })
                        }
                    }
                }
            }
        }
    }
}
