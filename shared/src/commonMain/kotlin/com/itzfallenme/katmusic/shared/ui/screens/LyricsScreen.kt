package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.lyrics.LrcParser
import com.itzfallenme.katmusic.shared.lyrics.Lyrics
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.lyrics.LyricsResult
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth

/**
 * Lyrics for the track that is playing, following its position line by line.
 *
 * Fetching lives behind [LyricsProvider] so each platform contributes its own
 * real source (Android's provider chain, the desktop LRCLIB client) while the
 * screen, the parsing and the highlighting are shared.
 */
@Composable
fun LyricsScreen(
    state: PlayerState,
    provider: LyricsProvider?,
    onBack: () -> Unit,
) {
    val track = state.current
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }
    var error by remember(track?.id) { mutableStateOf<String?>(null) }
    var loading by remember(track?.id) { mutableStateOf(provider != null) }

    LaunchedEffect(track?.id, provider) {
        if (track == null || provider == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        when (val result = provider.lyricsFor(track)) {
            is LyricsResult.Found -> lyrics = result.lyrics
            LyricsResult.NotFound -> lyrics = null
            is LyricsResult.Failed -> error = result.message
        }
        loading = false
    }

    val listState = rememberLazyListState()
    val currentIndex = lyrics?.let { LrcParser.currentLineIndex(it.lines, state.positionMs) } ?: -1
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < (lyrics?.lines?.size ?: 0)) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Lyrics", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(track?.title, track?.artist).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            provider == null -> Message("Lyrics are not available in this build.")
            loading -> Loading()
            error != null -> Message(error ?: "")
            lyrics == null || lyrics!!.isEmpty -> Message("No lyrics found for this track.")
            else -> LyricsList(
                lyrics = lyrics!!,
                currentIndex = currentIndex,
                listState = listState,
            )
        }
    }
}

@Composable
private fun LyricsList(
    lyrics: Lyrics,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = buildString {
                    append(lyrics.source ?: "Lyrics")
                    if (!lyrics.isSynced) append(" · not synced")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Finding lyrics…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
