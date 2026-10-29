package com.itzfallenme.katmusic.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.StatsPeriod
import com.itzfallenme.katmusic.data.weekdayLabels
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(nav: Navigator) {
    val library by ServiceLocator.library.state.collectAsState()
    var period by remember { mutableStateOf(StatsPeriod.ALL) }
    var topTab by remember { mutableIntStateOf(0) }
    val stats = remember(library.history, period) { ServiceLocator.library.stats(period) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Captured before the button lambda: theme access is a composable call.
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val backgroundArgb = MaterialTheme.colorScheme.background.toArgb()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(
            title = "Your listening",
            onBack = { nav.back() },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        val card = runCatching {
                            com.itzfallenme.katmusic.data.ReceiptCard.render(
                                context = context,
                                stats = stats,
                                periodLabel = period.label,
                                accent = accentArgb,
                                background = backgroundArgb
                            )
                        }.getOrNull()
                        if (card != null) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        com.itzfallenme.katmusic.data.LyricsShareCard.shareIntent(context, card),
                                        "Share receipt"
                                    )
                                )
                            }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Receipt, contentDescription = "Share receipt")
                }
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

        com.itzfallenme.katmusic.ui.theme.ExpressiveSegmentedRow(
            options = StatsPeriod.entries.map { it.label },
            selectedIndex = StatsPeriod.entries.indexOf(period),
            onSelect = { period = StatsPeriod.entries[it] },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        if (stats.totalPlays == 0) {
            EmptyState(
                icon = Icons.Filled.TrendingUp,
                title = if (period == StatsPeriod.ALL) "Your story starts with a play"
                else "Nothing in ${period.label.lowercase()} yet",
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
                            period.label.uppercase(),
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
                        comparisonLabel(period, stats.totalMinutes, stats.previousMinutes)?.let { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        WeekdayStrip(stats.byWeekday)
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
                        Text("Listening clock", style = MaterialTheme.typography.titleMedium) // period-scoped
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Plays by hour, ${period.label.lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                com.itzfallenme.katmusic.ui.theme.ExpressiveSegmentedRow(
                    options = listOf("Songs", "Artists", "Albums"),
                    selectedIndex = topTab,
                    onSelect = { topTab = it },
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
            }
            val ranking = when (topTab) {
                1 -> stats.topArtists
                2 -> stats.topAlbums
                else -> stats.topTracks
            }
            item {
                Text(
                    when (topTab) {
                        1 -> "Top artists"
                        2 -> "Top albums"
                        else -> "Top songs"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            itemsIndexed(ranking) { index, entry ->
                val maxCount = ranking.firstOrNull()?.second ?: 1
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

/** "58 min more than last week" - null when there is nothing to compare against. */
private fun comparisonLabel(period: StatsPeriod, minutes: Long, previous: Long): String? {
    if (period == StatsPeriod.ALL || previous <= 0L) return null
    val reference = when (period) {
        StatsPeriod.TODAY -> "yesterday"
        StatsPeriod.WEEK -> "last week"
        StatsPeriod.MONTH -> "last month"
        StatsPeriod.ALL -> return null
    }
    val diff = minutes - previous
    return when {
        diff > 0 -> "$diff min more than $reference"
        diff < 0 -> "${-diff} min fewer than $reference"
        else -> "Same as $reference"
    }
}

/** Seven bars, Monday first, with the busiest day called out in words. */
@Composable
private fun WeekdayStrip(counts: List<Int>) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val busiest = counts.indices.maxByOrNull { counts[it] }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            counts.forEachIndexed { index, count ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((6 + 28 * (count.toFloat() / max)).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (index == busiest) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        weekdayLabels[index].take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (busiest != null && counts[busiest] > 0) {
            Text(
                "Most active on ${weekdayLabels[busiest]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
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
