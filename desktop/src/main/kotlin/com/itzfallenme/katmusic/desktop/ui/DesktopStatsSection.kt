package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.data.DesktopListeningStats

@Composable
fun DesktopStatsSection(stats: DesktopListeningStats) {
    val state by stats.state.collectAsState()

    Text(
        text = "Listening stats",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (state.totalPlays == 0) {
        Text(
            text = "Play a few tracks to build your local listening history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        return
    }

    Text(
        text = "${state.totalPlays} plays · ${state.uniqueTracks} tracks · ${state.last7Days} in the last 7 days",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    StatRows("Top artists", state.topArtists)
    StatRows("Top tracks", state.topTracks)
    StatRows("Top genres", state.topGenres)
}

@Composable
private fun StatRows(title: String, rows: List<Pair<String, Int>>) {
    if (rows.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    rows.forEach { (label, count) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(text = count.toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
