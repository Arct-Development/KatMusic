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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar

@Composable
fun EqualizerScreen(nav: Navigator) {
    val eqState by ServiceLocator.eq.state.collectAsState()
    val settings by ServiceLocator.settings.state.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SimpleTopBar(title = "Equalizer", onBack = { nav.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (!eqState.available) {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Equalizer unavailable", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Start playing a track first - the equalizer binds to the active audio session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            GlassCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable effects", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Applied to the current playback session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.equalizerEnabled,
                            onCheckedChange = {
                                ServiceLocator.settings.update { s -> s.copy(equalizerEnabled = it) }
                                ServiceLocator.eq.setEnabled(it)
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Presets", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(eqState.presetNames) { preset ->
                            Chip(
                                text = preset,
                                selected = eqState.preset == preset,
                                onClick = {
                                    val index = eqState.presetNames.indexOf(preset)
                                    ServiceLocator.eq.applyPreset(index)
                                    ServiceLocator.settings.update { s ->
                                        s.copy(equalizerPreset = preset)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Bands",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    eqState.bands.forEachIndexed { index, frequency ->
                        val level = eqState.levels.getOrElse(index) { 0f }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (frequency >= 1000) "${frequency / 1000}k" else "$frequency",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(36.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = level,
                                onValueChange = { ServiceLocator.eq.setBandLevel(index, it) },
                                valueRange = -12f..12f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%+.0f".format(level),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Extra effects", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Bass boost", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = eqState.bassBoost.toFloat(),
                        onValueChange = { ServiceLocator.eq.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f
                    )
                    Text("Virtualizer", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = eqState.virtualizer.toFloat(),
                        onValueChange = { ServiceLocator.eq.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
