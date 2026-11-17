package com.itzfallenme.katmusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.data.local.ThemeMode
import com.itzfallenme.katmusic.shared.platform.platformDisplayName
import com.itzfallenme.katmusic.shared.settings.AppSettings
import com.itzfallenme.katmusic.shared.settings.SettingsStore
import com.itzfallenme.katmusic.ui.components.adaptiveContentWidth

@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    /**
     * Platform-only settings section rendered above About. Desktop uses it
     * for the YouTube account QR pairing; Android has its own full settings.
     */
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .adaptiveContentWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Appearance is shared with the Android app — the same tokens, type scale and components.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        SettingsSection("Theme")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { settingsStore.update { it.copy(themeMode = mode) } },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    },
                )
            }
        }
        ToggleRow(
            title = "KatMusic signature palette",
            subtitle = "Blue and cyan accents sampled from the logo.",
            checked = settings.signatureTheme,
            onCheckedChange = { value -> settingsStore.update { it.copy(signatureTheme = value) } },
        )
        ToggleRow(
            title = "Pure black (AMOLED)",
            subtitle = "True-black surfaces for OLED displays.",
            checked = settings.amoled,
            onCheckedChange = { value -> settingsStore.update { it.copy(amoled = value) } },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingsSection("Type")
        ToggleRow(
            title = "Use application font",
            subtitle = "Figtree, bundled with the shared UI.",
            checked = settings.useCustomFont,
            onCheckedChange = { value -> settingsStore.update { it.copy(useCustomFont = value) } },
        )
        ToggleRow(
            title = "Bold headings",
            subtitle = "Heavier end of the Figtree weight axis.",
            checked = settings.useBoldFont,
            enabled = settings.useCustomFont,
            onCheckedChange = { value -> settingsStore.update { it.copy(useBoldFont = value) } },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingsSection("Navigation")
        ToggleRow(
            title = "Liquid glass chrome",
            subtitle = "Translucent navigation and player surfaces.",
            checked = settings.liquidGlass,
            onCheckedChange = { value -> settingsStore.update { it.copy(liquidGlass = value) } },
        )
        ToggleRow(
            title = "Material 3 navigation bar",
            subtitle = "Solid navigation surface instead of the glass treatment.",
            checked = settings.md3NavBar,
            onCheckedChange = { value -> settingsStore.update { it.copy(md3NavBar = value) } },
        )
        ToggleRow(
            title = "Hide navigation tabs",
            subtitle = "Extra vertical room; navigation stays available in the player and back gestures.",
            checked = settings.hideNavTabs,
            onCheckedChange = { value -> settingsStore.update { it.copy(hideNavTabs = value) } },
        )

        extraContent?.let { content ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingsSection("About")
        Text(
            text = "KatMusic 2.6.5 · ${platformDisplayName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
