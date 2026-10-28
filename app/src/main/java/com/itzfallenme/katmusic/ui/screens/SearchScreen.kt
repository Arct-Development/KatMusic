package com.itzfallenme.katmusic.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.itzfallenme.katmusic.ui.TrackRow
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: Navigator) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val library by ServiceLocator.library.state.collectAsState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        suggestions = YtMusic.suggestions(query)
    }

    fun runSearch(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        submitted = trimmed
        loading = true
        ServiceLocator.library.addRecentSearch(trimmed)
    }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        shelves = runCatching { YtMusic.search(submitted) }.getOrDefault(emptyList())
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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 10.dp)
        )
        TextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isBlank()) {
                    submitted = ""
                    shelves = emptyList()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focus),
            placeholder = { Text("Songs, albums, artists…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; submitted = ""; suggestions = emptyList() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch(query) })
        )

        if (query.isNotBlank() && suggestions.isNotEmpty() && submitted != query) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runSearch(suggestion) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            return@Column
        }

        if (submitted.isBlank()) {
            Column(Modifier.padding(top = 14.dp)) {
                if (library.recentSearches.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { ServiceLocator.library.clearRecentSearches() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(library.recentSearches) { recent ->
                            Chip(text = recent, onClick = { runSearch(recent) })
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Browse categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        listOf("Pop", "Hip hop", "Lo-fi", "Rock", "Electronic", "Jazz", "Bollywood", "K-pop")
                    ) { category ->
                        Chip(text = category, onClick = { runSearch(category) })
                    }
                }
            }
            return@Column
        }

        if (loading) {
            LoadingRow("Searching…")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp, top = 10.dp)
        ) {
            items(shelves.size) { index ->
                val shelf = shelves[index]
                if (shelf.tracks.isNotEmpty()) {
                    Column {
                        com.itzfallenme.katmusic.ui.SectionHeader(title = shelf.title ?: "Songs")
                        shelf.tracks.take(10).forEachIndexed { i, track ->
                            TrackRow(
                                track = track,
                                index = i + 1,
                                isCurrent = false,
                                onClick = { ServiceLocator.player.playTracks(shelf.tracks, i) },
                                onStartRadio = { ServiceLocator.player.playRadio(it) },
                                onPlayNext = { ServiceLocator.player.playNext(it) },
                                onAddToQueue = { ServiceLocator.player.addToQueue(it) },
                                onToggleLike = { ServiceLocator.library.toggleLike(it) },
                                onDownload = { ServiceLocator.downloads.download(it) },
                                onGoToArtist = { t ->
                                    t.artistId?.let { nav.openDetail(it, t.artist, CardKind.ARTIST) }
                                }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                } else {
                    CardShelf(shelf = shelf, onCardClick = { openCard(it) })
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (!loading && shelves.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("No results for \"$submitted\"")
                        }
                    }
                }
            }
        }
    }
}
