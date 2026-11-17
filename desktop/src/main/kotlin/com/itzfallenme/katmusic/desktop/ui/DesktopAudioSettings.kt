package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.DesktopSettingsStore
import com.itzfallenme.katmusic.desktop.data.MpvPlayer
import com.itzfallenme.katmusic.shared.player.PlayerController

/**
 * Desktop equalizer: a real 10-band libmpv audio filter, persisted in the
 * desktop settings store. The Java Sound fallback has no filter graph, so the
 * controls are disabled and explained instead of pretending to work.
 */
@Composable
fun DesktopEqualizerSection(store: DesktopSettingsStore, player: PlayerController) {
    var enabled by remember { mutableStateOf(store.equalizerEnabled()) }
    var gains by remember { mutableStateOf(store.equalizerGains().toList()) }
    val mpv = player as? MpvPlayer

    fun persist() {
        store.setEqualizerEnabled(enabled)
        store.setEqualizerGains(gains.toFloatArray())
        mpv?.setEqualizer(enabled, gains.toFloatArray())
    }

    LaunchedEffect(Unit) {
        mpv?.setEqualizer(enabled, gains.toFloatArray())
    }

    Text(
        text = "Equalizer",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("10-band equalizer", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (mpv == null) {
                    "Install libmpv to process streamed audio. Local Java Sound output ignores this."
                } else {
                    "Applied by libmpv, ±12 dB per band."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                persist()
            },
            enabled = mpv != null,
        )
    }

    if (enabled && mpv != null) {
        MpvPlayer.EQUALIZER_BANDS_HZ.forEachIndexed { index, hz ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatFrequency(hz),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = gains[index],
                    onValueChange = { value ->
                        gains = gains.toMutableList().also { it[index] = value }
                        persist()
                    },
                    valueRange = -12f..12f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = java.lang.String.format(java.util.Locale.US, "%+.1f dB", gains[index]),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(
            onClick = {
                gains = List(MpvPlayer.EQUALIZER_BANDS_HZ.size) { 0f }
                persist()
            },
        ) {
            Text("Reset equalizer")
        }
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1_000) "${hz / 1_000}k" else hz.toString()
