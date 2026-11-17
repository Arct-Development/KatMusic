package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Desktop-native playback integrations that have no Android counterpart.
 * The tray and media-key listener are created at startup; this section only
 * explains what the operating system will hand to KatMusic.
 */
@Composable
fun DesktopPlaybackIntegrationSection(globalMediaKeys: Boolean) {
    Text(
        "Desktop playback",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        if (globalMediaKeys) {
            "Keyboard media keys (play/pause, next, previous) are captured system-wide. " +
                "The tray icon mirrors the same controls and shows track-change notifications."
        } else {
            "Keyboard media keys work while KatMusic is focused; this desktop session does not " +
                "allow a system-wide native hook. The tray icon still provides play/pause, next and previous."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}
