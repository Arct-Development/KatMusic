package com.itzfallenme.katmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.CardKind
import com.itzfallenme.katmusic.data.SmartMix
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.EmptyState
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar
import kotlinx.coroutines.launch

@Composable
fun GeneratorScreen(nav: Navigator) {
    var seeds by remember { mutableStateOf<List<SmartMix.Seed>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var size by remember { mutableIntStateOf(25) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        seeds = SmartMix.seedsFromHistory(ServiceLocator.library)
        selected = seeds.take(4).map { it.name }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = "Smart mix", onBack = { nav.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (seeds.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Listen a little first",
                    subtitle = "The generator learns your artists from listening history. Play a few tracks and come back."
                )
                return@Column
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Pick seed artists", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "KatMusic pulls each artist's popular tracks and alternates between them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    seeds.forEach { seed ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = seed.name in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + seed.name
                                    else selected - seed.name
                                }
                            )
                            Text(seed.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${seed.weight} plays",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Length", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 25, 40, 60).forEach { count ->
                            Chip(
                                text = "$count tracks",
                                selected = size == count,
                                onClick = { size = count }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selected.isNotEmpty() && !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val chosen = seeds.filter { it.name in selected }
                        val tracks = SmartMix.generate(chosen, size)
                        if (tracks.isEmpty()) {
                            message = "Couldn't find tracks for those artists"
                        } else {
                            val name = chosen.take(2).joinToString(" + ") { it.name } + " mix"
                            val playlist = ServiceLocator.library.createPlaylist(
                                name,
                                "Generated from your listening"
                            )
                            ServiceLocator.library.addToPlaylist(playlist.id, tracks)
                            busy = false
                            nav.openDetail(playlist.id, name, CardKind.PLAYLIST)
                            return@launch
                        }
                        busy = false
                    }
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Generate playlist")
                }
            }
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
