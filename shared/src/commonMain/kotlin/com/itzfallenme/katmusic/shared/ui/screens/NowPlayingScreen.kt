package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.player.RepeatMode
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import com.itzfallenme.katmusic.shared.ui.components.Artwork
import com.itzfallenme.katmusic.shared.ui.components.formatDuration
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth
import com.itzfallenme.katmusic.ui.theme.NowPlayingCardShape

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    player: PlayerController,
    onBack: () -> Unit,
    onOpenLyrics: (() -> Unit)? = null,
    onShareSongCode: ((com.itzfallenme.katmusic.shared.models.Track) -> Unit)? = null,
    /** Android's "Start mix with this song": seed a radio from the current track. */
    onStartMix: ((com.itzfallenme.katmusic.shared.models.Track) -> Unit)? = null,
) {
    val current = state.current
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing playing", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    var scrubPosition by remember(current.id) { mutableStateOf<Float?>(null) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var customTimerMinutes by remember { mutableStateOf("") }
    val shareVideoId = current.videoId ?: current.id.takeUnless { current.isLocal }
    val canShareSongCode = onShareSongCode != null && KatMusicCode.encode(shareVideoId) != null
    val duration = state.durationMs.coerceAtLeast(current.durationMs).coerceAtLeast(1L)
    val sliderValue = (scrubPosition ?: state.positionMs.toFloat()).coerceIn(0f, duration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player")
            }
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Row {
                if (onStartMix != null) {
                    IconButton(onClick = { onStartMix.invoke(current) }) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Start mix with this song")
                    }
                }
                IconButton(onClick = { showSleepTimer = true }) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = if (state.sleepTimerRemainingMs == null) {
                            "Set sleep timer"
                        } else {
                            "Sleep timer ${formatSleepTimer(state.sleepTimerRemainingMs)} remaining"
                        },
                        tint = if (state.sleepTimerRemainingMs != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (canShareSongCode) {
                    IconButton(onClick = { onShareSongCode.invoke(current) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share KatMusic Code")
                    }
                }
                if (onOpenLyrics != null) {
                    IconButton(onClick = onOpenLyrics) {
                        Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Artwork(
            uri = current.artwork,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.72f)
                .height(320.dp),
            shape = RoundedCornerShape(24.dp),
            contentDescription = current.album.ifBlank { current.title },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = current.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(current.artist, current.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        Slider(
            value = sliderValue,
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { player.seekTo(it.toLong()) }
                scrubPosition = null
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(sliderValue.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { player.setShuffle(!state.shuffle) }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { player.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(
                onClick = { player.togglePlayPause() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = { player.cycleRepeatMode() }) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth().adaptiveContentWidth(maxWidth = 640.dp)) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.height(220.dp)) {
                itemsIndexed(state.queue) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Artwork(
                            uri = track.artwork,
                            modifier = Modifier.size(36.dp),
                            shape = NowPlayingCardShape,
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == state.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showSleepTimer) {
        val activeTime = state.sleepTimerRemainingMs?.let(::formatSleepTimer)
        AlertDialog(
            onDismissRequest = {
                customTimerMinutes = ""
                showSleepTimer = false
            },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeTime?.let {
                        Text(
                            "$it remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    listOf(15, 30, 60).forEach { minutes ->
                        TextButton(
                            onClick = {
                                player.setSleepTimerMinutes(minutes)
                                customTimerMinutes = ""
                                showSleepTimer = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$minutes minutes") }
                    }
                    OutlinedTextField(
                        value = customTimerMinutes,
                        onValueChange = { value ->
                            customTimerMinutes = value.filter(Char::isDigit).take(4)
                        },
                        label = { Text("Custom minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            player.setSleepTimerMinutes(0)
                            customTimerMinutes = ""
                            showSleepTimer = false
                        },
                    ) { Text("Turn off") }
                    val customMinutes = customTimerMinutes.toIntOrNull()?.takeIf { it > 0 }
                    TextButton(
                        onClick = {
                            customMinutes?.let(player::setSleepTimerMinutes)
                            customTimerMinutes = ""
                            showSleepTimer = false
                        },
                        enabled = customMinutes != null,
                    ) { Text("Set") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customTimerMinutes = ""
                        showSleepTimer = false
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

private fun formatSleepTimer(remainingMs: Long): String {
    val seconds = (remainingMs / 1_000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
