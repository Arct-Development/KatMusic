package com.itzfallenme.katmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.ServiceLocator
import com.itzfallenme.katmusic.data.AutoEq
import com.itzfallenme.katmusic.ui.Chip
import com.itzfallenme.katmusic.ui.GlassCard
import com.itzfallenme.katmusic.ui.Navigator
import com.itzfallenme.katmusic.ui.SimpleTopBar

@Composable
fun EqualizerScreen(nav: Navigator) {
    val eqState by ServiceLocator.eq.state.collectAsState()
    val settings by ServiceLocator.settings.state.collectAsState()
    val context = LocalContext.current
    var importOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("AutoEq profile") }

    val openProfileFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                importStatus = "Could not read that file"
            } else {
                pasted = text
                profileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: profileName
                importStatus = "Loaded ${text.length} characters - tap Apply"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
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
                    Text(
                        "Headphone correction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Load any AutoEq profile (parametric, GraphicEQ or CSV) and its curve is " +
                            "mapped onto these bands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { importOpen = true }) { Text("Paste profile") }
                        TextButton(onClick = {
                            openProfileFile.launch(arrayOf("text/*", "application/octet-stream"))
                        }) { Text("Open file") }
                    }
                    if (importStatus != null) {
                        Text(
                            importStatus ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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

    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text("AutoEq profile") },
            text = {
                Column {
                    Text(
                        "Paste the contents of a ParametricEQ.txt, GraphicEQ.txt or CSV file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text("Profile text") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importStatus != null) {
                        Text(
                            importStatus ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val profile = AutoEq.parse(pasted, profileName)
                    if (profile.isEmpty) {
                        importStatus = "No AutoEq filters or curve found in that text"
                        return@TextButton
                    }
                    val gains = AutoEq.gainsFor(profile, eqState.bands)
                    if (gains.isEmpty()) {
                        importStatus = "Start playback so the equalizer can bind first"
                        return@TextButton
                    }
                    gains.forEachIndexed { index, gain ->
                        ServiceLocator.eq.setBandLevel(index, gain)
                    }
                    ServiceLocator.settings.update { s ->
                        s.copy(
                            equalizerPreset = profile.name.ifBlank { profileName },
                            equalizerBands = gains,
                            equalizerEnabled = true
                        )
                    }
                    importStatus = "Applied ${profile.name.ifBlank { profileName }}"
                    importOpen = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) { Text("Close") }
            }
        )
    }
}
