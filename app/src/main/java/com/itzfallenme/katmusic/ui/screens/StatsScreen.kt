package com.itzfallenme.katmusic.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar

@Composable
fun StatsScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    val stats = remember(library.history) { ServiceLocator.library.stats() }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(
            title = "Your listening",
            onBack = { nav.back() },
            actions = {
                IconButton(onClick = {
                    val text = buildString {
                        appendLine("My KatMusic wrapped")
                        appendLine("${stats.totalMinutes} minutes • ${stats.totalPlays} plays")
                        stats.topArtists.take(5).forEach { (artist, count) ->
                            appendLine("$artist — $count plays")
                        }
                    }
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(share, "Share stats"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share stats")
                }
            }
        )

        if (stats.totalPlays == 0) {
            EmptyState(
                icon = Icons.Filled.TrendingUp,
                title = "Your story starts with a play",
                subtitle = "Listen to a few tracks and your minutes, top artists and listening clock appear here."
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "THIS IS YOUR YEAR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                stats.totalMinutes.toString(),
                                style = MaterialTheme.typography.displayLarge
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "minutes",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Text(
                            "${stats.totalPlays} plays • ${stats.topArtists.size} artists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedButton(onClick = {
                            val calendar = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.DAY_OF_MONTH, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                            }
                            val tracks = com.itzfallenme.katmusic.data.SmartMix.recapTracks(
                                ServiceLocator.library,
                                calendar.timeInMillis
                            )
                            if (tracks.isNotEmpty()) {
                                val month = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date())
                                val playlist = ServiceLocator.library.createPlaylist(
                                    "$month recap",
                                    "Your most played tracks this month"
                                )
                                ServiceLocator.library.addToPlaylist(playlist.id, tracks)
                                nav.openDetail(playlist.id, playlist.name, com.itzfallenme.katmusic.data.CardKind.PLAYLIST)
                            }
                        }) {
                            Text("Save monthly recap playlist")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Listening clock", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val peakColor = MaterialTheme.colorScheme.primary
                        val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        val max = (stats.hourly.maxOrNull() ?: 1).coerceAtLeast(1)
                        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                            val barWidth = size.width / 32f
                            stats.hourly.forEachIndexed { hour, count ->
                                val heightRatio = count.toFloat() / max
                                val barHeight = (size.height * heightRatio).coerceAtLeast(3f)
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(peakColor, dimColor)
                                    ),
                                    topLeft = Offset(
                                        x = hour * (size.width / 24f) + 2f,
                                        y = size.height - barHeight
                                    ),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("12am", style = MaterialTheme.typography.labelSmall)
                            Text("noon", style = MaterialTheme.typography.labelSmall)
                            Text("11pm", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                Text(
                    "Top artists",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            itemsIndexed(stats.topArtists) { index, entry ->
                val maxCount = stats.topArtists.firstOrNull()?.second ?: 1
                StatBar(
                    rank = index + 1,
                    title = entry.first,
                    value = "${entry.second} plays",
                    fraction = entry.second.toFloat() / maxCount
                )
            }

            item {
                Text(
                    "Top tracks",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                )
            }
            itemsIndexed(stats.topTracks) { index, entry ->
                val maxCount = stats.topTracks.firstOrNull()?.second ?: 1
                StatBar(
                    rank = index + 1,
                    title = entry.first,
                    value = "${entry.second} plays",
                    fraction = entry.second.toFloat() / maxCount
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(18.dp)
                ) {
                    Text("Recent listens", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    library.history.take(5).forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                entry.track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                entry.track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun StatBar(rank: Int, title: String, value: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
            )
        }
    }
}
